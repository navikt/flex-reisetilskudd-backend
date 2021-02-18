package no.nav.helse.flex.client.syketilfelle

data class OppfolgingstilfelleDTO(
    val antallBrukteDager: Int,
    val oppbruktArbeidsgvierperiode: Boolean,
    val arbeidsgiverperiode: PeriodeDTO?
)
