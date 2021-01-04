package no.nav.helse.flex

import no.nav.helse.flex.reisetilskudd.db.*
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.Transportmiddel
import no.nav.helse.flex.utils.TestDB
import org.amshove.kluent.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class DatabaseTest {
    val db = TestDB()

    @Test
    fun `lagre og hente reisetilskudd`() {
        val fnr = "01010112345"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        db.hentReisetilskuddene(fnr).size shouldBe 1
        db.eierReisetilskudd(fnr, rt.reisetilskuddId) shouldBe true
    }

    @Test
    fun `lagre kvittering og forsikre at reell person eier kvittering`() {
        val fnr = "01010154321"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val kv = kvittering(rt.reisetilskuddId)
        db.lagreKvittering(kv)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe true
        db.eierKvittering("01010112345", kv.kvitteringId) shouldBe false
        db.eierKvittering("abc", "123") shouldBe false
    }

    @Test
    fun `lagre og slette kvittering`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val kv = kvittering(rt.reisetilskuddId)
        db.lagreKvittering(kv)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe true
        db.slettKvittering(kv.kvitteringId)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe false
    }

    @Test
    fun `oppdater reisetilskudd`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val rtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
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
            kollektivtransport = 37.0
        )
        db.oppdaterReisetilskudd(svar)
        val nyRtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        nyRtFraDB.shouldNotBeNull()
        nyRtFraDB.fnr shouldBeEqualTo fnr
        nyRtFraDB.status shouldEqual ReisetilskuddStatus.ÅPEN
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
        val rt = reisetilskudd(fnr)

        db.lagreReisetilskudd(rt)
        val rtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.sendt.shouldBeNull()
        rtFraDB.status shouldEqual ReisetilskuddStatus.ÅPEN

        db.sendReisetilskudd(fnr, rt.reisetilskuddId)
        val nyRtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        nyRtFraDB.shouldNotBeNull()
        nyRtFraDB.sendt.shouldNotBeNull()
        nyRtFraDB.status shouldEqual ReisetilskuddStatus.SENDT

        db.sendReisetilskudd(fnr, rt.reisetilskuddId)
        val nyereRtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        nyereRtFraDB.shouldNotBeNull()
        nyereRtFraDB.shouldNotBeNull()
        nyRtFraDB.sendt shouldEqual nyereRtFraDB.sendt
        nyereRtFraDB.status shouldEqual ReisetilskuddStatus.SENDT
    }

    @Test
    fun `avbryt og gjenåpne reisetilskudd`() {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)

        db.lagreReisetilskudd(rt)
        val rtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.sendt.shouldBeNull()
        rtFraDB.status shouldEqual ReisetilskuddStatus.ÅPEN

        db.avbrytReisetilskudd(fnr, rt.reisetilskuddId)
        val avbruttRtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        avbruttRtFraDB.shouldNotBeNull()
        avbruttRtFraDB.status shouldEqual ReisetilskuddStatus.AVBRUTT
        avbruttRtFraDB.avbrutt.shouldNotBeNull()

        db.gjenapneReisetilskudd(fnr, rt.reisetilskuddId)
        val gjenåpnetRtFraDB = db.hentReisetilskudd(rt.reisetilskuddId)
        gjenåpnetRtFraDB.shouldNotBeNull()
        gjenåpnetRtFraDB.status shouldEqual ReisetilskuddStatus.ÅPEN
        gjenåpnetRtFraDB.avbrutt.shouldBeNull()
    }

    @Test
    fun `aktivering av reisetilskudd`() {
        val fnr = "123aktiver"
        val rtÅpen = reisetilskudd(fnr).copy(
            fom = LocalDate.of(2020, 7, 1),
            tom = LocalDate.of(2020, 7, 20),
            status = ReisetilskuddStatus.ÅPEN
        )
        val rtFremtidig = reisetilskudd(fnr).copy(
            fom = LocalDate.of(2020, 7, 21),
            tom = LocalDate.of(2020, 8, 5),
            status = ReisetilskuddStatus.FREMTIDIG
        )

        db.lagreReisetilskudd(rtFremtidig)
        db.lagreReisetilskudd(rtÅpen)
        val reisetilskuddeneFør = db.hentReisetilskuddene(fnr)
        reisetilskuddeneFør.size shouldBe 2
        reisetilskuddeneFør[0].status shouldEqual ReisetilskuddStatus.ÅPEN
        reisetilskuddeneFør[1].status shouldEqual ReisetilskuddStatus.FREMTIDIG

        val åpneReisetilskuddSkalIkkeAktiveres = db.finnReisetilskuddSomSkalAktiveres(LocalDate.of(2020, 7, 21))
        åpneReisetilskuddSkalIkkeAktiveres.size shouldBe 0

        val aktiveresIkkeForSammeDag = db.finnReisetilskuddSomSkalAktiveres(LocalDate.of(2020, 8, 5))
        aktiveresIkkeForSammeDag.size shouldBe 0

        val aktiveresNårTomErPassert = db.finnReisetilskuddSomSkalAktiveres(LocalDate.of(2020, 8, 6))
        aktiveresNårTomErPassert.size shouldBe 1
        db.aktiverReisetilskudd(aktiveresNårTomErPassert.first())

        val reisetilskuddeneEtter = db.hentReisetilskuddene(fnr)
        reisetilskuddeneEtter.size shouldBe 2
        reisetilskuddeneEtter[0].status shouldEqual ReisetilskuddStatus.ÅPEN
        reisetilskuddeneEtter[1].status shouldEqual ReisetilskuddStatus.ÅPEN
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
        oppfølgende = false
    )

private fun kvittering(id: String): Kvittering =
    Kvittering(
        kvitteringId = UUID.randomUUID().toString(),
        reisetilskuddId = id,
        navn = "test.jpg",
        fom = LocalDate.of(2020, 7, 1),
        tom = null,
        belop = 250.0,
        transportmiddel = Transportmiddel.TAXI,
        storrelse = 1000 * 1000
    )
