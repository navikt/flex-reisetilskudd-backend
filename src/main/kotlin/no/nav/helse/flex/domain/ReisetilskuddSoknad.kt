package no.nav.helse.flex.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import java.time.Instant
import java.time.LocalDate

data class ReisetilskuddSoknad(
    @Id
    val id: String? = null,
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
    @MappedCollection(keyColumn = "REISETILSKUDD_SOKNAD_ID")
    val kvitteringer: List<Kvittering> = emptyList()
)

enum class ReisetilskuddStatus {
    FREMTIDIG, ÅPEN, SENDBAR, SENDT, AVBRUTT
}
