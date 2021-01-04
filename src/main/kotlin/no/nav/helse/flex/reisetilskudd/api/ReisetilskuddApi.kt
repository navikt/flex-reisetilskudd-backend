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
                val svarJson = call.receive<Svar>()
                var reisetilskudd = this
                if (svarJson.går != null) {
                    reisetilskudd = reisetilskudd.copy(går = svarJson.går)
                }
                if (svarJson.sykler != null) {
                    reisetilskudd = reisetilskudd.copy(sykler = svarJson.sykler)
                }
                if (svarJson.utbetalingTilArbeidsgiver != null) {
                    reisetilskudd =
                        reisetilskudd.copy(utbetalingTilArbeidsgiver = svarJson.utbetalingTilArbeidsgiver)
                }
                if (svarJson.egenBil != null) {
                    reisetilskudd = reisetilskudd.copy(egenBil = svarJson.egenBil)
                }
                if (svarJson.kollektivtransport != null) {
                    reisetilskudd = reisetilskudd.copy(kollektivtransport = svarJson.kollektivtransport)
                }
                val oppdatert = reisetilskuddService.oppdaterReisetilskudd(reisetilskudd)

                call.respond(oppdatert)
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/send") {
            reisetilskudd {
                if (status != ReisetilskuddStatus.ÅPEN) {
                    call.respond(Respons("Kan ikke sende en søknad med status $status").toTextContent(HttpStatusCode.Forbidden))
                    return@reisetilskudd
                }
                reisetilskuddService.sendReisetilskudd(fnr, reisetilskuddId)
                call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt sendt").toTextContent())
            }
        }

        post("/kvittering") {
            val fnr = call.fnr()

            val kvitteringJson = call.receive<Kvittering>()
            if (reisetilskuddService.eierReisetilskudd(fnr, kvitteringJson.reisetilskuddId)) {
                reisetilskuddService.lagreKvittering(kvitteringJson)
                call.respond(Respons("${kvitteringJson.kvitteringId} ble lagret i databasen").toTextContent())
            } else {
                call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/avbryt") {
            reisetilskudd {
                if (!(status == ReisetilskuddStatus.ÅPEN || status == ReisetilskuddStatus.FREMTIDIG)) {
                    call.respond(Respons("Kan ikke avbryte en søknad med status $status").toTextContent(HttpStatusCode.Forbidden))
                    return@reisetilskudd
                }
                reisetilskuddService.avbrytReisetilskudd(fnr, reisetilskuddId)
                call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt avbrutt").toTextContent())
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/gjenapne") {
            reisetilskudd {
                if (status != ReisetilskuddStatus.AVBRUTT) {
                    call.respond(Respons("Kan ikke gjenåpne en søknad med status $status").toTextContent(HttpStatusCode.Forbidden))
                    return@reisetilskudd
                }
                reisetilskuddService.gjenapneReisetilskudd(fnr, reisetilskuddId)
                call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt gjenåpnet").toTextContent())
            }
        }

        delete("/kvittering/{kvitteringId}") {
            val fnr = call.fnr()
            val kvitteringId = call.parameters["kvitteringId"]!!

            if (reisetilskuddService.eierKvittering(fnr, kvitteringId)) {
                reisetilskuddService.slettKvittering(kvitteringId)
                call.respond(Respons("kvitteringId $kvitteringId ble slettet fra databasen").toTextContent())
            } else {
                call.respond(Respons("Bruker eier ikke kvitteringen").toTextContent(HttpStatusCode.Forbidden))
            }
        }
    }
}

private fun ApplicationCall.fnr(): String {
    val principal: JWTPrincipal = this.authentication.principal()!!
    return principal.payload.subject
}
