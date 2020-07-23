package no.nav.syfo.domain

import java.time.LocalDate

data class ReisetilskuddDTO(
    val reisetilskuddId: String,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val orgNummer: String?,
    val orgNavn: String?
)
