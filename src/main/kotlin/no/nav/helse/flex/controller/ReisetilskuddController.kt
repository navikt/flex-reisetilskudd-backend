package no.nav.helse.flex.controller

import no.nav.helse.flex.config.OIDCIssuer.SELVBETJENING
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.ReisetilskuddStatus.*
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
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

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<ReisetilskuddSoknad> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        return reisetilskuddService.hentReisetilskuddene(fnr)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknad(@PathVariable id: String): ReisetilskuddSoknad {
        return hentOgSjekkTilgangTilSoknad(id)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PutMapping(
        value = ["/reisetilskudd/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun svar(@PathVariable("id") id: String, @RequestBody svar: Svar): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(SENDBAR, ÅPEN), "svar")


        return soknad
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(
        value = ["/reisetilskudd/{id}/kvittering"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun lagreKvittering(@PathVariable("id") id: String, @RequestBody svar: Kvittering): Kvittering {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(SENDBAR, ÅPEN), "lagre kvittering")
        return reisetilskuddService.lagreKvittering(svar, soknad.id!!)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(
        value = ["/reisetilskudd/{id}/kvittering/{kvitteringId}"]
    )
    fun slettKvittering(@PathVariable("id") id: String, @PathVariable kvitteringId: String) {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(SENDBAR, ÅPEN), "lagre kvittering")
        reisetilskuddService.slettKvittering(
            kvitteringId = kvitteringId,
            reisetilskuddId = id
        )
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/send"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(SENDBAR), "send")

        reisetilskuddService.sendReisetilskudd(soknad.fnr, soknad.id!!)
        return reisetilskuddService.hentReisetilskudd(soknad.id) ?: throw IllegalStateException()
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/avbryt"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun avbrytSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(ÅPEN, FREMTIDIG, SENDBAR), "avbryt")

        reisetilskuddService.avbrytReisetilskudd(soknad.fnr, soknad.id!!)
        return reisetilskuddService.hentReisetilskudd(soknad.id) ?: throw IllegalStateException()
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/gjenapne"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun gjenapneSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(AVBRUTT), "gjenåpne")

        reisetilskuddService.gjenapneReisetilskudd(soknad.fnr, soknad.id!!)
        return reisetilskuddService.hentReisetilskudd(soknad.id) ?: throw IllegalStateException()
    }

    private fun hentOgSjekkTilgangTilSoknad(soknadId: String): ReisetilskuddSoknad {
        val hentReisetilskudd = reisetilskuddService.hentReisetilskudd(soknadId) ?: throw SoknadIkkeFunnetException()

        if (this.tokenValidationContextHolder.fnrFraOIDC() != hentReisetilskudd.fnr) {
            throw IkkeTilgangException("Er ikke eier")
        }
        return hentReisetilskudd
    }

    private fun ReisetilskuddSoknad.sjekkGyldigStatus(statuser: List<ReisetilskuddStatus>, operasjon: String) {
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
