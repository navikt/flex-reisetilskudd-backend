package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.controller.AbstractApiError
import no.nav.helse.flex.controller.LogLevel.WARN
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype.*
import org.springframework.http.HttpStatus.BAD_REQUEST

fun ReisetilskuddSoknad.validerSvarPaSoknad() {
    sporsmal.forEach { it.validerSvarPaSporsmal() }
}

fun Sporsmal.validerSvarPaSporsmal() {
    validerAntallSvar()
    validerSvarverdier()
    validerGrenserPaSvar()
    validerUndersporsmal()
}

private fun Sporsmal.validerUndersporsmal() {

    fun validerUnderspørsmålHvisDeSkalVises() {
        if (svar.size == 1 && kriterieForVisningAvUndersporsmal != null) {
            if (svar.first().verdi == kriterieForVisningAvUndersporsmal.name) {
                undersporsmal.forEach { it.validerSvarPaSporsmal() }
            }
        }
    }
    return when (svartype) {
        CHECKBOX_GRUPPE -> {
            val besvarteUndersporsmal = undersporsmal.filter { it.svar.isNotEmpty() }
            if (besvarteUndersporsmal.isEmpty()) {
                throw ValideringException("Spørsmål ${this.id} av typen $svartype må ha minst ett besvart underspørsmål")
            } else {
                besvarteUndersporsmal.forEach { it.validerSvarPaSporsmal() }
            }
        }
        JA_NEI -> validerUnderspørsmålHvisDeSkalVises()
        CHECKBOX -> validerUnderspørsmålHvisDeSkalVises()
        DATOER -> validerUnderspørsmålHvisDeSkalVises()
        BELOP -> validerUnderspørsmålHvisDeSkalVises()
        KILOMETER -> validerUnderspørsmålHvisDeSkalVises()
        KVITTERING -> validerUnderspørsmålHvisDeSkalVises()
    }
}

private fun Sporsmal.validerGrenserPaSvar() {
    /*
    return if (sporsmal.min == null && sporsmal.max == null) {
        true
    } else when (sporsmal.svartype) {
        else -> {
            log.error("Har ikke implementert validering av grenser for svartype: " + sporsmal.svartype)
            false
        }
    }
    */
}

private fun Sporsmal.validerSvarverdier() {
    /*
    return when (sporsmal.svartype) {
        Svartype.JA_NEI -> "JA" == verdi || "NEI" == verdi
        Svartype.CHECKBOX, Svartype.CHECKBOX_GRUPPE -> verdi == null
        else -> {
            log.error("Har ikke implementert validering av svartype: " + sporsmal.svartype)
            false
        }
    }

     */
}

private fun Sporsmal.validerAntallSvar() {
    val predikat: (Int) -> Boolean = when (this.svartype) {
        JA_NEI,
        BELOP,
        KILOMETER,
        CHECKBOX -> {
            { it == 1 }
        }
        CHECKBOX_GRUPPE -> {
            { it == 0 }
        }
        DATOER,
        KVITTERING -> {
            { it >= 0 }
        }
    }
    if (!predikat(svar.size)) {
        throw ValideringException("Spørsmål $id med tag $tag har feil antall svar ${svar.size}")
    }
}

class ValideringException(message: String) : AbstractApiError(
    message = message,
    httpStatus = BAD_REQUEST,
    reason = "SPORSMALETS_SVAR_VALIDERER_IKKE",
    loglevel = WARN
)
