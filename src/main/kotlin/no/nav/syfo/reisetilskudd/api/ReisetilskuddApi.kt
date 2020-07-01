package no.nav.syfo.reisetilskudd.api


import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import no.nav.syfo.reisetilskudd.ReisetilskuddService

fun Routing.setupReisetilskuddApi(reisetilskuddService: ReisetilskuddService) {
    get("/reisetilskudd") {
        call.respond(reisetilskuddService.hentReisetilskudd("01010112345"))
    }
}