package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.Svartype.CHECKBOX_GRUPPE
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.soknadsoppsett.offentligTransportBeløpSpørsmål
import no.nav.helse.flex.soknadsoppsett.reiseMedBilSpørsmål
import no.nav.helse.flex.soknadsoppsett.transportTilDagligSpørsmål
import no.nav.helse.flex.utils.byttSvar
import org.amshove.kluent.*
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class SvarValideringTest {

    @Test
    fun `test er double med max en desimal`() {
        "34.2".`er double med max en desimal`().`should be true`()
        "34".`er double med max en desimal`().`should be true`()
        "34.0".`er double med max en desimal`().`should be true`()
        "-34.0".`er double med max en desimal`().`should be true`()

        "34.".`er double med max en desimal`().`should be false`()
        "34,2".`er double med max en desimal`().`should be false`()
        "34.20".`er double med max en desimal`().`should be false`()
        "aergaerg".`er double med max en desimal`().`should be false`()
        "".`er double med max en desimal`().`should be false`()
        null.`er double med max en desimal`().`should be false`()
    }

    @Test
    fun `test er heltall`() {
        "3".`er heltall`().`should be true`()
        "-3".`er heltall`().`should be true`()
        "0".`er heltall`().`should be true`()
        "000".`er heltall`().`should be true`()
        "34".`er heltall`().`should be true`()

        "34.0".`er heltall`().`should be false`()
        "3.0".`er heltall`().`should be false`()
        "34,2".`er heltall`().`should be false`()
        "aergaerg".`er heltall`().`should be false`()
        "".`er heltall`().`should be false`()
        null.`er heltall`().`should be false`()
    }

    @Test
    fun `test er dato`() {
        "2020-03-12".`er dato`().`should be true`()

        "2020-02-30".`er dato`().`should be false`()
        "2020-3-12".`er dato`().`should be false`()
        "34.0".`er dato`().`should be false`()
        "aergaerg".`er dato`().`should be false`()
        "".`er dato`().`should be false`()
        null.`er dato`().`should be false`()
    }

    @Test
    fun `test er uuid`() {
        "d4ee3021-4021-45c4-aa82-4af38bd99505".`er uuid`().`should be true`()
        UUID.randomUUID().toString().`er uuid`().`should be true`()

        "d4ee3021-4021-45c4-aa82-4af38bd99505a".`er uuid`().`should be false`()
        "d4ee3021-4021-45c4-aa82-4af38bd9950".`er uuid`().`should be false`()
        "d4ee3021402145c4aa824af38bd99505".`er uuid`().`should be false`()
    }

    @Test
    fun `test validering av beløp grenseverdi`() {
        "d4ee3021-4021-45c4-aa82-4af38bd99505".`er uuid`().`should be true`()
        UUID.randomUUID().toString().`er uuid`().`should be true`()

        "d4ee3021-4021-45c4-aa82-4af38bd99505a".`er uuid`().`should be false`()
        "d4ee3021-4021-45c4-aa82-4af38bd9950".`er uuid`().`should be false`()
        "d4ee3021402145c4aa824af38bd99505".`er uuid`().`should be false`()
    }

    @Test
    fun `test validering av beløp spørsmål`() {
        val spm = offentligTransportBeløpSpørsmål()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil antall svar 0"

        spm.byttSvar(svar = "1.0") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil svarverdi 1.0"
        spm.byttSvar(svar = "-1") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har svarverdi utenfor grenseverdi -1"
        spm.byttSvar(svar = "0").validerSvarPaSporsmal()
        spm.byttSvar(svar = "1").validerSvarPaSporsmal()
    }

    @Test
    fun `test reiseMedBilSpørsmål`() {
        val spm =
            reiseMedBilSpørsmål(fom = LocalDate.now(), tom = LocalDate.now().plusDays(2), formattertPeriode = " okok")

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag REISE_MED_BIL har feil antall svar 0"

        spm.byttSvar(svar = "NEI").validerSvarPaSporsmal()
        spm.byttSvar(svar = "KANSKJE") `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $REISE_MED_BIL har feil svarverdi KANSKJE"

        spm.byttSvar(svar = "JA") `valider svar og forvent feilmelding` "Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har feil antall svar 0"

        val igår = LocalDate.now().minusDays(1).toString()

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = igår)
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har svarverdi utenfor grenseverdi $igår")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = LocalDate.now().toString())
            .validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_DATOER, svar = listOf(LocalDate.now().toString(), LocalDate.now().toString()))
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(BIL_DATOER)} med tag $BIL_DATOER har duplikate svar")
    }

    @Test
    fun `test transport til daglig spørsmål`() {
        val spm = transportTilDagligSpørsmål()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag $TRANSPORT_TIL_DAGLIG har feil antall svar 0"

        spm.byttSvar(svar = "NEI")
            .validerSvarPaSporsmal()

        spm.byttSvar(svar = "JA")
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(TYPE_TRANSPORT)} av typen $CHECKBOX_GRUPPE må ha minst ett besvart underspørsmål")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_TIL_DAGLIG, svar = "CHECKED")
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(KM_HJEM_JOBB)} med tag $KM_HJEM_JOBB har feil antall svar 0")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_TIL_DAGLIG, svar = "CHECKED")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.45")
            .`valider svar og forvent feilmelding`("Spørsmål ${spm.idForTag(KM_HJEM_JOBB)} med tag $KM_HJEM_JOBB har feil svarverdi 4.45")

        spm.byttSvar(svar = "JA")
            .byttSvar(tag = BIL_TIL_DAGLIG, svar = "CHECKED")
            .byttSvar(tag = KM_HJEM_JOBB, svar = "4.4")
            .validerSvarPaSporsmal()
    }

    private fun Sporsmal.idForTag(tag: Tag): String {
        return listOf(this).flatten().first { it.tag == tag }.id
    }

    private fun String?.`er double med max en desimal`() = this.erDoubleMedMaxEnDesimal()
    private fun String?.`er heltall`() = this.erHeltall()
    private fun String?.`er dato`() = this.erDato()
    private fun String.`er uuid`() = this.erUUID()
    private infix fun Sporsmal.`valider svar og forvent feilmelding`(s: String) {
        try {
            validerSvarPaSporsmal()
            fail("Forventer exeption")
        } catch (e: ValideringException) {
            e.message shouldBeEqualTo s
        }
    }
}
