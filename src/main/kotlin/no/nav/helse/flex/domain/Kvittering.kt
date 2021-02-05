package no.nav.helse.flex.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import java.time.Instant
import java.time.LocalDate

enum class Transportmiddel {
    KOLLEKTIVT, TAXI, EGEN_BIL
}

data class Kvittering(
    @Id
    val id: String? = null,
    @JsonIgnore
    val reisetilskuddSoknadId: String? = null,
    val blobId: String,
    val datoForUtgift: LocalDate,
    val belop: Int, // Beløp i øre . 100kr = 10000
    val typeUtgift: Transportmiddel,
    @JsonIgnore
    val opprettet: Instant? = null,
)
