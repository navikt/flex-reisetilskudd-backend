package no.nav.helse.flex

import io.ktor.server.testing.TestApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.kafka.* // ktlint-disable no-wildcard-imports
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import no.nav.helse.flex.utils.getSykmeldingDto
import no.nav.helse.flex.utils.skapSykmeldingStatusKafkaMessageDTO
import no.nav.helse.flex.utils.stopApplicationNårAntallKafkaMeldingerErLest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.util.Properties

@KtorExperimentalAPI
object ReisetilskuddVerdikjedeSpek : Spek({
    val applicationState = ApplicationState()
    val fnr = "12345678901"
    val kafkaAivenConfig = mockk<AivenKafkaConfig>()

    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
        .withNetwork(Network.newNetwork())
    kafka.start()

    val kafkaConfig = Properties()
    kafkaConfig.let {
        it["bootstrap.servers"] = kafka.bootstrapServers
        it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }
    val env = mockk<Environment>()
    fun setupEnvMock() {
        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.kafkaSecurityProtocol } returns "PLAINTEXT"
        every { env.serviceuserUsername } returns "user"
        every { env.serviceuserPassword } returns "pwd"
        every { env.kafkaAutoOffsetReset } returns "earliest"
        every { env.kafkaBootstrapServers } returns kafka.bootstrapServers
    }

    setupEnvMock()

    val sykmeldingKafkaConsumer = spyk(skapSykmeldingKafkaConsumer(env))

    val producerProperties = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java
    )
    val sykmeldingKafkaProducer = KafkaProducer<String, SykmeldingMessage?>(
        producerProperties
    )

    beforeEachTest {
        setupEnvMock()
        applicationState.alive = true
        applicationState.ready = true
        every { kafkaAivenConfig.producer() } returns KafkaProducer(producerProperties)
    }

    describe("Test hele verdikjeden") {
        with(TestApplicationEngine()) {
            val testDb = TestDB()

            val reisetilskuddService = ReisetilskuddService(testDb, kafkaAivenConfig)
            val sykmeldingKafkaService = SykmeldingKafkaService(
                kafkaConsumer = sykmeldingKafkaConsumer,
                applicationState = applicationState,
                reisetilskuddService = reisetilskuddService,
                delayStart = 10L,
                environment = env
            )

            start()
            // TODO: Sett opp createApplicationEngine, skjønner meg ikke helt på auth greiene

            it("Reisetilskudd sykmelding oppretter søknad") {
                val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
                val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
                val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)

                testDb.hentReisetilskudd(fnr).size `should be equal to` 0

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
                    stopApplicationNårAntallKafkaMeldingerErLest(sykmeldingKafkaConsumer, applicationState, antallKafkaMeldinger = 1)
                    sykmeldingKafkaService.start()
                }

                val reisetilskudd = testDb.hentReisetilskudd(fnr)
                reisetilskudd.size shouldEqual 1
                reisetilskudd[0].fnr shouldEqual fnr
                reisetilskudd[0].fom shouldEqual sykmelding.sykmeldingsperioder[0].fom
                reisetilskudd[0].tom shouldEqual sykmelding.sykmeldingsperioder[0].tom
                reisetilskudd[0].status shouldEqual ReisetilskuddStatus.ÅPEN
                reisetilskudd[0].sykmeldingId shouldEqual sykmeldingId
            }
        }
    }
})
