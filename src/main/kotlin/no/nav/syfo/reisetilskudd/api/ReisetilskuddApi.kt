package no.nav.syfo.reisetilskudd.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.log
import no.nav.syfo.reisetilskudd.ReisetilskuddService

fun Route.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {
    get("/reisetilskudd") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        log.info("Authenticated user $fnr")
        call.respond(reisetilskuddService.hentReisetilskudd(fnr))
    }
}
