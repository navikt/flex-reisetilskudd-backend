package no.nav.helse.flex

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.util.*
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class SelftestTest {

    companion object {
        lateinit var testApp: TestApp

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            testApp = skapTestApplication()
        }
    }

    @Test
    fun `successfull liveness and readyness`() {
        with(testApp) {
            applicationState.ready = true
            applicationState.alive = true

            with(engine.handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm alive! :)"
            }

            with(engine.handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm ready! :)"
            }
        }
    }

    @Test
    fun `unsuccessful liveness and readyness`() {
        with(testApp) {
            applicationState.ready = false
            applicationState.alive = false

            with(engine.handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "I'm dead x_x"
            }

            with(engine.handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "Please wait! I'm not ready :("
            }
        }
    }
}
