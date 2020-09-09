package no.nav.helse.flex.reisetilskudd.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Reisetilskudd(
    val status: ReisetilskuddStatus,
    val reisetilskuddId: String,
    val sykmeldingId: String,
    val oppfølgende: Boolean,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sendt: LocalDateTime? = null,
    val orgNummer: String?,
    val orgNavn: String?,
    val utbetalingTilArbeidsgiver: Boolean? = null,
    val går: Boolean? = null,
    val sykler: Boolean? = null,
    val egenBil: Double = 0.0,
    val kollektivtransport: Double = 0.0,
    val kvitteringer: List<Kvittering> = emptyList()
)

enum class ReisetilskuddStatus {
    FREMTIDIG, ÅPEN, SENDT
}
