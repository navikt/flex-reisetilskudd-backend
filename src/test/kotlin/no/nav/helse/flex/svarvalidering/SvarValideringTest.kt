package no.nav.helse.flex.svarvalidering

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.soknadsoppsett.offentligTransportBeløpSpørsmål
import org.amshove.kluent.*
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
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

        spm.copy(svar = listOf(Svar(verdi = "1.0"))) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil svarverdi 1.0"
        spm.copy(svar = listOf(Svar(verdi = "-1"))) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har svarverdi utenfor grenseverdi -1"
        spm.copy(svar = listOf(Svar(verdi = "0"))).validerSvarPaSporsmal()
        spm.copy(svar = listOf(Svar(verdi = "1"))).validerSvarPaSporsmal()
    }

    @Test
    fun `test reiseMedBilSpørsmål`() {
        val spm = offentligTransportBeløpSpørsmål()

        spm `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil antall svar 0"

        spm.copy(svar = listOf(Svar(verdi = "1.0"))) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har feil svarverdi 1.0"
        spm.copy(svar = listOf(Svar(verdi = "-1"))) `valider svar og forvent feilmelding` "Spørsmål ${spm.id} med tag OFFENTLIG_TRANSPORT_BELOP har svarverdi utenfor grenseverdi -1"
        spm.copy(svar = listOf(Svar(verdi = "0"))).validerSvarPaSporsmal()
        spm.copy(svar = listOf(Svar(verdi = "1"))).validerSvarPaSporsmal()
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
