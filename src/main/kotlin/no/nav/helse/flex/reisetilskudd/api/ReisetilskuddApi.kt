package no.nav.helse.flex.reisetilskudd.api

import io.ktor.application.*
import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.api.utils.Respons
import no.nav.helse.flex.reisetilskudd.api.utils.toTextContent
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
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
            this.call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.NotFound))
            return
        }
        if (reisetilskudd.fnr != fnr) {
            call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
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
            call.respond(Respons("Operasjonen støttes ikke på søknad med status $status").toTextContent(HttpStatusCode.BadRequest))
        }
    }

    @ContextDsl
    suspend fun Reisetilskudd.sjekkStatus(
        status: ReisetilskuddStatus,
        call: ApplicationCall,
        body: suspend Reisetilskudd.() -> Unit
    ) {
        return sjekkStatus(listOf(status), call, body)
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
                sjekkStatus(ÅPEN, call) {
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
                sjekkStatus(ÅPEN, call) {
                    reisetilskuddService.sendReisetilskudd(fnr, reisetilskuddId)
                    call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt sendt").toTextContent())
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/avbryt") {
            reisetilskudd {
                sjekkStatus(listOf(ÅPEN, FREMTIDIG), call) {
                    reisetilskuddService.avbrytReisetilskudd(fnr, reisetilskuddId)
                    call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt avbrutt").toTextContent())
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/gjenapne") {
            reisetilskudd {
                sjekkStatus(AVBRUTT, call) {
                    reisetilskuddService.gjenapneReisetilskudd(fnr, reisetilskuddId)
                    call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt gjenåpnet").toTextContent())
                }
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/kvittering") {
            reisetilskudd {
                sjekkStatus(ÅPEN, call) {
                    val kvittering = call.receive<Kvittering>()
                    reisetilskuddService.lagreKvittering(kvittering, reisetilskuddId)
                    call.respond(Respons("Kvittering ${kvittering.kvitteringId} ble lagret").toTextContent())
                }
            }
        }

        delete("/reisetilskudd/{reisetilskuddId}/kvittering/{kvitteringId}") {
            reisetilskudd {
                reisetilskudd {
                    sjekkStatus(ÅPEN, call) {
                        val kvitteringId = call.parameters["kvitteringId"]!!
                        reisetilskuddService.slettKvittering(kvitteringId = kvitteringId, reisetilskuddId = reisetilskuddId)
                        call.respond(Respons("kvitteringId $kvitteringId ble slettet").toTextContent())
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
