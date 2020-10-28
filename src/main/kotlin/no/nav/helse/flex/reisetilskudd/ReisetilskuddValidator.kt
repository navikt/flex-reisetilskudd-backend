package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.reisetilskudd.domain.KvitteringDTO

private const val MAX_VEDLEGG_SIZE = 8 * 1024 * 1024 // vedlegg på 8 MB

internal fun validerKvittering(kvittering: KvitteringDTO): Boolean {
    val validertOk = validerFilStorresle(kvittering)
    return if (validertOk) {
        true
    } else {
        throw RuntimeException("Filstørrelsen overstiger maksgrensen på $MAX_VEDLEGG_SIZE MB")
    }
}

private fun validerFilStorresle(kvittering: KvitteringDTO): Boolean {
    return if (kvittering.storrelse > MAX_VEDLEGG_SIZE) {
        throw RuntimeException("Filstørrelsen overstiger maksgrensen på $MAX_VEDLEGG_SIZE MB")
    } else {
        true
    }
}
