package no.nav.syfo.reisetilskudd.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.put
import no.nav.syfo.domain.KvitteringJson
import no.nav.syfo.log
import no.nav.syfo.reisetilskudd.ReisetilskuddService

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
        val kvitteringJson = call.receive<KvitteringJson>()
        if (reisetilskuddService.eierReisetilskudd(fnr, kvitteringJson.reisetilskuddId)) {
            reisetilskuddService.lagreKvittering(kvitteringJson)
            call.respond(TextContent("{\"message\": \"${kvitteringJson.reisetilskuddId} ble lagret i databasen\"}", ContentType.Application.Json))
        } else {
            call.respond(TextContent("{\"message\": \"Bruker eier ikke søknaden\"}", ContentType.Application.Json, HttpStatusCode.Unauthorized))
        }
    }
}
