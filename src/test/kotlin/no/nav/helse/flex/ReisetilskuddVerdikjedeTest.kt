package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.application.objectMapper
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.Svar
import no.nav.helse.flex.utils.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.util.*

@KtorExperimentalAPI
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ReisetilskuddVerdikjedeTest {

    companion object {
        lateinit var testApp: TestApp
        val fnr = "12345678901"
        val tom = LocalDate.now()
        val fom = LocalDate.now().minusDays(4)
        val sykmeldingId = UUID.randomUUID().toString()

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            testApp = skapTestApplication()
        }
    }

    @Test
    @Order(0)
    fun `Det er ingen reisetilskudd til å begynne med`() {
        with(testApp) {
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content!!.tilReisetilskuddListe().size `should be equal to` 0
            }
        }
    }

    @Test
    @Order(1)
    fun `En sykmelding med reisetilskudd konsumereres`() {
        with(testApp) {
            applicationState.alive = true
            applicationState.ready = true
            val sykmeldingStatusKafkaMessageDTO =
                skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, sykmeldingId = sykmeldingId)
            val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId, fom = fom, tom = tom)
            sykmeldingKafkaProducer.send(
                ProducerRecord(
                    "syfo-sendt-sykmelding",
                    SykmeldingMessage(
                        sykmelding = sykmelding,
                        event = sykmeldingStatusKafkaMessageDTO.event,
                        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
                    )
                )
            ).get()

            runBlocking {
                stopApplicationNårAntallKafkaMeldingerErLest(
                    sykmeldingKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )
                sykmeldingKafkaService.start()
            }
        }
    }

    @Test
    @Order(2)
    fun `Et reisetilskudd er tilgjengelig`() {
        with(testApp) {
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1
                reisetilskudd[0].fnr shouldEqual fnr
                reisetilskudd[0].fom shouldEqual fom
                reisetilskudd[0].tom shouldEqual tom
                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.ÅPEN
                reisetilskudd[0].sykmeldingId shouldEqual sykmeldingId
                reisetilskudd[0].egenBil shouldEqual 0.0
                reisetilskudd[0].sykler.shouldBeNull()
                reisetilskudd[0].kollektivtransport shouldEqual 0.0
                reisetilskudd[0].oppfølgende.`should be false`()
                reisetilskudd[0].sendt.shouldBeNull()
                reisetilskudd[0].avbrutt.shouldBeNull()
                reisetilskudd[0].går.shouldBeNull()
                reisetilskudd[0].kvitteringer.shouldBeEmpty()
                reisetilskudd[0].orgNummer.shouldBeNull()
                reisetilskudd[0].orgNavn.shouldBeNull()
                reisetilskudd[0].utbetalingTilArbeidsgiver.shouldBeNull()
            }
        }
    }

    @Test
    @Order(2)
    fun `Nivå 3 token returnerer 401`() {
        with(testApp) {
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr, level = "Level3")
                }
            ) {
                response.status() shouldEqual HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/$id/avbryt") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.AVBRUTT
                reisetilskudd[0].avbrutt.shouldNotBeNull()
            }
        }
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/$id/gjenapne") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.ÅPEN
                reisetilskudd[0].avbrutt.shouldBeNull()
            }
        }
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Put, "/api/v1/reisetilskudd/$id") {
                    medSelvbetjeningToken(fnr)
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(Svar(sykler = true).serialisertTilString())
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content.tilReisetilskudd()
                reisetilskudd.sykler?.shouldBeTrue()
            }
        }
    }

    @Test
    @Order(6)
    fun `Vi kan sende inn søknaden`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/$id/send") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.SENDT
                reisetilskudd[0].sendt.shouldNotBeNull()
            }
        }
    }
}

fun String?.tilReisetilskuddListe(): List<Reisetilskudd> = objectMapper.readValue(this!!)
fun String?.tilReisetilskudd(): Reisetilskudd = objectMapper.readValue(this!!)

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
