package no.nav.syfo.reisetilskudd.domain

import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class KvitteringDTO(
    val reisetilskuddId: String,
    val kvitteringId: String,
    val navn: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val storrelse: Long,
    val belop: Double,
    val transportmiddel: Transportmiddel
)

/*
Forventet payload fra frontend:

{
    "reisetilskuddId": "en-uuid-her",
    "kvitteringId": "en-uuid-til",
    "navn": "foo.jpg",
    "fom": "2020-07-22",
    "tom": null,
    "storrelse": 988987123,
    "belop": 180.40,
    "transportmiddel": "TAXI",
}

tom blir satt kun dersom det er periode
 */
