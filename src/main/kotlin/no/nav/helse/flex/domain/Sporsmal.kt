package no.nav.helse.flex.domain

data class Sporsmal(
    val id: String,
    val tag: Tag,
    val overskrift: String? = null,
    val sporsmalstekst: String? = null,
    val undertekst: String? = null,
    val svartype: Svartype,
    val min: String? = null,
    val max: String? = null,
    val kriterieForVisningAvUndersporsmal: KriterieForVisningAvUndersporsmal? = null,
    val svar: List<Svar> = emptyList(),
    val undersporsmal: List<Sporsmal> = emptyList()
)
