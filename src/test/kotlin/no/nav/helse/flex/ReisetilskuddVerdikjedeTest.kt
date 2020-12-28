package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.configureApplication
import no.nav.helse.flex.application.objectMapper
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.utils.*
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.util.Properties

@KtorExperimentalAPI
internal class ReisetilskuddVerdikjedeTest {
    val applicationState = ApplicationState()
    val fnr = "12345678901"
    val kafkaAivenConfig = mockk<AivenKafkaConfig>()

    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
        .withNetwork(Network.newNetwork())
    val kafkaConfig = Properties()
    val env = mockk<Environment>()

    init {
        kafka.start()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        setupEnvMock()
    }

    fun setupEnvMock() {
        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.kafkaSecurityProtocol } returns "PLAINTEXT"
        every { env.serviceuserUsername } returns "user"
        every { env.serviceuserPassword } returns "pwd"
        every { env.kafkaAutoOffsetReset } returns "earliest"
        every { env.loginserviceIdportenAudience } returns "AUD"
        every { env.kafkaBootstrapServers } returns kafka.bootstrapServers
    }

    val sykmeldingKafkaConsumer = spyk(skapSykmeldingKafkaConsumer(env))

    val producerProperties = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java
    )
    val sykmeldingKafkaProducer = KafkaProducer<String, SykmeldingMessage?>(
        producerProperties
    )

    @BeforeEach
    fun bedforeEach() {
        setupEnvMock()
        applicationState.alive = true
        applicationState.ready = true
        every { kafkaAivenConfig.producer() } returns KafkaProducer(producerProperties)
    }

    @Test
    fun `Test hele verdikjeden`() {
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

            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            start()
            val issuer = "TestIssuer"
            val aud = "AUD"
            application.configureApplication(
                env = env,
                applicationState = applicationState,
                reisetilskuddService = reisetilskuddService,
                jwkProvider = jwkProvider,
                issuer = issuer
            )

            fun TestApplicationRequest.medSelvbetjeningToken(subject: String, level: String = "Level4") {
                addHeader(
                    HttpHeaders.Authorization,
                    "Bearer ${
                    generateJWT(
                        audience = aud,
                        issuer = issuer,
                        subject = subject,
                        level = level
                    )
                    }"
                )
            }

            val sykmeldingStatusKafkaMessageDTO = skapSykmeldingStatusKafkaMessageDTO(fnr = fnr)
            val sykmeldingId = sykmeldingStatusKafkaMessageDTO.event.sykmeldingId
            val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId)

            with(
                handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
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
                handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
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
