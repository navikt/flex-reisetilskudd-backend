package no.nav.helse.flex.utils

import no.nav.helse.flex.domain.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.test.web.servlet.MockMvc

private fun ReisetilskuddSoknad.byttSvar(tag: Tag, svar: List<Svar>): ReisetilskuddSoknad =
    copy(sporsmal = sporsmal.byttSvar(tag, svar))

fun Sporsmal.byttSvar(tag: Tag, svar: List<Svar>): Sporsmal = listOf(this).byttSvar(tag, svar).first()

private fun List<Sporsmal>.byttSvar(tag: Tag, svar: List<Svar>): List<Sporsmal> {
    return map { spm ->
        when {
            spm.tag == tag -> spm.copy(svar = svar)
            spm.undersporsmal.isNotEmpty() -> spm.copy(
                undersporsmal = spm.undersporsmal.byttSvar(
                    tag,
                    svar
                )
            )
            else -> spm
        }
    }
}

private fun Sporsmal.erSporsmalMedIdEllerHarUndersporsmalMedId(id: String): Boolean =
    this.id == id || this.undersporsmal.any { it.erSporsmalMedIdEllerHarUndersporsmalMedId(id) } || this.undersporsmal.any { it.id == id }

class SoknadBesvarer(
    var reisetilskuddSoknad: ReisetilskuddSoknad,
    override val mockMvc: MockMvc,
    override val server: MockOAuth2Server,
    private val fnr: String,
) : TestHelper {
    fun besvarSporsmal(tag: Tag, svar: String, ferdigBesvart: Boolean = true): SoknadBesvarer {
        return besvarSporsmal(tag, listOf(svar), ferdigBesvart)
    }

    fun besvarSporsmal(tag: Tag, svarListe: List<String>, ferdigBesvart: Boolean = true): SoknadBesvarer {
        val svar = svarListe.map { Svar(verdi = it) }
        val oppdatertSoknad = reisetilskuddSoknad.byttSvar(tag, svar)
        reisetilskuddSoknad = oppdatertSoknad
        return if (ferdigBesvart) {
            gaVidere(tag)
        } else {
            this
        }
    }

    fun gaVidere(tag: Tag): SoknadBesvarer {
        val hovedsporsmal = finnHovedsporsmal(tag)
        oppdaterSporsmal(fnr, reisetilskuddSoknad.id, hovedsporsmal)

        return this
    }

    fun finnHovedsporsmal(tag: Tag): Sporsmal {
        val sporsmal = reisetilskuddSoknad.sporsmal.flatten().find { it.tag == tag }
            ?: throw RuntimeException("Spørsmål ikke funnet $tag")
        return reisetilskuddSoknad.sporsmal.first { it.erSporsmalMedIdEllerHarUndersporsmalMedId(sporsmal.id) }
    }

    fun sendSoknad(): ReisetilskuddSoknad {
        return this.sendSøknad(fnr, this.reisetilskuddSoknad.id)
    }
}
