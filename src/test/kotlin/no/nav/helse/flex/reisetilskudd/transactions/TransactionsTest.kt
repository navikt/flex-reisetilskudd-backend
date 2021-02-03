package no.nav.helse.flex.reisetilskudd.transactions

import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.Environment
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskuddene
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

@KtorExperimentalAPI
internal class TransactionsTest {
    companion object {
        val fnr = "fnr"
        val db = TestDB()
        val env = mockk<Environment>()
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(Network.newNetwork())
            .apply {
                addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            }.also {
                it.start()
            }
        val aivenKafkaConfig = AivenKafkaConfig(env)
        val handler = Transactions(
            database = db,
            aivenKafkaConfig = aivenKafkaConfig
        )
        lateinit var consumer: KafkaConsumer<String, Reisetilskudd>

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            clearAllMocks()
            every { env.bootstrapServers() } returns kafka.bootstrapServers
            every { env.securityProtocol() } returns "PLAINTEXT"
            every { env.sslTruststoreLocation() } returns "/"
            every { env.sslKeystoreLocation() } returns "/"
            every { env.sslTruststorePassword() } returns "123"
            every { env.sslKeystorePassword() } returns "123"

            every { env.applicationName } returns "test"
            every { env.kafkaAutoOffsetReset } returns "latest"

            consumer = aivenKafkaConfig.consumer().also {
                it.subscribe(listOf(AivenKafkaConfig.topic))
                it.poll(Duration.ofSeconds(1))
            }
        }
    }

    @Test
    fun `Transactions blir commited`() {
        val rt = reisetilskudd(fnr = fnr)

        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 0
        }

        handler.transaction {
            lagreReisetilskudd(rt)
        }

        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 1
        }
        consumer.poll(Duration.ofSeconds(5))
            .first().value()
            .reisetilskuddId shouldBeEqualTo rt.reisetilskuddId
    }

    @Test
    fun `Exceptions ruller tilbake en transaction`() {
        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 1
        }

        kotlin.runCatching {
            handler.transaction {
                val rt = reisetilskudd(fnr = fnr)
                lagreReisetilskudd(rt)
                val rtFraConnection = hentReisetilskudd(rt.reisetilskuddId)!!
                rt.reisetilskuddId shouldBeEqualTo rtFraConnection.reisetilskuddId
                rt.status shouldBeEqualTo rtFraConnection.status
                rt.fom shouldBeEqualTo rtFraConnection.fom

                hentReisetilskuddene(fnr).size shouldBe 2

                throw Exception("en feil")
            }
        }

        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 1
        }

        consumer.poll(Duration.ofSeconds(1))
            .firstOrNull() shouldBe null
    }

    @Test
    fun `Exceptions ruller tilbake mange reisetilskudd`() {
        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 1
        }

        kotlin.runCatching {
            handler.transaction {
                for (i in 0..100) {
                    val rt = reisetilskudd(fnr = fnr)
                    lagreReisetilskudd(rt)
                }
                hentReisetilskuddene(fnr).size shouldBe 1 + 100
                throw Exception("en feil")
            }
        }

        db.connection.run {
            hentReisetilskuddene(fnr).size shouldBe 1
        }

        consumer.poll(Duration.ofSeconds(1))
            .firstOrNull() shouldBe null
    }

    private fun reisetilskudd(
        reisetilskuddId: String = UUID.randomUUID().toString(),
        sykmeldingId: String = UUID.randomUUID().toString(),
        fnr: String,
        fom: LocalDate = LocalDate.now().minusDays(10),
        tom: LocalDate = LocalDate.now(),
        orgNummer: String = "12345",
        orgNavn: String = "min arbeidsplass",
        status: ReisetilskuddStatus? = null,
        oppfølgende: Boolean = false
    ) = Reisetilskudd(
        reisetilskuddId = reisetilskuddId,
        sykmeldingId = sykmeldingId,
        fnr = fnr,
        fom = fom,
        tom = tom,
        orgNummer = orgNummer,
        orgNavn = orgNavn,
        status = status ?: reisetilskuddStatus(fom, tom),
        oppfølgende = oppfølgende,
        opprettet = Instant.now(),
    )
}
