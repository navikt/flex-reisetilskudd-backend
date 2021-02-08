package no.nav.helse.flex.domain

import java.time.Instant
import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class Kvittering(
    val id: String,
    val blobId: String,
    val datoForUtgift: LocalDate,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Transportmiddel,
    val opprettet: Instant? = null,
)
