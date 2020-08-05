package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.reisetilskudd.db.eierKvittering
import no.nav.syfo.reisetilskudd.db.eierReisetilskudd
import no.nav.syfo.reisetilskudd.db.hentReisetilskudd
import no.nav.syfo.reisetilskudd.db.lagreKvittering
import no.nav.syfo.reisetilskudd.db.lagreReisetilskudd
import no.nav.syfo.reisetilskudd.db.oppdaterReisetilskudd
import no.nav.syfo.reisetilskudd.db.slettKvittering
import no.nav.syfo.reisetilskudd.domain.KvitteringDTO
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO
import no.nav.syfo.reisetilskudd.domain.Transportmiddel
import no.nav.syfo.utils.TestDB
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeInRange
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

@KtorExperimentalAPI
object DatabaseTest : Spek({
    val db = TestDB()

    describe("lagre og hente reisetilskudd") {
        val fnr = "01010112345"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        db.hentReisetilskudd(fnr).size shouldBe 1
        db.eierReisetilskudd(fnr, rt.reisetilskuddId) shouldBe true
    }

    describe("lagre kvittering og forsikre at reell person eier kvittering") {
        val fnr = "01010154321"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val kv = kvittering(rt.reisetilskuddId)
        db.lagreKvittering(kv)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe true
        db.eierKvittering("01010112345", kv.kvitteringId) shouldBe false
        db.eierKvittering("abc", "123") shouldBe false
    }

    describe("lagre og slette kvittering") {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val kv = kvittering(rt.reisetilskuddId)
        db.lagreKvittering(kv)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe true
        db.slettKvittering(kv.kvitteringId)
        db.eierKvittering(fnr, kv.kvitteringId) shouldBe false
    }

    describe("oppdater reisetilskudd") {
        val fnr = "01010111111"
        val rt = reisetilskudd(fnr)
        db.lagreReisetilskudd(rt)
        val rtFraDB = db.hentReisetilskudd(fnr, rt.reisetilskuddId)
        rtFraDB.shouldNotBeNull()
        rtFraDB.egenBil.shouldBeInRange(0.0, 0.0)
        val svar = ReisetilskuddDTO(
            rt.reisetilskuddId,
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
        val nyRtFraDB = db.hentReisetilskudd(fnr, rt.reisetilskuddId)
        nyRtFraDB.shouldNotBeNull()
        nyRtFraDB.fnr shouldBeEqualTo fnr
        nyRtFraDB.utbetalingTilArbeidsgiver?.shouldBeFalse()
        nyRtFraDB.går?.shouldBeTrue()
        nyRtFraDB.sykler?.shouldBeTrue()
        nyRtFraDB.egenBil.shouldBeInRange(0.0, 0.0)
        nyRtFraDB.kollektivtransport.shouldBeInRange(37.0, 37.0)
    }
})

fun reisetilskudd(fnr: String): ReisetilskuddDTO =
    ReisetilskuddDTO(
        reisetilskuddId = UUID.randomUUID().toString(),
        sykmeldingId = UUID.randomUUID().toString(),
        fnr = fnr,
        fom = LocalDate.of(2020, 7, 1),
        tom = LocalDate.of(2020, 7, 20),
        orgNummer = "12345",
        orgNavn = "min arbeidsplass"
    )

fun kvittering(id: String): KvitteringDTO =
    KvitteringDTO(
        kvitteringId = UUID.randomUUID().toString(),
        reisetilskuddId = id,
        navn = "test.jpg",
        fom = LocalDate.of(2020, 7, 1),
        tom = null,
        belop = 250.0,
        transportmiddel = Transportmiddel.TAXI,
        storrelse = 1000 * 1000
    )
