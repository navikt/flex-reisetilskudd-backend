package no.nav.helse.flex.cronjob

import no.nav.helse.flex.KafkaContainerWithProps
import no.nav.helse.flex.PostgreSQLContainerWithProps
import no.nav.helse.flex.application.DatabaseInterface
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskuddene
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.util.*

@SpringBootTest
@Testcontainers
@DirtiesContext
@EnableMockOAuth2Server
internal class CronjobKtTest {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()

        val fnr = "12345678901"
    }

    @Autowired
    lateinit var db: DatabaseInterface

    @Autowired
    lateinit var cronjob: Cronjob

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
        reisetilskuddeneFør[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
        reisetilskuddeneFør[1].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskuddeneFør[2].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG
        reisetilskuddeneFør[3].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG

        cronjob.run()

        val reisetilskuddeneEtter = db.hentReisetilskuddene(fnr)
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
