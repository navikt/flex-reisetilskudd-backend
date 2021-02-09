package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class BesvarSporsmalService(
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao
) {

    fun oppdaterSporsmal(soknadFraBasenFørOppdatering: ReisetilskuddSoknad, sporsmal: Sporsmal): ReisetilskuddSoknad {
        val sporsmalId = sporsmal.id
        val soknadId = soknadFraBasenFørOppdatering.id
        if (!soknadFraBasenFørOppdatering.sporsmal.flatten().map { it.id }.contains(sporsmalId)) {
            throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
        }

        if (!soknadFraBasenFørOppdatering.sporsmal.map { it.id }.contains(sporsmalId)) {
            throw IllegalArgumentException("$sporsmalId er ikke et hovedspørsmål i søknad $soknadId")
        }

        fun List<Sporsmal>.erUlikUtenomSvar(sammenlign: List<Sporsmal>): Boolean {
            fun List<Sporsmal>.flattenOgFjernSvar(): List<Sporsmal> {
                return this.flatten().map { it.copy(svar = emptyList()) }.sortedBy { it.id }
            }

            return this.flattenOgFjernSvar() != sammenlign.flattenOgFjernSvar()
        }

        val sporsmaletFraBasen = soknadFraBasenFørOppdatering.sporsmal.find { it.id == sporsmal.id }
            ?: throw IllegalArgumentException("Soknad fra basen skal ha spørsmålet")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Spørsmål i databasen er ulikt spørsmål som er besvart")
        }

        reisetilskuddSoknadDao.lagreSvar(sporsmal)
        return reisetilskuddSoknadDao.hentSoknad(soknadId)
    }
}
