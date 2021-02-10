package no.nav.helse.flex.db

import no.nav.helse.flex.controller.SoknadIkkeFunnetException
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.flatten
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class ReisetilskuddSoknadDao(
    val jdbcAggregateTemplate: JdbcAggregateTemplate,
    val reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository,
    val sporsmalRepository: SporsmalRepository,
    val svarRepository: SvarRepository,
) {

    fun hentSoknad(id: String): ReisetilskuddSoknad {
        return finnSoknad(id) ?: throw SoknadIkkeFunnetException()
    }

    fun finnMedFnr(fnr: String): List<ReisetilskuddSoknad> {
        return reisetilskuddSoknadRepository.findReisetilskuddSoknadByFnr(fnr).map { it.hentUnderliggende() }
    }

    fun Set<Sporsmal>.flatten(): List<Sporsmal> =
        this.toList().flatMap {
            mutableListOf(it).apply {
                addAll(it.undersporsmal.toSet().flatten())
            }
        }

    private fun List<SporsmalDbRecord>.hentUnderliggendeOgSkapGraf(): List<Sporsmal> {
        val svar = svarRepository.findSvarDbRecordsBySporsmalIdIn(this.map { it.id })
        val listAvAlleSpm = HashSet<SporsmalDbRecord>(this)

        val hovedsporsmal = mutableSetOf<Sporsmal>()

        while (listAvAlleSpm.isNotEmpty()) {
            if (hovedsporsmal.isEmpty()) {
                // Første runde
                val hovedsporsmalDbRecord = listAvAlleSpm.filter { it.oversporsmalId == null }
                listAvAlleSpm.removeAll(hovedsporsmalDbRecord)
                hovedsporsmalDbRecord
                    .map { spm ->
                        spm.tilSporsmal(
                            undersporsmal = ArrayList(),
                            svar = svar.filter { it.sporsmalId == spm.id }
                                .map { it.tilSvar(spm.svartype == Svartype.KVITTERING) }
                        )
                    }.forEach { hovedsporsmal.add(it) }
            } else {
                val toRemove = mutableSetOf<SporsmalDbRecord>()
                listAvAlleSpm.forEach { spm ->
                    val flatListe = hovedsporsmal.flatten()
                    val find = flatListe.find { it.id == spm.oversporsmalId }
                    find?.let {
                        toRemove.add(spm)
                        (find.undersporsmal as ArrayList).add(
                            spm.tilSporsmal(
                                undersporsmal = ArrayList(),
                                svar = svar.filter { it.sporsmalId == spm.id }
                                    .map { it.tilSvar(spm.svartype == Svartype.KVITTERING) }
                            )
                        ).also { sortedBy { it.tag } }
                    }
                }
                listAvAlleSpm.removeAll(toRemove)
            }
        }

        return hovedsporsmal.toList().sortedBy { it.tag }
    }

    private fun ReisetilskuddSoknadDbRecord.hentUnderliggende(): ReisetilskuddSoknad {
        val sporsmal = sporsmalRepository.findSporsmalByReisetilskuddSoknadId(this.id).sortedBy { it.tag }
            .hentUnderliggendeOgSkapGraf()
        return this.tilReisetilskuddsoknad(sporsmal = sporsmal)
    }

    fun finnSoknad(id: String): ReisetilskuddSoknad? {
        return reisetilskuddSoknadRepository.findByIdOrNull(id)?.hentUnderliggende()
    }

    fun lagreSoknad(reisetilskuddSoknad: ReisetilskuddSoknad) {
        jdbcAggregateTemplate.insert(reisetilskuddSoknad.tilReisetilskuddSoknadDbRecord())
        reisetilskuddSoknad.sporsmal.forEach {
            lagreHovedsporsmal(sporsmal = it, reisetilskuddSoknadId = reisetilskuddSoknad.id)
        }
    }

    private fun lagreHovedsporsmal(sporsmal: Sporsmal, reisetilskuddSoknadId: String) {
        jdbcAggregateTemplate.insert(sporsmal.tilSporsmalDbRecord(reisetilskuddSoknadId = reisetilskuddSoknadId))
        sporsmal.undersporsmal.forEach {
            lagreUndersporsmal(it, reisetilskuddSoknadId, sporsmal.id)
        }
    }

    private fun lagreUndersporsmal(sporsmal: Sporsmal, reisetilskuddSoknadId: String, oversporsmalId: String) {
        jdbcAggregateTemplate.insert(
            sporsmal.tilSporsmalDbRecord(
                oversporsmalId = oversporsmalId,
                reisetilskuddSoknadId = reisetilskuddSoknadId
            )
        )
        sporsmal.undersporsmal.forEach {
            lagreUndersporsmal(it, reisetilskuddSoknadId, sporsmal.id)
        }
    }

    fun lagreSvar(sporsmal: Sporsmal) {
        val alleSporsmal = listOf(sporsmal).flatten()
        svarRepository.deleteSvarDbRecordByIdIn(alleSporsmal.map { it.id })
        alleSporsmal.forEach { spm ->
            spm.svar.forEach { svar ->
                jdbcAggregateTemplate.insert(svar.tilSvarDbRecord(sporsmalId = spm.id))
            }
        }
    }

    fun slettSvar(svarId: String) {
        svarRepository.deleteById(svarId)
    }
}
