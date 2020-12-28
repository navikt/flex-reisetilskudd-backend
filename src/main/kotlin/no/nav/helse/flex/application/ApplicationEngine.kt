package no.nav.helse.flex.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.api.registerNaisApi
import no.nav.helse.flex.application.metrics.monitorHttpRequests
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.api.setupReisetilskuddApi
import java.util.UUID

@KtorExperimentalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    reisetilskuddService: ReisetilskuddService,
    jwkProvider: JwkProvider,
    issuer: String
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        configureApplication(
            env = env,
            applicationState = applicationState,
            reisetilskuddService = reisetilskuddService,
            jwkProvider = jwkProvider,
            issuer = issuer
        )
    }

@KtorExperimentalAPI
fun Application.configureApplication(
    env: Environment,
    applicationState: ApplicationState,
    reisetilskuddService: ReisetilskuddService,
    jwkProvider: JwkProvider,
    issuer: String
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    setupAuth(
        loginserviceClientId = env.loginserviceIdportenAudience,
        jwkProvider = jwkProvider,
        issuer = issuer
    )

    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(HttpHeaders.XCorrelationId)
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error("Caught exception", cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
    }

    routing {
        registerNaisApi(applicationState)
        authenticate("jwt") {
            setupReisetilskuddApi(reisetilskuddService)
        }
    }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
