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
import no.nav.helse.flex.utils.*
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class ReisetilskuddVerdikjedeTest {

    val fnr = "12345678901"

    @BeforeEach
    fun beforeEach() {
        testApp.applicationState.alive = true
        testApp.applicationState.ready = true
    }

    companion object {
        lateinit var testApp: TestApp

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            testApp = skapTestApplication()
        }
    }

    @Test
    fun `Test hele verdikjeden`() {
        with(testApp) {
            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
            val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)

            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content!!.tilReisetilskuddListe().size `should be equal to` 0
            }
            sykmeldingKafkaProducer.send(
                ProducerRecord(
                    "syfo-sendt-sykmelding",
                    SykmeldingMessage(
                        sykmelding = sykmelding,
                        event = sykmeldingStatusKafkaMessageDTO.event,
                        kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
                    )
                )
            )

            runBlocking {
                stopApplicationNårAntallKafkaMeldingerErLest(
                    sykmeldingKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = 1
                )
                sykmeldingKafkaService.start()
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldEqual HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1
                reisetilskudd[0].fnr shouldEqual fnr
                reisetilskudd[0].fom shouldEqual sykmelding.sykmeldingsperioder[0].fom
                reisetilskudd[0].tom shouldEqual sykmelding.sykmeldingsperioder[0].tom
                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.ÅPEN
                reisetilskudd[0].sykmeldingId shouldEqual sykmeldingId
            }
        }
    }
}

fun String.tilReisetilskuddListe(): List<Reisetilskudd> = objectMapper.readValue(this)
