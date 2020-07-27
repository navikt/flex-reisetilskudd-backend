package no.nav.syfo.reisetilskudd.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.put
import no.nav.syfo.log
import no.nav.syfo.reisetilskudd.ReisetilskuddService
import no.nav.syfo.reisetilskudd.api.utils.Respons
import no.nav.syfo.reisetilskudd.api.utils.toTextContent
import no.nav.syfo.reisetilskudd.domain.DeleteKvittering
import no.nav.syfo.reisetilskudd.domain.KvitteringDTO

fun Route.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {
    get("/reisetilskudd") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        log.info("Authenticated user $fnr")
        call.respond(reisetilskuddService.hentReisetilskudd(fnr))
    }

    put("/kvittering") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val kvitteringJson = call.receive<KvitteringDTO>()
        if (reisetilskuddService.eierReisetilskudd(fnr, kvitteringJson.reisetilskuddId)) {
            reisetilskuddService.lagreKvittering(kvitteringJson)
            call.respond(Respons("${kvitteringJson.kvitteringId} ble lagret i databasen").toTextContent())
        } else {
            call.respond(Respons("Bruker eier ikke søknaden").toTextContent(HttpStatusCode.Forbidden))
        }
    }

    delete("/kvittering") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val kvittering = call.receive<DeleteKvittering>()
        if (reisetilskuddService.eierKvittering(fnr, kvittering.kvitteringId)) {
            reisetilskuddService.slettKvittering(kvittering.kvitteringId)
            call.respond(Respons("${kvittering.kvitteringId} ble slettet fra databasen").toTextContent())
        } else {
            call.respond(Respons("Bruker eier ikke kvitteringen").toTextContent(HttpStatusCode.Forbidden))
        }
    }
}
