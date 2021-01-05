package no.nav.helse.flex

import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.utils.*
import org.amshove.kluent.*
import org.junit.jupiter.api.*
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
    fun `Man kan ikke sende en FREMTIDIG søknad`() {
        with(testApp) {
            val reisetilskudd = Reisetilskudd(
                fnr = fnr,
                fom = LocalDate.now().plusDays(1),
                tom = LocalDate.now().plusDays(3),
                reisetilskuddId = UUID.randomUUID().toString(),
                oppfølgende = false,
                orgNavn = "dsf",
                orgNummer = "sdfsdf",
                status = ReisetilskuddStatus.FREMTIDIG,
                sykmeldingId = UUID.randomUUID().toString()
            ).also {
                testDb.lagreReisetilskudd(it)
            }

            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/${reisetilskudd.reisetilskuddId}/send") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.BadRequest
                response.headers["Content-Type"]!! `should be equal to` "application/json"
                response.content!! `should be equal to` "Operasjonen støttes ikke på søknad med status FREMTIDIG"
            }
        }
    }
}
