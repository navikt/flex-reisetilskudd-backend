package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.domain.ReisetilskuddStatus
import java.time.LocalDate

fun reisetilskuddStatus(fom: LocalDate, tom: LocalDate): ReisetilskuddStatus {
    val now = LocalDate.now()
    if (fom.isAfter(now)) {
        return ReisetilskuddStatus.FREMTIDIG
    }
    if (now.isAfter(tom)) {
        return ReisetilskuddStatus.SENDBAR
    }
    return ReisetilskuddStatus.ÅPEN
}
