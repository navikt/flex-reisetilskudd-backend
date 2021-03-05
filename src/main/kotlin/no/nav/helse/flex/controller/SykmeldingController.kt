package no.nav.helse.flex.controller

import no.nav.helse.flex.cache.SykmeldingBehandletCache
import no.nav.helse.flex.config.OIDCIssuer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v1/sykmeldinger"])
class SykmeldingController(
    private val sykmeldingBehandletCache: SykmeldingBehandletCache,
) {

    @ProtectedWithClaims(issuer = OIDCIssuer.SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/{sykmeldingId}/behandlet"], produces = [APPLICATION_JSON_VALUE])
    fun sykmeldingBehandlet(@PathVariable("sykmeldingId") sykmeldingId: String): Boolean {
        return sykmeldingBehandletCache.erSykmeldingBehandlet(sykmeldingId)
    }
}
