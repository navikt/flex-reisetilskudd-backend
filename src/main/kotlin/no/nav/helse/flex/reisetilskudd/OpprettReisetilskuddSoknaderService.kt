package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.client.pdl.ResponseData
import no.nav.helse.flex.client.pdl.format
import no.nav.helse.flex.client.syketilfelle.OppfolgingstilfelleDTO
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.kafka.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsoppsett.skapReisetilskuddsoknad
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
@Transactional
class OpprettReisetilskuddSoknaderService(
    private val kafkaProducer: ReisetilskuddKafkaProducer,
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao,
) {
    private val log = logger()

    fun behandleSykmelding(
        sykmeldingMessage: SykmeldingMessage,
        person: ResponseData,
        oppfolgingstilfelle: OppfolgingstilfelleDTO?,
        ignorerArbeidsgiverPeriode: Boolean = false
    ) {
        val navn = person.hentPerson?.navn?.firstOrNull()?.format()
            ?: throw RuntimeException("Fant ikke navn for sykmelding ${sykmeldingMessage.sykmelding.id}")

        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .map { periode ->
                    skapReisetilskuddsoknad(periode, sykmeldingMessage, navn)
                }
                .filter { reisetilskudd ->
                    if (oppfolgingstilfelle == null) {
                        log.info("Mottok sykmelding med reisetilskudd der det ikke finnes oppfolgingstilfelle ${sykmeldingMessage.sykmelding.id}")
                        return@filter false
                    }

                    val arbeidsgiverperiodeTom = oppfolgingstilfelle.arbeidsgiverperiode!!.tom
                    val oppbruktArbeidsgiverperiode = oppfolgingstilfelle.oppbruktArbeidsgvierperiode

                    if (ignorerArbeidsgiverPeriode) {
                        return@filter true
                    }
                    if (!oppbruktArbeidsgiverperiode || reisetilskudd.tom.isBeforeOrEqual(arbeidsgiverperiodeTom)) {
                        log.info("Reisetilskudd fra ${reisetilskudd.fom} til ${reisetilskudd.tom} er innenfor arbeidsgiverperioden med sykmelding ${sykmeldingMessage.sykmelding.id}")
                        return@filter false
                    }
                    return@filter true
                }
                .forEach { reisetilskudd ->
                    reisetilskuddSoknadDao.lagreSoknad(reisetilskudd)
                    kafkaProducer.send(reisetilskudd)
                    log.info("Opprettet reisetilskudd ${reisetilskudd.id}")
                }
        }.onSuccess {
            log.info("Sykmelding ${sykmeldingMessage.sykmelding.id} ferdig behandlet")
        }.onFailure { ex ->
            log.error("Uhåndtert feil ved behandleSykmelding ${sykmeldingMessage.sykmelding.id}", ex)
            throw ex
        }
    }

    private fun LocalDate.isBeforeOrEqual(other: LocalDate): Boolean {
        return this == other || this.isBefore(other)
    }
}
