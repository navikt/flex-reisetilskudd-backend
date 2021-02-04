package no.nav.helse.flex.domain

data class Svar(
    val utbetalingTilArbeidsgiver: Boolean? = null,
    val går: Boolean? = null,
    val sykler: Boolean? = null,
    val egenBil: Double? = null,
    val kollektivtransport: Double? = null,
)
