package no.nav.helse.flex.client.syketilfelle

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class SyketilfelleClient(
    private val flexFssProxyRestTemplate: RestTemplate,
    @Value("\${flex.fss.proxy.url}") private val flexFssProxyUrl: String
) {
    private val log = logger()

    @Retryable
    fun erUtenforVentetid(
        aktorId: String,
        sykmeldingId: String,
        erUtenforVentetidRequest: ErUtenforVentetidRequest
    ): Boolean {

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val queryBuilder = UriComponentsBuilder
            .fromHttpUrl(flexFssProxyUrl)
            .pathSegment("reisetilskudd", aktorId, sykmeldingId, "erUtenforVentetid")

        val result = flexFssProxyRestTemplate
            .exchange(
                queryBuilder.toUriString(),
                HttpMethod.POST,
                HttpEntity(erUtenforVentetidRequest, headers),
                Boolean::class.java
            )

        if (!result.statusCode.is2xxSuccessful) {
            val message = "Kall mot syfosyketilfelle feiler med HTTP-${result.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        return result.body ?: throw RuntimeException("Ingen data returnert fra syfosyketilfelle i erUtenforVentetid")
    }
}
