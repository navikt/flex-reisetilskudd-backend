package no.nav.helse.flex.application.cronjob

import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.Environment
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.JacksonKafkaSerializer
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskuddene
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.util.*

@KtorExperimentalAPI
internal class CronjobKtTest {
    companion object {
        val db = TestDB()
        val env = mockk<Environment>()
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(Network.newNetwork())
        val aivenKafkaConfig = mockk<AivenKafkaConfig>()

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            kafka.start()
        }
    }

    @BeforeEach
    fun beforeEach() {
        val producerProperties = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java
        )

        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.electorPath } returns "dont_look_for_leader"
        every { aivenKafkaConfig.producer() } returns KafkaProducer(producerProperties)
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
        db.lagreReisetilskudd(nr4)
        db.lagreReisetilskudd(nr3)
        db.lagreReisetilskudd(nr2)
        db.lagreReisetilskudd(nr1)

        val reisetilskuddeneFør = db.hentReisetilskuddene(fnr)
        reisetilskuddeneFør.size shouldBe 4
        reisetilskuddeneFør[0].status shouldEqual ReisetilskuddStatus.SENDT
        reisetilskuddeneFør[1].status shouldEqual ReisetilskuddStatus.ÅPEN
        reisetilskuddeneFør[2].status shouldEqual ReisetilskuddStatus.FREMTIDIG
        reisetilskuddeneFør[3].status shouldEqual ReisetilskuddStatus.FREMTIDIG

        cronJobTask(
            env = env,
            database = db,
            aivenKafkaConfig = aivenKafkaConfig
        )

        val reisetilskuddeneEtter = db.hentReisetilskuddene(fnr)
        reisetilskuddeneEtter.size shouldBe 4
        reisetilskuddeneEtter[0].status shouldEqual ReisetilskuddStatus.SENDT
        reisetilskuddeneEtter[1].status shouldEqual ReisetilskuddStatus.SENDBAR
        reisetilskuddeneEtter[2].status shouldEqual ReisetilskuddStatus.ÅPEN
        reisetilskuddeneEtter[3].status shouldEqual ReisetilskuddStatus.FREMTIDIG
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
        oppfølgende = oppfølgende
    )
}
