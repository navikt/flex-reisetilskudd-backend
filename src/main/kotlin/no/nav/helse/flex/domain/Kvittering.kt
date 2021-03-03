package no.nav.helse.flex.domain

import java.time.Instant
import java.time.LocalDate

enum class Utgiftstype {
    OFFENTLIG_TRANSPORT, TAXI, PARKERING, ANNET
}

data class Kvittering(
    val blobId: String,
    val datoForUtgift: LocalDate,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Utgiftstype,
    val opprettet: Instant? = null,
)
