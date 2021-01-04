package no.nav.helse.flex.reisetilskudd.util

import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import java.time.LocalDate

fun reisetilskuddStatus(tom: LocalDate): ReisetilskuddStatus {
    val now = LocalDate.now()
    return if (tom.isAfter(now)) ReisetilskuddStatus.FREMTIDIG
    else ReisetilskuddStatus.ÅPEN
}
