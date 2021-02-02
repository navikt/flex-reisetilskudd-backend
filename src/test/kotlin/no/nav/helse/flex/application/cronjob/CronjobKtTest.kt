package no.nav.helse.flex.application.cronjob

import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskuddene
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.LocalDate
import java.util.*

@KtorExperimentalAPI
internal class CronjobKtTest {
    companion object {
        val applicationState = ApplicationState()
        val db = TestDB()
        val env = mockk<Environment>()
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(Network.newNetwork())
            .apply {
                addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            }
        val aivenKafkaConfig = AivenKafkaConfig(env)
        val podLeaderCoordinator = mockk<PodLeaderCoordinator>()
        val cronjob = Cronjob(
            applicationState = applicationState,
            env = env,
            database = db,
            aivenKafkaConfig = aivenKafkaConfig,
            podLeaderCoordinator = podLeaderCoordinator
        )

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            kafka.start()
        }
    }

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.electorPath } returns "dont_look_for_leader"
        every { env.bootstrapServers() } returns kafka.bootstrapServers
        every { env.securityProtocol() } returns "PLAINTEXT"
        every { env.sslTruststoreLocation() } returns "/"
        every { env.sslKeystoreLocation() } returns "/"
        every { env.sslTruststorePassword() } returns "123"
        every { env.sslKeystorePassword() } returns "123"
        every { podLeaderCoordinator.isLeader() } returns true

        applicationState.alive = true
        applicationState.ready = true
    }

    @Test
    fun `aktivering av reisetilskudd`() {
        val fnr = "123aktiver"
        val now = LocalDate.now()
        val nr1 = reisetilskudd(
            fnr = fnr,
            fom = now.minusDays(20),
            tom = now.minusDays(11),
            status = ReisetilskuddStatus.SENDT
        )
        val nr2 = reisetilskudd(
            fnr = fnr,
            fom = now.minusDays(10),
            tom = now.minusDays(1),
            status = ReisetilskuddStatus.ÅPEN
        )
        val nr3 = reisetilskudd(
            fnr = fnr,
            fom = now,
            tom = now.plusDays(9),
            status = ReisetilskuddStatus.FREMTIDIG
        )
        val nr4 = reisetilskudd(
            fnr = fnr,
            fom = now.plusDays(10),
            tom = now.plusDays(19),
            status = ReisetilskuddStatus.FREMTIDIG
        )
        db.connection.run {
            lagreReisetilskudd(nr4)
            lagreReisetilskudd(nr3)
            lagreReisetilskudd(nr2)
            lagreReisetilskudd(nr1)
        }

        val reisetilskuddeneFør = db.connection.run {
            hentReisetilskuddene(fnr)
        }
        reisetilskuddeneFør.size shouldBe 4
        reisetilskuddeneFør[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
        reisetilskuddeneFør[1].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskuddeneFør[2].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG
        reisetilskuddeneFør[3].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG

        cronjob.run()

        val reisetilskuddeneEtter = db.connection.run {
            hentReisetilskuddene(fnr)
        }
        reisetilskuddeneEtter.size shouldBe 4
        reisetilskuddeneEtter[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
        reisetilskuddeneEtter[1].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        reisetilskuddeneEtter[2].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskuddeneEtter[3].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG
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
