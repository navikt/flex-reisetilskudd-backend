package no.nav.helse.flex.db

import no.nav.helse.flex.controller.SoknadIkkeFunnetException
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.Sporsmal
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
    val kvitteringRepository: KvitteringRepository,
) {

    fun hentSoknad(id: String): ReisetilskuddSoknad {
        return finnSoknad(id) ?: throw SoknadIkkeFunnetException()
    }

    fun finnMedFnr(fnr: String): List<ReisetilskuddSoknad> {
        return reisetilskuddSoknadRepository.findReisetilskuddSoknadByFnr(fnr).map { it.hentUnderliggende() }
    }

    private fun SporsmalDbRecord.hentUnderliggende(): Sporsmal {
        val undersporsmal = sporsmalRepository.findSporsmalByOversporsmalId(this.id).map { it.hentUnderliggende() }
        val svar = svarRepository.findSvarBySporsmalId(this.id).map { it.tilSvar() }
        return this.tilSporsmal(undersporsmal, svar)
    }

    private fun ReisetilskuddSoknadDbRecord.hentUnderliggende(): ReisetilskuddSoknad {
        val sporsmal = sporsmalRepository.findSporsmalByReisetilskuddSoknadId(this.id).map { it.hentUnderliggende() }
        val kvitteringer =
            kvitteringRepository.findKvitteringDbRecordByReisetilskuddSoknadId(this.id).map { it.tilKvittering() }
        return this.tilReisetilskuddsoknad(sporsmal = sporsmal, kvitteringer = kvitteringer)
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
            lagreUndersporsmal(it, sporsmal.id)
        }
    }

    private fun lagreUndersporsmal(sporsmal: Sporsmal, oversporsmalId: String) {
        jdbcAggregateTemplate.insert(sporsmal.tilSporsmalDbRecord(oversporsmalId = oversporsmalId))
        sporsmal.undersporsmal.forEach {
            lagreUndersporsmal(it, sporsmal.id)
        }
    }

    fun lagreKvittering(reisetilskuddSoknadId: String, kvittering: Kvittering): Kvittering {
        return jdbcAggregateTemplate.insert(kvittering.tilKvitteringDbRecord(reisetilskuddSoknadId)).tilKvittering()
    }

    fun slettKvitteringMedId(kvitteringId: String) {
        kvitteringRepository.deleteById(kvitteringId)
    }
}
