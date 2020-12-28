package no.nav.helse.flex.reisetilskudd.api

import io.ktor.application.*
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.api.utils.Respons
import no.nav.helse.flex.reisetilskudd.api.utils.toTextContent
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Svar

fun Route.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {
    route("/api/v1") {
        get("/reisetilskudd") {
            val fnr = call.fnr()

            call.respond(reisetilskuddService.hentReisetilskudd(fnr))
        }

        put("/reisetilskudd/{reisetilskuddId}") {
            val fnr = call.fnr()
            val reisetilskuddId = call.parameters["reisetilskuddId"]!!

            val svarJson = call.receive<Svar>()

            if (reisetilskuddService.eierReisetilskudd(fnr, reisetilskuddId)) {
                var reisetilskudd = reisetilskuddService.hentReisetilskudd(fnr, reisetilskuddId)
                if (reisetilskudd == null) {
                    call.respond(Respons("$reisetilskuddId finnes ikke").toTextContent(HttpStatusCode.NotFound))
                    return@put
                }
                if (svarJson.går != null) {
                    reisetilskudd = reisetilskudd.copy(går = svarJson.går)
                }
                if (svarJson.sykler != null) {
                    reisetilskudd = reisetilskudd.copy(sykler = svarJson.sykler)
                }
                if (svarJson.utbetalingTilArbeidsgiver != null) {
                    reisetilskudd = reisetilskudd.copy(utbetalingTilArbeidsgiver = svarJson.utbetalingTilArbeidsgiver)
                }
                if (svarJson.egenBil != null) {
                    reisetilskudd = reisetilskudd.copy(egenBil = svarJson.egenBil)
                }
                if (svarJson.kollektivtransport != null) {
                    reisetilskudd = reisetilskudd.copy(kollektivtransport = svarJson.kollektivtransport)
                }
                reisetilskuddService.oppdaterReisetilskudd(reisetilskudd)
                val reisetilskuddFraDb = reisetilskuddService.hentReisetilskudd(fnr, reisetilskuddId)
                if (reisetilskuddFraDb != null) {
                    call.respond(reisetilskuddFraDb)
                } else {
                    call.respond(Respons("Noe gikk galt i henting fra databasen!").toTextContent(HttpStatusCode.InternalServerError))
                }
            } else {
                call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
            }
        }

        post("/reisetilskudd/{reisetilskuddId}/send") {
            val fnr = call.fnr()
            val reisetilskuddId = call.parameters["reisetilskuddId"]!!
            if (reisetilskuddService.eierReisetilskudd(fnr, reisetilskuddId)) {
                reisetilskuddService.sendReisetilskudd(fnr, reisetilskuddId)
                call.respond(Respons("Reisetilskudd $reisetilskuddId har blitt sendt").toTextContent())
            } else {
                call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
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
