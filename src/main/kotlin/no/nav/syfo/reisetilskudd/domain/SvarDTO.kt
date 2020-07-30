package no.nav.syfo.reisetilskudd.domain

data class SvarDTO(
    val reisetilskuddId: String,
    val utbetalingTilArbeidsgiver: Boolean?,
    val går: Boolean?,
    val sykler: Boolean?,
    val egenBil: Double?,
    val kollektivtransport: Double?
)