package no.nav.helse.flex.domain

import java.time.Instant
import java.time.LocalDate

data class ReisetilskuddSoknad(
    val status: ReisetilskuddStatus,
    val id: String,
    val sykmeldingId: String,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: Instant,
    val endret: Instant,
    val sendt: Instant? = null,
    val avbrutt: Instant? = null,
    val orgNummer: String?,
    val orgNavn: String?,
    val kvitteringer: List<Kvittering> = emptyList()
)

enum class ReisetilskuddStatus {
    FREMTIDIG, ÅPEN, SENDBAR, SENDT, AVBRUTT
}
