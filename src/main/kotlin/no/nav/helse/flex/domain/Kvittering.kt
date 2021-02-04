package no.nav.helse.flex.domain

import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class Kvittering(
    val kvitteringId: String? = null,
    val blobId: String,
    val navn: String,
    val datoForReise: LocalDate,
    val storrelse: Long,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val transportmiddel: Transportmiddel
)
