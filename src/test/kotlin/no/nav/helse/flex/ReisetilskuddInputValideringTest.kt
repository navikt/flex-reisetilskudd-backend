package no.nav.helse.flex

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus.FREMTIDIG
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus.ÅPEN
import no.nav.helse.flex.utils.*
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.LocalDate
import java.util.*

@KtorExperimentalAPI
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ReisetilskuddInputValideringTest {

    companion object {
        lateinit var testApp: TestApp
        val fnr = "12345678901"

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            testApp = skapTestApplication()
        }
    }

    @AfterEach
    internal fun afterEach() {
        testApp.testDb.connection.dropData()
    }

    @Test
    fun `Man kan ikke sende en FREMTIDIG eller ÅPEN søknad`() {
        with(testApp) {
            val reisetilskudd = reisetilskudd(FREMTIDIG).also {
                testDb.lagreReisetilskudd(it)
            }

            val reisetilskudd2 = reisetilskudd(ÅPEN)
                .also {
                    testDb.lagreReisetilskudd(it)
                }

            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/${reisetilskudd.reisetilskuddId}/send") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.BadRequest
                response.headers["Content-Type"]!! `should be equal to` "application/json; charset=UTF-8"
                response.content!! `should be equal to` "{\"message\":\"Operasjonen støttes ikke på søknad med status FREMTIDIG\"}"
            }

            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/${reisetilskudd2.reisetilskuddId}/send") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.BadRequest
                response.headers["Content-Type"]!! `should be equal to` "application/json; charset=UTF-8"
                response.content!! `should be equal to` "{\"message\":\"Operasjonen støttes ikke på søknad med status ÅPEN\"}"
            }
        }
    }

    @Test
    fun `Ukjent id gir 404`() {
        with(testApp) {

            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd/${UUID.randomUUID()}") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.NotFound
                response.headers["Content-Type"]!! `should be equal to` "application/json; charset=UTF-8"
                response.content!! `should be equal to` "{\"message\":\"Søknad ikke funnet\"}"
            }
        }
    }

    @Test
    fun `En annen persons reisetilskudd id gir 403`() {
        with(testApp) {
            val reisetilskudd = reisetilskudd(FREMTIDIG).also {
                testDb.lagreReisetilskudd(it)
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd/${reisetilskudd.reisetilskuddId}") {
                    medSelvbetjeningToken("01010132548")
                }
            ) {
                response.status() shouldEqual Forbidden
                response.headers["Content-Type"]!! `should be equal to` "application/json; charset=UTF-8"
                response.content!! `should be equal to` "{\"message\":\"Bruker eier ikke søknaden\"}"
            }
        }
    }

    fun reisetilskudd(status: ReisetilskuddStatus): Reisetilskudd {
        return Reisetilskudd(
            fnr = fnr,
            fom = LocalDate.now().plusDays(1),
            tom = LocalDate.now().plusDays(3),
            reisetilskuddId = UUID.randomUUID().toString(),
            oppfølgende = false,
            orgNavn = "dsf",
            orgNummer = "sdfsdf",
            status = status,
            sykmeldingId = UUID.randomUUID().toString(),
            opprettet = Instant.now(),
        )
    }
}
