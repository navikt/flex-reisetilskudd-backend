package no.nav.helse.flex.kafka

import no.nav.helse.flex.client.pdl.AKTORID
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.syketilfelle.SyketilfelleClient
import no.nav.helse.flex.environment.NaisEnvironment
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.reisetilskudd.OpprettReisetilskuddSoknaderService
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import org.springframework.stereotype.Component

@Component
class SykmeldingKafkaService(
    private val naisEnvironment: NaisEnvironment,
    private val reisetilskuddService: OpprettReisetilskuddSoknaderService,
    private val pdlClient: PdlClient,
    private val syketilfelleClient: SyketilfelleClient,
    private val metrikk: Metrikk,
) {
    val log = logger()

    fun run(sykmeldingMessage: SykmeldingMessage?, topic: String, key: String) {
        metrikk.mottattSykmelding.increment()
        if (sykmeldingMessage == null) {
            log.info("Mottok tombstone på topic $topic med key $key")
            metrikk.tombstoneSykmelding.increment()
            return
        }
        if (sykmeldingMessage.ikkeInneholdeReisetilskudd()) {
            log.info("Mottok sykmelding som vi ikke bryr oss om: ${sykmeldingMessage.sykmelding.id}")
            metrikk.sykmeldingIkkeReisetilskudd.increment()
            return
        }
        if (sykmeldingMessage.ikkeAllePerioderErReisetilskudd()) {
            log.info("Mottok sykmelding der ikke alle perioder er reisetilskudd: ${sykmeldingMessage.sykmelding.id}")
            metrikk.sykmeldingAllePerioderIkkeReisetilskudd.increment()
            return
        }
        if (sykmeldingMessage.inneholderGradertPeriode()) {
            log.info("Mottok sykmelding med gradert periode: ${sykmeldingMessage.sykmelding.id}")
            metrikk.sykmeldingGradertPeriode.increment()
            return
        }
        if (sykmeldingMessage.mismatchAvTypeOgReisetilskuddFlagg()) {
            log.warn("Mottok sykmelding der reisetilskudd flagg ikke stemmer overens med type: ${sykmeldingMessage.sykmelding.id}")
            metrikk.sykmeldingMismatchAvTypeOgReisetilskuddFlagg.increment()
            return
        }
        if (sykmeldingMessage.erIkkeArbeidstaker() && !sykmeldingMessage.erAnnetOgDev()) {
            log.info("Mottok sykmelding med reisetilskudd hvor arbeidssituasjon er ${sykmeldingMessage.hentArbeidssituasjon()}: ${sykmeldingMessage.sykmelding.id}")
            return
        }
        if (sykmeldingMessage.erDefinitivtReisetilskudd()) {

            val person = pdlClient.hentPerson(sykmeldingMessage.kafkaMetadata.fnr)
            val aktorId = person.hentIdenter?.identer?.find { it.gruppe == AKTORID }?.ident
                ?: throw RuntimeException("Fant ikke aktorId for sykmelding ${sykmeldingMessage.sykmelding.id}")
            val oppfolgingstilfelle = syketilfelleClient.beregnOppfolgingstilfelle(sykmeldingMessage, aktorId)

            log.info("Mottok sykmelding som vi bryr oss om ${sykmeldingMessage.sykmelding.id}")
            reisetilskuddService.behandleSykmelding(
                sykmeldingMessage = sykmeldingMessage,
                person = person,
                oppfolgingstilfelle = oppfolgingstilfelle,
                ignorerArbeidsgiverPeriode = sykmeldingMessage.erAnnetOgDev()
            )

            return
        }
        log.warn("Mottok sykmelding ${sykmeldingMessage.sykmelding.id} med udefinert utfall, skal ikke skje!")

    }

    private fun SykmeldingMessage.ikkeInneholdeReisetilskudd(): Boolean {
        return this.sykmelding.sykmeldingsperioder.none { periode -> periode.reisetilskudd }
    }

    private fun SykmeldingMessage.ikkeAllePerioderErReisetilskudd(): Boolean {
        return !this.sykmelding.sykmeldingsperioder.all { periode -> periode.reisetilskudd }
    }

    private fun SykmeldingMessage.inneholderGradertPeriode(): Boolean {
        return this.sykmelding.sykmeldingsperioder.any { periode ->
            periode.gradert != null && periode.gradert?.grad != 100
        }
    }

    private fun SykmeldingMessage.mismatchAvTypeOgReisetilskuddFlagg(): Boolean {
        return this.sykmelding.sykmeldingsperioder.any { periode ->
            (periode.reisetilskudd && periode.type != PeriodetypeDTO.REISETILSKUDD) ||
                (!periode.reisetilskudd && periode.type == PeriodetypeDTO.REISETILSKUDD)
        }
    }

    private fun SykmeldingMessage.erDefinitivtReisetilskudd(): Boolean {
        return this.sykmelding.sykmeldingsperioder.all { periode ->
            periode.reisetilskudd && periode.type == PeriodetypeDTO.REISETILSKUDD
        }
    }

    private fun SykmeldingMessage.erAnnetOgDev(): Boolean {
        return this.hentArbeidssituasjon() == Arbeidssituasjon.ANNET && naisEnvironment.erDevEllerDockerCompose()
    }

    private fun SykmeldingMessage.erIkkeArbeidstaker(): Boolean {
        return !this.erArbeidstaker()
    }

    private fun SykmeldingMessage.erArbeidstaker(): Boolean {
        return this.hentArbeidssituasjon() == Arbeidssituasjon.ARBEIDSTAKER
    }

    private fun SykmeldingMessage.hentArbeidssituasjon(): Arbeidssituasjon? {
        this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.let {
            return Arbeidssituasjon.valueOf(
                it
            )
        }
        return null
    }
}
