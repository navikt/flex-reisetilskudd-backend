package no.nav.helse.flex.soknadsoppsett

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

object DatoFormaterer {
    var sporsmalstekstFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun mnd(dato: LocalDate): String {
        return dato.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("nb-NO"))
    }

    fun formatterPeriode(fom: LocalDate, tom: LocalDate): String {
        return fom.dayOfMonth.toString() + "." +
            (if (fom.month == tom.month) "" else " " + mnd(fom)) +
            (if (fom.year == tom.year) "" else " " + fom.year) +
            " - " + formatterDato(tom)
    }

    fun formatterDato(dato: LocalDate): String {
        return dato.dayOfMonth.toString() + ". " +
            mnd(dato) + " " +
            dato.year
    }
}
