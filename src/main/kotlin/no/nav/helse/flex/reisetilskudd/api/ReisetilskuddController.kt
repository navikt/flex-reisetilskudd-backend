package no.nav.helse.flex.reisetilskudd.api

import no.nav.helse.flex.application.OIDCIssuer.SELVBETJENING
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v1"])
class SoknadController(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val reisetilskuddService: ReisetilskuddService
) {

    private val log = log()

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<Reisetilskudd> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        return reisetilskuddService.hentReisetilskuddene(fnr)
    }
}

fun TokenValidationContextHolder.fnrFraOIDC(): String {
    val context = this.tokenValidationContext
    return context.getClaims(SELVBETJENING).subject
}
