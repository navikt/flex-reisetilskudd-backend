package no.nav.helse.flex.domain

import java.util.*

data class Sporsmal(
    val id: String = UUID.randomUUID().toString(),
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

fun List<Sporsmal>.flatten(): List<Sporsmal> =
    flatMap {
        mutableListOf(it).apply {
            addAll(it.undersporsmal.flatten())
        }
    }
