package no.nav.helse.flex.reisetilskudd

import io.ktor.util.*
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.flex.Environment
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import no.nav.helse.flex.utils.lagSykmeldingMessage
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@KtorExperimentalAPI
internal class ReisetilskuddServiceTest {
    companion object {
        val db = TestDB()
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(Network.newNetwork())
            .apply {
                addEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                addEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            }
        val env = mockk<Environment>()
        val aivenKafkaConfig = AivenKafkaConfig(env)
        val reisetilskuddService = ReisetilskuddService(
            database = db,
            aivenKafkaConfig = aivenKafkaConfig
        )

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            kafka.start()
            every { env.bootstrapServers() } returns kafka.bootstrapServers
            every { env.securityProtocol() } returns "PLAINTEXT"
            every { env.sslTruststoreLocation() } returns "/"
            every { env.sslKeystoreLocation() } returns "/"
            every { env.sslTruststorePassword() } returns "123"
            every { env.sslKeystorePassword() } returns "123"
        }
    }

    @Test
    fun `Vi mottar en sykmelding med reisetilskudd`() {
        val syk = lagSykmeldingMessage(
            fnr = "fnr1"
        )
        reisetilskuddService.behandleSykmelding(syk)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr1")
        reisetilskudd.size shouldBe 1
        reisetilskudd.first().status shouldBe ReisetilskuddStatus.ÅPEN
        reisetilskudd.first().oppfølgende shouldBe false
    }

    @Test
    fun `Vi mottar en sykmelding med en lang periode`() {
        val now = LocalDate.now()
        val syk = lagSykmeldingMessage(
            fnr = "fnr2",
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeDTO(
                    fom = now.minusDays(49),
                    tom = now.plusDays(24),
                    type = PeriodetypeDTO.REISETILSKUDD,
                    reisetilskudd = true,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )
        reisetilskuddService.behandleSykmelding(syk)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr2")
        reisetilskudd.size shouldBe 3

        reisetilskudd[0].status shouldBe ReisetilskuddStatus.FREMTIDIG
        reisetilskudd[0].oppfølgende shouldBe true
        reisetilskudd[0].fom shouldBeEqualTo now.plusDays(1)
        reisetilskudd[0].tom shouldBeEqualTo now.plusDays(24)
        ChronoUnit.DAYS.between(reisetilskudd[0].fom, reisetilskudd[0].tom) + 1 shouldBe 24

        reisetilskudd[1].status shouldBe ReisetilskuddStatus.ÅPEN
        reisetilskudd[1].oppfølgende shouldBe true
        reisetilskudd[1].fom shouldBeEqualTo now.minusDays(24)
        reisetilskudd[1].tom shouldBeEqualTo now
        ChronoUnit.DAYS.between(reisetilskudd[1].fom, reisetilskudd[1].tom) + 1 shouldBe 25

        reisetilskudd[2].status shouldBe ReisetilskuddStatus.SENDBAR
        reisetilskudd[2].oppfølgende shouldBe false
        reisetilskudd[2].fom shouldBeEqualTo now.minusDays(49)
        reisetilskudd[2].tom shouldBeEqualTo now.minusDays(25)
        ChronoUnit.DAYS.between(reisetilskudd[2].fom, reisetilskudd[2].tom) + 1 shouldBe 25
    }

    @Test
    fun `Vi mottar en sykmelding med 2 perioder`() {
        val now = LocalDate.now()
        val syk = lagSykmeldingMessage(
            fnr = "fnr3",
            sykmeldingsperioder = listOf(
                SykmeldingsperiodeDTO(
                    fom = now.minusDays(50),
                    tom = now.minusDays(1),
                    type = PeriodetypeDTO.REISETILSKUDD,
                    reisetilskudd = true,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                ),
                SykmeldingsperiodeDTO(
                    fom = now,
                    tom = now.plusDays(20),
                    type = PeriodetypeDTO.REISETILSKUDD,
                    reisetilskudd = true,
                    aktivitetIkkeMulig = null,
                    behandlingsdager = null,
                    gradert = null,
                    innspillTilArbeidsgiver = null
                )
            )
        )
        reisetilskuddService.behandleSykmelding(syk)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr3")
        reisetilskudd.size shouldBe 3

        reisetilskudd[0].status shouldBe ReisetilskuddStatus.ÅPEN
        reisetilskudd[0].oppfølgende shouldBe true
        reisetilskudd[0].fom shouldBeEqualTo now
        reisetilskudd[0].tom shouldBeEqualTo now.plusDays(20)
        ChronoUnit.DAYS.between(reisetilskudd[0].fom, reisetilskudd[0].tom) + 1 shouldBe 21

        reisetilskudd[1].status shouldBe ReisetilskuddStatus.SENDBAR
        reisetilskudd[1].oppfølgende shouldBe true
        reisetilskudd[1].fom shouldBeEqualTo now.minusDays(25)
        reisetilskudd[1].tom shouldBeEqualTo now.minusDays(1)
        ChronoUnit.DAYS.between(reisetilskudd[1].fom, reisetilskudd[1].tom) + 1 shouldBe 25

        reisetilskudd[2].status shouldBe ReisetilskuddStatus.SENDBAR
        reisetilskudd[2].oppfølgende shouldBe false
        reisetilskudd[2].fom shouldBeEqualTo now.minusDays(50)
        reisetilskudd[2].tom shouldBeEqualTo now.minusDays(26)
        ChronoUnit.DAYS.between(reisetilskudd[2].fom, reisetilskudd[2].tom) + 1 shouldBe 25
    }
}
