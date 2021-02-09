package no.nav.helse.flex.domain

import java.time.Instant
import java.time.LocalDate
import java.util.*

data class ReisetilskuddSoknad(
    val id: String = UUID.randomUUID().toString(),
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
    val kvitteringer: List<Kvittering> = emptyList(),
    val sporsmal: List<Sporsmal> = emptyList()
)

enum class ReisetilskuddStatus {
    FREMTIDIG, ÅPEN, SENDBAR, SENDT, AVBRUTT
}

