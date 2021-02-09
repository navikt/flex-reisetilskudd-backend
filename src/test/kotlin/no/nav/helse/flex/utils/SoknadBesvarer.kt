package no.nav.helse.flex.utils

import no.nav.helse.flex.domain.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.test.web.servlet.MockMvc

private fun copyRsByttSvar(
    rsSykepengesoknad: ReisetilskuddSoknad,
    rsSporsmal: Sporsmal,
    rsSvar: List<Svar>
): ReisetilskuddSoknad {
    var alleSporsmal = rsSykepengesoknad.sporsmal
    alleSporsmal = byttSvar(alleSporsmal, rsSporsmal, rsSvar)
    return rsSykepengesoknad.copy(sporsmal = alleSporsmal)
}

private fun ReisetilskuddSoknad.byttSvar(rsSporsmal: Sporsmal, rsSvar: List<Svar>): ReisetilskuddSoknad {
    return copyRsByttSvar(this, rsSporsmal, rsSvar)
}

private fun byttSvar(alleSporsmal: List<Sporsmal>, rsSporsmal: Sporsmal, rsSvar: List<Svar>): List<Sporsmal> {
    return alleSporsmal.map { spm ->
        if (spm.tag == rsSporsmal.tag) rsSporsmal.copy(svar = rsSvar)
        else if (spm.undersporsmal.isNotEmpty()) spm.copy(
            undersporsmal = byttSvar(
                spm.undersporsmal,
                rsSporsmal,
                rsSvar
            )
        )
        else spm
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
        val sporsmal = reisetilskuddSoknad.sporsmal.flatten().find { it.tag == tag }
            ?: throw RuntimeException("Spørsmål ikke funnet $tag")
        val rsSvar = svarListe.map { Svar(verdi = it) }
        val oppdatertSoknad = reisetilskuddSoknad.byttSvar(sporsmal, rsSvar)
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
