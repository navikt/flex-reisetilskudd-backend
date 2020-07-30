package no.nav.syfo.reisetilskudd.domain

import java.time.LocalDate

data class ReisetilskuddDTO(
    val reisetilskuddId: String,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val orgNummer: String?,
    val orgNavn: String?,
    var utbetalingTilArbeidsgiver: Boolean? = null,
    var går: Boolean? = null,
    var sykler: Boolean? = null,
    var egenBil: Double? = null,
    var kollektivtransport: Double? = null
)
