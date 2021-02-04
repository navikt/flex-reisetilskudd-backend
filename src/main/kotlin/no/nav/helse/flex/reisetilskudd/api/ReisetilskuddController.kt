package no.nav.helse.flex.reisetilskudd.api

import no.nav.helse.flex.application.OIDCIssuer.SELVBETJENING
import no.nav.helse.flex.logger
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus.*
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v1"])
class SoknadController(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val reisetilskuddService: ReisetilskuddService
) {

    private val log = logger()

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<Reisetilskudd> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        return reisetilskuddService.hentReisetilskuddene(fnr)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/avbryt"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun avbrytSoknad(@PathVariable("id") id: String): Reisetilskudd {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(ÅPEN, FREMTIDIG, SENDBAR), "avbryt")

        reisetilskuddService.avbrytReisetilskudd(soknad.fnr, soknad.reisetilskuddId)
        return reisetilskuddService.hentReisetilskudd(soknad.reisetilskuddId) ?: throw IllegalStateException()
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/gjenapne"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun gjenapneSoknad(@PathVariable("id") id: String): Reisetilskudd {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(AVBRUTT), "avbryt")

        reisetilskuddService.gjenapneReisetilskudd(soknad.fnr, soknad.reisetilskuddId)
        return reisetilskuddService.hentReisetilskudd(soknad.reisetilskuddId) ?: throw IllegalStateException()
    }

    private fun hentOgSjekkTilgangTilSoknad(soknadId: String): Reisetilskudd {
        val hentReisetilskudd = reisetilskuddService.hentReisetilskudd(soknadId) ?: throw SoknadIkkeFunnetException()

        if (this.tokenValidationContextHolder.fnrFraOIDC() != hentReisetilskudd.fnr) {
            throw IkkeTilgangException("Er ikke eier")
        }
        return hentReisetilskudd
    }

    private fun Reisetilskudd.sjekkGyldigStatus(statuser: List<ReisetilskuddStatus>, operasjon: String) {
        if (!statuser.contains(this.status)) {
            throw UgyldigStatusException("${this.status} ikke støttet for operasjon $operasjon")
        }
    }
}

fun TokenValidationContextHolder.fnrFraOIDC(): String {
    val context = this.tokenValidationContext
    return context.getClaims(SELVBETJENING).subject
}

class SoknadIkkeFunnetException : AbstractApiError(
    message = "Søknad ikke funnet",
    httpStatus = HttpStatus.NOT_FOUND,
    loglevel = LogLevel.WARN,
    reason = "Søknad ikke funnet"
)

class IkkeTilgangException(s: String) : AbstractApiError(
    message = s,
    httpStatus = HttpStatus.FORBIDDEN,
    loglevel = LogLevel.WARN,
    reason = s
)

class UgyldigStatusException(s: String) : AbstractApiError(
    message = s,
    httpStatus = HttpStatus.BAD_REQUEST,
    loglevel = LogLevel.WARN,
    reason = s
)
