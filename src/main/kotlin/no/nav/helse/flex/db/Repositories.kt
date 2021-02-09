package no.nav.helse.flex.db

import no.nav.helse.flex.domain.*
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface EnkelReisetilskuddSoknadRepository : CrudRepository<EnkelReisetilskuddSoknad, String> {

    @Query(
        """
            select * FROM reisetilskudd_soknad
            WHERE status = 'ÅPEN'
            AND tom < :now
        """
    )
    fun finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<EnkelReisetilskuddSoknad>

    @Query(
        """
            select * FROM reisetilskudd_soknad
            WHERE status = 'FREMTIDIG'
            AND fom <= :now
        """
    )
    fun finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<EnkelReisetilskuddSoknad>
}

@Repository
interface KvitteringRepository : CrudRepository<KvitteringDbRecord, String> {
    fun findKvitteringDbRecordByReisetilskuddSoknadId(reisetilskuddSoknadId: String): List<KvitteringDbRecord>
}

@Repository
interface SporsmalRepository : CrudRepository<SporsmalDbRecord, String> {
    fun findSporsmalByReisetilskuddSoknadId(reisetilskuddSoknadId: String): List<SporsmalDbRecord>
    fun findSporsmalByOversporsmalId(oversporsmalId: String): List<SporsmalDbRecord>
}

@Repository
interface SvarRepository : CrudRepository<SvarDbRecord, String> {
    fun findSvarDbRecordsBySporsmalIdIn(ider: List<String>): List<SvarDbRecord>
}

@Repository
interface ReisetilskuddSoknadRepository : CrudRepository<ReisetilskuddSoknadDbRecord, String> {
    fun findReisetilskuddSoknadByFnr(fnr: String): List<ReisetilskuddSoknadDbRecord>
}

@Table("sporsmal")
data class SporsmalDbRecord(
    @Id
    val id: String,
    val reisetilskuddSoknadId: String? = null,
    val oversporsmalId: String? = null,
    val tag: Tag,
    val overskrift: String? = null,
    val sporsmalstekst: String? = null,
    val undertekst: String? = null,
    val svartype: Svartype,
    val min: String? = null,
    val max: String? = null,
    val kriterieForVisningAvUndersporsmal: KriterieForVisningAvUndersporsmal? = null,
)

@Table("svar")
data class SvarDbRecord(
    @Id
    val id: String,
    val sporsmalId: String,
    val verdi: String
)

@Table("reisetilskudd_soknad")
data class ReisetilskuddSoknadDbRecord(
    @Id
    val id: String,
    val status: ReisetilskuddStatus,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: Instant,
    val endret: Instant,
    val sendt: Instant? = null,
    val avbrutt: Instant? = null,
    val arbeidsgiverOrgnummer: String?,
    val arbeidsgiverNavn: String?,
)

@Table("kvittering")
data class KvitteringDbRecord(
    @Id
    val id: String,
    val reisetilskuddSoknadId: String? = null,
    val blobId: String,
    val datoForUtgift: LocalDate,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Transportmiddel,
    val opprettet: Instant,
)

fun SvarDbRecord.tilSvar(): Svar = Svar(id, verdi)

fun ReisetilskuddSoknad.tilReisetilskuddSoknadDbRecord(): ReisetilskuddSoknadDbRecord = ReisetilskuddSoknadDbRecord(
    id = this.id,
    status = this.status,
    sykmeldingId = this.sykmeldingId,
    fnr = this.fnr,
    fom = this.fom,
    tom = this.tom,
    opprettet = this.opprettet,
    endret = this.endret,
    sendt = this.sendt,
    avbrutt = this.avbrutt,
    arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
    arbeidsgiverNavn = this.arbeidsgiverNavn
)

fun Kvittering.tilKvitteringDbRecord(reisetilskuddSoknadId: String): KvitteringDbRecord = KvitteringDbRecord(
    id = id,
    blobId = blobId,
    datoForUtgift = datoForUtgift,
    belop = belop,
    typeUtgift = typeUtgift,
    opprettet = opprettet ?: Instant.now(),
    reisetilskuddSoknadId = reisetilskuddSoknadId,
)

fun SporsmalDbRecord.tilSporsmal(undersporsmal: List<Sporsmal>, svar: List<Svar>): Sporsmal {
    return Sporsmal(
        id = id,
        tag = tag,
        overskrift = overskrift,
        sporsmalstekst = sporsmalstekst,
        undertekst = undertekst,
        svartype = svartype,
        min = min,
        max = max,
        kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
        undersporsmal = undersporsmal,
        svar = svar,
    )
}

fun KvitteringDbRecord.tilKvittering(): Kvittering = Kvittering(
    id = id,
    blobId = blobId,
    datoForUtgift = datoForUtgift,
    belop = belop,
    typeUtgift = typeUtgift,
    opprettet = opprettet
)

fun ReisetilskuddSoknadDbRecord.tilReisetilskuddsoknad(
    kvitteringer: List<Kvittering>,
    sporsmal: List<Sporsmal>
): ReisetilskuddSoknad = ReisetilskuddSoknad(
    id = this.id,
    status = this.status,
    sykmeldingId = this.sykmeldingId,
    fnr = this.fnr,
    fom = this.fom,
    tom = this.tom,
    opprettet = this.opprettet,
    endret = this.endret,
    sendt = this.sendt,
    avbrutt = this.avbrutt,
    arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
    arbeidsgiverNavn = this.arbeidsgiverNavn,
    kvitteringer = kvitteringer,
    sporsmal = sporsmal
)

fun Sporsmal.tilSporsmalDbRecord(
    reisetilskuddSoknadId: String? = null,
    oversporsmalId: String? = null,
): SporsmalDbRecord {
    return SporsmalDbRecord(
        id = this.id,
        reisetilskuddSoknadId = reisetilskuddSoknadId,
        oversporsmalId = oversporsmalId,
        tag = this.tag,
        overskrift = this.overskrift,
        sporsmalstekst = this.sporsmalstekst,
        undertekst = this.undertekst,
        svartype = this.svartype,
        min = this.min,
        max = this.max,
        kriterieForVisningAvUndersporsmal = this.kriterieForVisningAvUndersporsmal
    )
}
