package no.nav.syfo.reisetilskudd.api

import io.ktor.application.* // ktlint-disable no-wildcard-imports
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.* // ktlint-disable no-wildcard-imports
import no.nav.syfo.reisetilskudd.ReisetilskuddService
import no.nav.syfo.reisetilskudd.api.utils.Respons
import no.nav.syfo.reisetilskudd.api.utils.toTextContent
import no.nav.syfo.reisetilskudd.domain.KvitteringDTO
import no.nav.syfo.reisetilskudd.domain.SvarDTO

fun Route.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {
    route("/api/v1") {
        get("/reisetilskudd") {
            val fnr = call.fnr()

            call.respond(reisetilskuddService.hentReisetilskudd(fnr))
        }

        put("/reisetilskudd/{reisetilskuddId}") {
            val fnr = call.fnr()
            val reisetilskuddId = call.parameters["reisetilskuddId"]!!

            val svarJson = call.receive<SvarDTO>()
            if (reisetilskuddId != svarJson.reisetilskuddId) {
                call.respond(Respons("ID i url matcher ikke ID i body").toTextContent(HttpStatusCode.BadRequest))
                return@put
            }
            if (reisetilskuddService.eierReisetilskudd(fnr, svarJson.reisetilskuddId)) {
                val reisetilskudd = reisetilskuddService.hentReisetilskudd(fnr, svarJson.reisetilskuddId)
                if (reisetilskudd == null) {
                    call.respond(Respons("${svarJson.reisetilskuddId} finnes ikke").toTextContent(HttpStatusCode.NotFound))
                    return@put
                }
                if (svarJson.går != null) {
                    reisetilskudd.går = svarJson.går
                }
                if (svarJson.sykler != null) {
                    reisetilskudd.sykler = svarJson.sykler
                }
                if (svarJson.utbetalingTilArbeidsgiver != null) {
                    reisetilskudd.utbetalingTilArbeidsgiver = svarJson.utbetalingTilArbeidsgiver
                }
                if (svarJson.egenBil != null) {
                    reisetilskudd.egenBil = svarJson.egenBil
                }
                if (svarJson.kollektivtransport != null) {
                    reisetilskudd.kollektivtransport = svarJson.kollektivtransport
                }
                reisetilskuddService.oppdaterReisetilskudd(reisetilskudd)
                val reisetilskuddFraDb = reisetilskuddService.hentReisetilskudd(fnr, svarJson.reisetilskuddId)
                if (reisetilskuddFraDb != null) {
                    call.respond(reisetilskuddFraDb)
                } else {
                    call.respond(Respons("Noe gikk galt i henting fra databasen!").toTextContent(HttpStatusCode.InternalServerError))
                }
            } else {
                call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
            }
        }

        post("/kvittering") {
            val fnr = call.fnr()

            val kvitteringJson = call.receive<KvitteringDTO>()
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
