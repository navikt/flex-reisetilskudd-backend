package no.nav.helse.flex.reisetilskudd.api

import io.ktor.application.*
import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.*
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus.*
import no.nav.helse.flex.reisetilskudd.domain.Svar

fun Route.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {

    @ContextDsl
    suspend fun PipelineContext<Unit, ApplicationCall>.reisetilskudd(body: suspend Reisetilskudd.() -> Unit) {
        val fnr = this.call.fnr()
        val reisetilskuddId = this.call.parameters["reisetilskuddId"]!!
        val reisetilskudd = reisetilskuddService.hentReisetilskudd(reisetilskuddId)
        if (reisetilskudd == null) {
            call.reply("Søknad ikke funnet", NotFound)
            return
        }
        if (reisetilskudd.fnr != fnr) {
            call.reply("Bruker eier ikke søknaden", Forbidden)
            return
        }
        body(reisetilskudd)
    }

    @ContextDsl
    suspend fun Reisetilskudd.sjekkStatus(
        statusList: List<ReisetilskuddStatus>,
        call: ApplicationCall,
        body: suspend Reisetilskudd.() -> Unit
    ) {
        if (statusList.contains(this.status)) {
            body(this)
        } else {
            call.reply("Operasjonen støttes ikke på søknad med status $status", BadRequest)
        }
    }

    route("/api/v1") {
        get("/reisetilskudd") {
            val fnr = call.fnr()

            call.respond(reisetilskuddService.hentReisetilskuddene(fnr))
        }

        get("/reisetilskudd/{reisetilskuddId}") {
            reisetilskudd {
                call.respond(this)
            }
        }

        put("/reisetilskudd/{reisetilskuddId}") {

            reisetilskudd {
                sjekkStatus(listOf(ÅPEN, SENDBAR), call) {
                    val svar = call.receive<Svar>()
                    var reisetilskudd = this
                    if (svar.går != null) {
                        reisetilskudd = reisetilskudd.copy(går = svar.går)
                    }
                    if (svar.sykler != null) {
                        reisetilskudd = reisetilskudd.copy(sykler = svar.sykler)
                    }
                    if (svar.utbetalingTilArbeidsgiver != null) {
                        reisetilskudd =
                            reisetilskudd.copy(utbetalingTilArbeidsgiver = svar.utbetalingTilArbeidsgiver)
                    }
                    if (svar.egenBil != null) {
                        reisetilskudd = reisetilskudd.copy(egenBil = svar.egenBil)
                    }
                    if (svar.kollektivtransport != null) {
                        reisetilskudd = reisetilskudd.copy(kollektivtransport = svar.kollektivtransport)
                    }
                    val oppdatert = reisetilskuddService.oppdaterReisetilskudd(reisetilskudd)

                    call.respond(oppdatert)
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/send") {
            reisetilskudd {
                sjekkStatus(listOf(SENDBAR), call) {
                    reisetilskuddService.sendReisetilskudd(fnr, reisetilskuddId)
                    call.reply("Reisetilskudd $reisetilskuddId har blitt sendt")
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/avbryt") {
            reisetilskudd {
                sjekkStatus(listOf(ÅPEN, FREMTIDIG, SENDBAR), call) {
                    reisetilskuddService.avbrytReisetilskudd(fnr, reisetilskuddId)
                    call.reply("Reisetilskudd $reisetilskuddId har blitt avbrutt")
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/gjenapne") {
            reisetilskudd {
                sjekkStatus(listOf(AVBRUTT), call) {
                    reisetilskuddService.gjenapneReisetilskudd(fnr, reisetilskuddId)
                    call.reply("Reisetilskudd $reisetilskuddId har blitt gjenåpnet")
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/kvittering") {
            reisetilskudd {
                sjekkStatus(listOf(ÅPEN, SENDBAR), call) {
                    val kvittering = reisetilskuddService.lagreKvittering(call.receive(), reisetilskuddId)
                    call.response.status(Created)
                    call.respond(kvittering)
                }
            }
        }

        delete("/reisetilskudd/{reisetilskuddId}/kvittering/{kvitteringId}") {
            reisetilskudd {
                reisetilskudd {
                    sjekkStatus(listOf(ÅPEN, SENDBAR), call) {
                        val kvitteringId = call.parameters["kvitteringId"]!!
                        reisetilskuddService.slettKvittering(
                            kvitteringId = kvitteringId,
                            reisetilskuddId = reisetilskuddId
                        )
                        call.reply("kvitteringId $kvitteringId ble slettet")
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.fnr(): String {
    val principal: JWTPrincipal = this.authentication.principal()!!
    return principal.payload.subject
}

private data class Message(val message: String)

private suspend fun ApplicationCall.reply(message: String, status: HttpStatusCode = OK) {
    this.response.status(status)
    this.respond(Message(message))
}
