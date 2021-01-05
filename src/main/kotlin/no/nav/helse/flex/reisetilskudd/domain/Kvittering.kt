package no.nav.helse.flex.reisetilskudd.domain

import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class Kvittering(
    val kvitteringId: String,
    val navn: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val storrelse: Long,
    val belop: Double,
    val transportmiddel: Transportmiddel
)
