package no.nav.helse.flex

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.api.registerNaisApi
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test

internal class SelftestTest {

    @Test
    fun `successfull liveness and readyness`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm alive! :)"
            }

            with(handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm ready! :)"
            }
        }
    }

    @Test
    fun `unsuccessful liveness and readyness`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = false
            applicationState.alive = false
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "I'm dead x_x"
            }

            with(handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "Please wait! I'm not ready :("
            }
        }
    }
}
