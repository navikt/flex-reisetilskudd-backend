package no.nav.helse.flex.controller

import no.nav.helse.flex.config.OIDCIssuer.SELVBETJENING
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.ReisetilskuddStatus.*
import no.nav.helse.flex.reisetilskudd.BesvarSporsmalService
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
    private val reisetilskuddService: ReisetilskuddService,
    private val besvarSporsmalService: BesvarSporsmalService,
) {

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<ReisetilskuddSoknad> {
        val fnr = tokenValidationContextHolder.fnrFraOIDC()

        return reisetilskuddService.hentReisetilskuddene(fnr)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PutMapping(
        value = ["/reisetilskudd/{soknadId}/sporsmal/{sporsmalId}"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun oppdaterSporsmal(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @RequestBody sporsmal: Sporsmal
    ): OppdaterSporsmalResponse {
        val soknad = hentOgSjekkTilgangTilSoknad(soknadId)

        if (sporsmalId != sporsmal.id) {
            throw IllegalArgumentException("$sporsmalId != ${sporsmal.id} SporsmalId i body ikke lik sporsmalId i URL ")
        }
        soknad.sjekkGyldigStatus(listOf(SENDBAR, ÅPEN), "lagre sporsmal")

        val oppdatertSoknad = besvarSporsmalService.oppdaterSporsmal(soknad, sporsmal)
        val sporsmalSomBleOppdatert = oppdatertSoknad.sporsmal.find { it.tag == sporsmal.tag }!!

        return OppdaterSporsmalResponse(oppdatertSporsmal = sporsmalSomBleOppdatert)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(
        value = ["/reisetilskudd/{soknadId}/sporsmal/{sporsmalId}/svar"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun lagreNyttSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @RequestBody svar: Svar
    ): Sporsmal {
        val soknad = hentOgSjekkTilgangTilSoknad(soknadId)

        soknad.sjekkGyldigStatus(listOf(SENDBAR, ÅPEN), "lagre svar")

        return besvarSporsmalService.lagreNyttSvar(soknad, sporsmalId, svar)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(
        value = ["/reisetilskudd/{soknadId}/sporsmal/{sporsmalId}/svar/{svarId}"]
    )
    fun slettSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @PathVariable svarId: String,
    ) {
        val soknad = hentOgSjekkTilgangTilSoknad(soknadId)
        besvarSporsmalService.slettSvar(soknad, sporsmalId, svarId)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/reisetilskudd/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun hentSoknad(@PathVariable id: String): ReisetilskuddSoknad {
        return hentOgSjekkTilgangTilSoknad(id)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/send"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(SENDBAR), "send")

        return reisetilskuddService.sendReisetilskudd(soknad)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/avbryt"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun avbrytSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(ÅPEN, FREMTIDIG, SENDBAR), "avbryt")

        return reisetilskuddService.avbrytReisetilskudd(soknad)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @PostMapping(value = ["/reisetilskudd/{id}/gjenapne"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun gjenapneSoknad(@PathVariable("id") id: String): ReisetilskuddSoknad {
        val soknad = hentOgSjekkTilgangTilSoknad(id)
        soknad.sjekkGyldigStatus(listOf(AVBRUTT), "gjenåpne")

        return reisetilskuddService.gjenapneReisetilskudd(soknad)
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
