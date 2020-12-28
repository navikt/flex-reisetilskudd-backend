package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.configureApplication
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.helse.flex.utils.TestDB
import no.nav.helse.flex.utils.generateJWT
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.nio.file.Paths
import java.util.*

private val issuer = "TestIssuer"
private val aud = "AUD"

@KtorExperimentalAPI
class TestApp(
    val engine: TestApplicationEngine,
    val sykmeldingKafkaProducer: KafkaProducer<String, SykmeldingMessage?>,
    val sykmeldingKafkaService: SykmeldingKafkaService,
    val applicationState: ApplicationState,
    val sykmeldingKafkaConsumer: KafkaConsumer<String, SykmeldingMessage?>
)

@KtorExperimentalAPI
fun skapTestApplication(): TestApp {
    val applicationState = ApplicationState()
    val kafkaAivenConfig = mockk<AivenKafkaConfig>()

    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
        .withNetwork(Network.newNetwork())
    val kafkaConfig = Properties()
    val env = mockk<Environment>()

    kafka.start()

    val producerProperties = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java
    )
    fun setupEnvMock() {
        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.kafkaSecurityProtocol } returns "PLAINTEXT"
        every { env.serviceuserUsername } returns "user"
        every { env.serviceuserPassword } returns "pwd"
        every { env.kafkaAutoOffsetReset } returns "earliest"
        every { env.loginserviceIdportenAudience } returns "AUD"
        every { env.kafkaBootstrapServers } returns kafka.bootstrapServers
        every { kafkaAivenConfig.producer() } returns KafkaProducer(producerProperties)
    }

    kafkaConfig.let {
        it["bootstrap.servers"] = kafka.bootstrapServers
        it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }
    setupEnvMock()

    val sykmeldingKafkaConsumer = spyk(skapSykmeldingKafkaConsumer(env))

    val testDb = TestDB()

    val sykmeldingKafkaProducer = KafkaProducer<String, SykmeldingMessage?>(
        producerProperties
    )
    val reisetilskuddService = ReisetilskuddService(testDb, kafkaAivenConfig)
    val sykmeldingKafkaService = SykmeldingKafkaService(
        kafkaConsumer = sykmeldingKafkaConsumer,
        applicationState = applicationState,
        reisetilskuddService = reisetilskuddService,
        delayStart = 10L,
        environment = env
    )
    val e = TestApplicationEngine()
    with(e) {

        val path = "src/test/resources/jwkset.json"
        val uri = Paths.get(path).toUri().toURL()
        val jwkProvider = JwkProviderBuilder(uri).build()
        start()

        application.configureApplication(
            env = env,
            applicationState = applicationState,
            reisetilskuddService = reisetilskuddService,
            jwkProvider = jwkProvider,
            issuer = issuer
        )
    }

    return TestApp(
        engine = e,
        sykmeldingKafkaConsumer = sykmeldingKafkaConsumer,
        sykmeldingKafkaProducer = sykmeldingKafkaProducer,
        sykmeldingKafkaService = sykmeldingKafkaService,
        applicationState = applicationState
    )
}

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
