package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.application.objectMapper
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.domain.*
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
        val tom = LocalDate.now().minusDays(1)
        val fom = LocalDate.now().minusDays(5)
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1
                reisetilskudd[0].fnr shouldBeEqualTo fnr
                reisetilskudd[0].fom shouldBeEqualTo fom
                reisetilskudd[0].tom shouldBeEqualTo tom
                reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
                reisetilskudd[0].sykmeldingId shouldBeEqualTo sykmeldingId
                reisetilskudd[0].egenBil shouldBeEqualTo 0.0
                reisetilskudd[0].sykler.shouldBeNull()
                reisetilskudd[0].kollektivtransport shouldBeEqualTo 0.0
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
                response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content.tilReisetilskudd()
                reisetilskudd.sykler?.shouldBeTrue()
            }
        }
    }

    @Test
    @Order(6)
    fun `Vi kan laste opp en kvittering`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/$id/kvittering") {
                    medSelvbetjeningToken(fnr)
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    val kvittering = Kvittering(
                        blobId = "123456",
                        belop = 133700,
                        storrelse = 12,
                        transportmiddel = Transportmiddel.EGEN_BIL,
                        datoForReise = LocalDate.now(),
                        navn = "bilde123.jpg"
                    )
                    setBody(kvittering.serialisertTilString())
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.Created
                val kvittering = response.content.tilKvittering()
                kvittering.datoForReise.`should be equal to`(LocalDate.now())
                kvittering.kvitteringId.shouldNotBeNull()
            }
        }
    }

    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        with(testApp) {
            val kvitteringer = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].kvitteringer

            kvitteringer.size `should be equal to` 1
            val kvittering = kvitteringer.first()

            kvittering.datoForReise.`should be equal to`(LocalDate.now())
            kvittering.kvitteringId.shouldNotBeNull()
            kvittering.storrelse.`should be equal to`(12)
            kvittering.belop.`should be equal to`(133700)
        }
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {
        with(testApp) {
            val reisetilskudd = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0]

            reisetilskudd.kvitteringer.size.`should be equal to`(1)

            with(
                engine.handleRequest(
                    HttpMethod.Delete,
                    "/api/v1/reisetilskudd/${reisetilskudd.reisetilskuddId}/kvittering/${reisetilskudd.kvitteringer[0].kvitteringId}"
                ) {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }

            val reisetilskuddEtter = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0]

            reisetilskuddEtter.kvitteringer.size.`should be equal to`(0)
        }
    }

    @Test
    @Order(9)
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
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
                reisetilskudd[0].sendt.shouldNotBeNull()
            }
        }
    }
}

fun String?.tilReisetilskuddListe(): List<Reisetilskudd> = objectMapper.readValue(this!!)
fun String?.tilReisetilskudd(): Reisetilskudd = objectMapper.readValue(this!!)
fun String?.tilKvittering(): Kvittering = objectMapper.readValue(this!!)

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
