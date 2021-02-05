package no.nav.helse.flex.domain

import org.springframework.data.annotation.Id
import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class Kvittering(
    @Id
    val id: String? = null,
    val blobId: String,
    val datoForUtgift: LocalDate,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Transportmiddel
)
