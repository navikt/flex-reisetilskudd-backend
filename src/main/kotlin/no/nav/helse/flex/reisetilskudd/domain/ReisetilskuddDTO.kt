package no.nav.helse.flex.reisetilskudd.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class ReisetilskuddDTO(
    val reisetilskuddId: String,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sendt: LocalDateTime? = null,
    val orgNummer: String?,
    val orgNavn: String?,
    var utbetalingTilArbeidsgiver: Boolean? = null,
    var går: Boolean? = null,
    var sykler: Boolean? = null,
    var egenBil: Double = 0.0,
    var kollektivtransport: Double = 0.0,
    var kvitteringer: List<KvitteringDTO> = emptyList()
) {
    override fun toString() =
        """
       ReisetilskuddDTO(
        reisetilskuddId = $reisetilskuddId,
        sykmeldingId = $sykmeldingId,
        fnr = $fnr,
        fom = $fom,
        tom = $tom,
        orgNummer = $orgNummer,
        orgNavn = $orgNavn,
        utbetalingTilArbeidsgiver = $utbetalingTilArbeidsgiver,
        går = $går,
        sykler = $sykler,
        egenBil = $egenBil,
        kollektivtransport = $kollektivtransport
        
       ) 
    """
}

fun Boolean?.toInt(): Int {
    return when {
        this == true -> 1
        this == false -> 2
        else -> 0
    }
}

fun Int.toOptionalBoolean(): Boolean? {
    return when {
        this == 1 -> true
        this == 2 -> false
        else -> null
    }
}
