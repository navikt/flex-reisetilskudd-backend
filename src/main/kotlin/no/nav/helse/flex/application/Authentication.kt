package no.nav.helse.flex.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.flex.log

fun Application.setupAuth(
    loginserviceClientId: String,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials: JWTCredential ->
                if (!hasLoginserviceClientIdAudience(credentials, loginserviceClientId)) {
                    log.warn(
                        "Auth: Unexpected audience for jwt {}, {}",
                        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
                        StructuredArguments.keyValue("audience", credentials.payload.audience)
                    )
                    return@validate null
                }
                if (!erNiva4(credentials)) {
                    return@validate null
                }
                return@validate JWTPrincipal(credentials.payload)
            }
        }
    }
}

fun hasLoginserviceClientIdAudience(credentials: JWTCredential, loginserviceClientId: String): Boolean {
    return credentials.payload.audience.contains(loginserviceClientId)
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
