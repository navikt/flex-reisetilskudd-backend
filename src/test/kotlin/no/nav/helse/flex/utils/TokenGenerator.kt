package no.nav.helse.flex.utils

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import java.util.*

fun MockOAuth2Server.token(
    fnr: String,
    issuerId: String = "selvbetjening",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4"),

): String {
    return this.issueToken(
        issuerId,
        clientId,
        DefaultOAuth2TokenCallback(
            issuerId = issuerId,
            subject = fnr,
            audience = listOf(audience),
            claims = claims,
            expiry = 3600
        )
    ).serialize()
}
