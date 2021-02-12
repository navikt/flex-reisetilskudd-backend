package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.KafkaContainerWithProps
import no.nav.helse.flex.PostgreSQLContainerWithProps
import no.nav.helse.flex.client.pdl.HentPerson
import no.nav.helse.flex.client.pdl.Navn
import no.nav.helse.flex.client.pdl.ResponseData
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.utils.lagSykmeldingMessage
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@SpringBootTest
@Testcontainers
@DirtiesContext
@EnableMockOAuth2Server
internal class ReisetilskuddServiceTest {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()

        val fnr = "12345678901"
    }

    @Autowired
    private lateinit var reisetilskuddService: ReisetilskuddService

    @Autowired
    private lateinit var opprettReisetilskuddSoknaderService: OpprettReisetilskuddSoknaderService

    private val person = ResponseData(
        hentPerson = HentPerson(
            navn = listOf(
                Navn(
                    fornavn = "F",
                    mellomnavn = null,
                    etternavn = "Etter"
                )
            )
        )
    )

    @Test
    fun `Vi mottar en sykmelding med reisetilskudd`() {
        val syk = lagSykmeldingMessage(
            fnr = "fnr1"
        )
        opprettReisetilskuddSoknaderService.behandleSykmelding(syk, person)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr1")
        reisetilskudd.size shouldBe 1
        reisetilskudd.first().status shouldBe ReisetilskuddStatus.ÅPEN
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
        opprettReisetilskuddSoknaderService.behandleSykmelding(syk, person)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr2")
        reisetilskudd.size shouldBe 3

        reisetilskudd[0].status shouldBe ReisetilskuddStatus.FREMTIDIG
        reisetilskudd[0].fom shouldBeEqualTo now.plusDays(1)
        reisetilskudd[0].tom shouldBeEqualTo now.plusDays(24)
        ChronoUnit.DAYS.between(reisetilskudd[0].fom, reisetilskudd[0].tom) + 1 shouldBe 24

        reisetilskudd[1].status shouldBe ReisetilskuddStatus.ÅPEN
        reisetilskudd[1].fom shouldBeEqualTo now.minusDays(24)
        reisetilskudd[1].tom shouldBeEqualTo now
        ChronoUnit.DAYS.between(reisetilskudd[1].fom, reisetilskudd[1].tom) + 1 shouldBe 25

        reisetilskudd[2].status shouldBe ReisetilskuddStatus.SENDBAR
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
        opprettReisetilskuddSoknaderService.behandleSykmelding(syk, person)
        val reisetilskudd = reisetilskuddService.hentReisetilskuddene("fnr3")
        reisetilskudd.size shouldBe 3

        reisetilskudd[0].status shouldBe ReisetilskuddStatus.ÅPEN
        reisetilskudd[0].fom shouldBeEqualTo now
        reisetilskudd[0].tom shouldBeEqualTo now.plusDays(20)
        ChronoUnit.DAYS.between(reisetilskudd[0].fom, reisetilskudd[0].tom) + 1 shouldBe 21

        reisetilskudd[1].status shouldBe ReisetilskuddStatus.SENDBAR
        reisetilskudd[1].fom shouldBeEqualTo now.minusDays(25)
        reisetilskudd[1].tom shouldBeEqualTo now.minusDays(1)
        ChronoUnit.DAYS.between(reisetilskudd[1].fom, reisetilskudd[1].tom) + 1 shouldBe 25

        reisetilskudd[2].status shouldBe ReisetilskuddStatus.SENDBAR
        reisetilskudd[2].fom shouldBeEqualTo now.minusDays(50)
        reisetilskudd[2].tom shouldBeEqualTo now.minusDays(26)
        ChronoUnit.DAYS.between(reisetilskudd[2].fom, reisetilskudd[2].tom) + 1 shouldBe 25
    }
}
