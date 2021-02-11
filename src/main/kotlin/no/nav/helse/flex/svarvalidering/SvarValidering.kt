package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.controller.AbstractApiError
import no.nav.helse.flex.controller.LogLevel.WARN
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype.*
import org.springframework.http.HttpStatus.BAD_REQUEST
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

fun ReisetilskuddSoknad.validerSvarPaSoknad() {
    sporsmal.forEach { it.validerSvarPaSporsmal() }
}

fun Sporsmal.validerSvarPaSporsmal() {
    validerAntallSvar()
    validerSvarverdier()
    validerKunUnikeSvar()
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

private fun String?.tilLocalDate(): LocalDate? {
    if (this == null) {
        return null
    }
    return LocalDate.parse(this)
}

private fun String?.tilBigDescimal(): BigDecimal? {
    if (this == null) {
        return null
    }
    return this.toBigDecimal()
}

private fun Sporsmal.validerGrenserPaDato(svar: Svar): () -> Boolean {
    val dato = LocalDate.parse(svar.verdi)
    val minDato = this.min.tilLocalDate()
    val maxDato = this.max.tilLocalDate()
    var validerer = true

    if (minDato != null && minDato.isAfter(dato)) {
        validerer = false
    }

    if (maxDato != null && maxDato.isBefore(dato)) {
        validerer = false
    }
    return { validerer }
}

private fun Sporsmal.validerGrenserPaaTall(svar: Svar): () -> Boolean {
    val verdi = svar.verdi.tilBigDescimal() ?: throw IllegalStateException("Verdi er allerede validert her")
    val min = this.min.tilBigDescimal()
    val max = this.max.tilBigDescimal()
    var validerer = true
    if (min != null && verdi < min) {
        validerer = false
    }
    if (max != null && verdi > max) {
        validerer = false
    }
    return { validerer }
}

private fun Sporsmal.validerGrenserPaSvar(svar: Svar) {

    val predikat: () -> Boolean = when (svartype) {
        JA_NEI, CHECKBOX, CHECKBOX_GRUPPE, KVITTERING -> {
            { true }
        }
        DATOER -> validerGrenserPaDato(svar)
        BELOP -> validerGrenserPaaTall(svar)
        KILOMETER -> validerGrenserPaaTall(svar)
    }
    if (!predikat()) {
        throw ValideringException("Spørsmål $id med tag $tag har svarverdi utenfor grenseverdi ${svar.verdi}")
    }
}

private fun Sporsmal.validerGrenserPaSvar() {
    svar.forEach { this.validerGrenserPaSvar(it) }
}

private fun validerKvittering(kvittering: Kvittering?): () -> Boolean {
    return { kvittering != null && kvittering.blobId.erUUID() && kvittering.belop >= 0 }
}

fun String.erUUID(): Boolean {
    return try {
        UUID.fromString(this)
        this.length == 36
    } catch (e: Exception) {
        false
    }
}

fun String?.erHeltall(): Boolean {
    val verdi = this ?: return false
    return try {
        verdi.toInt()
        true
    } catch (e: Exception) {
        false
    }
}

fun String?.erDato(): Boolean {
    val verdi = this ?: return false
    return try {
        LocalDate.parse(verdi)
        true
    } catch (e: Exception) {
        false
    }
}

fun String?.erDoubleMedMaxEnDesimal(): Boolean {
    val verdi = this ?: return false
    return try {
        verdi.toBigDecimal()
        if (verdi.contains(".")) {
            return verdi.split(".")[1].length == 1
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }
}

private fun Sporsmal.validerSvarverdi(svar: Svar) {
    val verdi = svar.verdi
    val predikat: () -> Boolean = when (svartype) {
        JA_NEI -> {
            { "JA" == verdi || "NEI" == verdi }
        }
        CHECKBOX -> {
            { "CHECKED" == verdi }
        }
        DATOER -> {
            { verdi.erDato() }
        }
        BELOP -> {
            { verdi.erHeltall() }
        }
        KILOMETER -> {
            { verdi.erDoubleMedMaxEnDesimal() }
        }
        KVITTERING -> validerKvittering(svar.kvittering)
        CHECKBOX_GRUPPE -> throw IllegalStateException("Skal ha validert 0 svar allerede")
    }
    if (!predikat()) {
        throw ValideringException("Spørsmål $id med tag $tag har feil svarverdi $verdi")
    }
}

private fun Sporsmal.validerSvarverdier() {
    svar.forEach { this.validerSvarverdi(it) }
}
private fun Sporsmal.validerKunUnikeSvar() {
    if (svar.size != svar.toSet().size) {
        throw ValideringException("Spørsmål $id med tag $tag har duplikate svar")
    }
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
        DATOER -> {
            { it > 0 }
        }
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
