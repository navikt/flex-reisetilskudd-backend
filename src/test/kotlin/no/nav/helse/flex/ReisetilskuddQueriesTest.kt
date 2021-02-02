package no.nav.helse.flex

import no.nav.helse.flex.reisetilskudd.db.*
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.Transportmiddel
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import no.nav.helse.flex.utils.TestDB
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*

internal class DatabaseTest {
    val db = TestDB()

    @Test
    fun `lagre og hente reisetilskudd`() {
        val fnr = "01010112345"
        val rt = reisetilskudd(fnr)
        db.connection.lagreReisetilskudd(rt)
        db.connection.hentReisetilskuddene(fnr).size shouldBe 1
        db.connection.eierReisetilskudd(fnr, rt.reisetilskuddId) shouldBe true
    }

    @Test
    fun `lagre og slette kvittering`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.connection.lagreReisetilskudd(rt)
        val kv = kvittering()
        val kvitteringId = "1234"
        db.connection.lagreKvittering(kv, rt.reisetilskuddId, kvitteringId)
        val lagretKvittering = db.connection.hentKvittering(kvitteringId)
        val reisetilskuddene = db.connection.hentReisetilskuddene(fnr)
        reisetilskuddene.size shouldBe 1
        val antallSlettet = db.connection.slettKvittering(lagretKvittering!!.kvitteringId!!, rt.reisetilskuddId)
        antallSlettet `should be equal to` 1
    }

    @Test
    fun `oppdater reisetilskudd`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.connection.lagreReisetilskudd(rt)
        val rtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.egenBil.shouldBeInRange(0.0, 0.0)
        val svar = Reisetilskudd(
            status = ReisetilskuddStatus.FREMTIDIG,
            oppfølgende = false,
            reisetilskuddId = rt.reisetilskuddId,
            sykmeldingId = "abc",
            fnr = "abc",
            fom = LocalDate.MAX,
            tom = LocalDate.MAX,
            orgNummer = "abc",
            orgNavn = "abc",
            utbetalingTilArbeidsgiver = false,
            går = true,
            sykler = true,
            egenBil = 0.0,
            kollektivtransport = 37.0,
            opprettet = Instant.now(),
        )
        db.connection.oppdaterReisetilskudd(svar)
        val nyRtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        nyRtFraDB.shouldNotBeNull()
        nyRtFraDB.fnr shouldBeEqualTo fnr
        nyRtFraDB.status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        nyRtFraDB.utbetalingTilArbeidsgiver?.shouldBeFalse()
        nyRtFraDB.går?.shouldBeTrue()
        nyRtFraDB.sykler?.shouldBeTrue()
        nyRtFraDB.oppfølgende.shouldBeFalse()
        nyRtFraDB.egenBil.shouldBeInRange(0.0, 0.0)
        nyRtFraDB.kollektivtransport.shouldBeInRange(37.0, 37.0)
    }

    @Test
    fun `sende reisetilskudd`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr).copy(status = ReisetilskuddStatus.SENDBAR)

        db.connection.lagreReisetilskudd(rt)
        val rtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.sendt.shouldBeNull()
        rtFraDB.status shouldBeEqualTo ReisetilskuddStatus.SENDBAR

        db.connection.sendReisetilskudd(fnr, rt.reisetilskuddId)
        val nyRtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        nyRtFraDB.shouldNotBeNull()
        nyRtFraDB.sendt.shouldNotBeNull()
        nyRtFraDB.status shouldBeEqualTo ReisetilskuddStatus.SENDT

        db.connection.sendReisetilskudd(fnr, rt.reisetilskuddId)
        val nyereRtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        nyereRtFraDB.shouldNotBeNull()
        nyereRtFraDB.shouldNotBeNull()
        nyRtFraDB.sendt shouldBeEqualTo nyereRtFraDB.sendt
        nyereRtFraDB.status shouldBeEqualTo ReisetilskuddStatus.SENDT
    }

    @Test
    fun `avbryt og gjenåpne reisetilskudd`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)

        db.connection.lagreReisetilskudd(rt)
        val rtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.sendt.shouldBeNull()
        rtFraDB.status shouldBeEqualTo ReisetilskuddStatus.ÅPEN

        db.connection.avbrytReisetilskudd(fnr, rt.reisetilskuddId)
        val avbruttRtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        avbruttRtFraDB.shouldNotBeNull()
        avbruttRtFraDB.status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
        avbruttRtFraDB.avbrutt.shouldNotBeNull()

        db.connection.gjenapneReisetilskudd(fnr, rt.reisetilskuddId, reisetilskuddStatus(rt.fom, rt.tom))
        val gjenåpnetRtFraDB = db.connection.hentReisetilskudd(rt.reisetilskuddId)
        gjenåpnetRtFraDB.shouldNotBeNull()
        gjenåpnetRtFraDB.status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        gjenåpnetRtFraDB.avbrutt.shouldBeNull()
    }

    @Test
    fun `aktivering av reisetilskudd`() {
        val fnr = "123aktiver"
        val rtFremtidig = reisetilskudd(fnr).copy(
            fom = LocalDate.of(2020, 7, 1),
            tom = LocalDate.of(2020, 7, 20),
            status = ReisetilskuddStatus.FREMTIDIG
        )

        db.connection.lagreReisetilskudd(rtFremtidig)
        val reisetilskuddeneFør = db.connection.hentReisetilskuddene(fnr)
        reisetilskuddeneFør.size shouldBe 1
        reisetilskuddeneFør[0].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG

        val åpnesIkkeFørFom = db.connection.finnReisetilskuddSomSkalÅpnes(reisetilskuddeneFør[0].fom.minusDays(1))
        åpnesIkkeFørFom.size shouldBe 0

        val åpnesNårDatoErFom = db.connection.finnReisetilskuddSomSkalÅpnes(reisetilskuddeneFør[0].fom)
        åpnesNårDatoErFom.size shouldBe 1

        val åpnesNårDatoErEtterFom = db.connection.finnReisetilskuddSomSkalÅpnes(reisetilskuddeneFør[0].fom.plusDays(5))
        åpnesNårDatoErEtterFom.size shouldBe 1

        db.connection.åpneReisetilskudd(åpnesNårDatoErEtterFom.first())
        val åpneReisetilskudd = db.connection.hentReisetilskuddene(fnr)
        åpneReisetilskudd.size shouldBe 1
        åpneReisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN

        val blirIkkeSendtbartFørTom = db.connection.finnReisetilskuddSomSkalBliSendbar(åpneReisetilskudd[0].tom.minusDays(5))
        blirIkkeSendtbartFørTom.size shouldBe 0

        val blirIkkeSendtbartFørTomErPassert = db.connection.finnReisetilskuddSomSkalBliSendbar(åpneReisetilskudd[0].tom)
        blirIkkeSendtbartFørTomErPassert.size shouldBe 0

        val blirSendtbartNårTomErPassert = db.connection.finnReisetilskuddSomSkalBliSendbar(åpneReisetilskudd[0].tom.plusDays(1))
        blirSendtbartNårTomErPassert.size shouldBe 1

        db.connection.sendbarReisetilskudd(blirSendtbartNårTomErPassert.first())
        val reisetilskuddeneEtter = db.connection.hentReisetilskuddene(fnr)
        reisetilskuddeneEtter.size shouldBe 1
        reisetilskuddeneEtter[0].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
    }

    @Test
    fun `reisetilskuddStatus test`() {
        val now = LocalDate.now()

        reisetilskuddStatus(
            fom = now.plusDays(10),
            tom = now.plusDays(20)
        ) shouldBe ReisetilskuddStatus.FREMTIDIG

        reisetilskuddStatus(
            fom = now,
            tom = now.plusDays(10)
        ) shouldBe ReisetilskuddStatus.ÅPEN

        reisetilskuddStatus(
            fom = now.minusDays(10),
            tom = now
        ) shouldBe ReisetilskuddStatus.ÅPEN

        reisetilskuddStatus(
            fom = now.minusDays(11),
            tom = now.minusDays(1)
        ) shouldBe ReisetilskuddStatus.SENDBAR

        reisetilskuddStatus(
            fom = now.minusDays(100),
            tom = now.minusDays(90)
        ) shouldBe ReisetilskuddStatus.SENDBAR
    }
}

private fun reisetilskudd(fnr: String): Reisetilskudd =
    Reisetilskudd(
        reisetilskuddId = UUID.randomUUID().toString(),
        sykmeldingId = UUID.randomUUID().toString(),
        fnr = fnr,
        fom = LocalDate.of(2020, 7, 1),
        tom = LocalDate.of(2020, 7, 20),
        orgNummer = "12345",
        orgNavn = "min arbeidsplass",
        status = ReisetilskuddStatus.ÅPEN,
        oppfølgende = false,
        opprettet = Instant.now(),
    )

private fun kvittering(): Kvittering =
    Kvittering(
        blobId = UUID.randomUUID().toString(),
        navn = "test.jpg",
        datoForReise = LocalDate.of(2020, 7, 1),
        belop = 25000,
        transportmiddel = Transportmiddel.TAXI,
        storrelse = 1000 * 1000
    )
