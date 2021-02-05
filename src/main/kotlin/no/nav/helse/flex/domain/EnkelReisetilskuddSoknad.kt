package no.nav.helse.flex.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("reisetilskudd_soknad")
data class EnkelReisetilskuddSoknad(
    @Id
    val id: String,
    val status: ReisetilskuddStatus,
    val fnr: String,
    val endret: Instant,
    val sendt: Instant? = null,
    val avbrutt: Instant? = null,
)

fun ReisetilskuddSoknad.tilEnkel(): EnkelReisetilskuddSoknad {
    val id = this.id ?: throw IllegalStateException("ID skal være satt")
    return EnkelReisetilskuddSoknad(
        id = id,
        status = this.status,
        fnr = this.fnr,
        endret = this.endret,
        sendt = this.sendt,
        avbrutt = this.avbrutt,
    )
}
