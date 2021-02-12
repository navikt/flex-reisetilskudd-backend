package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.client.pdl.ResponseData
import no.nav.helse.flex.client.pdl.format
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsoppsett.skapReisetilskuddsoknad
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class OpprettReisetilskuddSoknaderService(
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>,
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao
) {
    private val log = logger()

    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage, person: ResponseData) {
        val navn = person.hentPerson?.navn?.firstOrNull()?.format()
            ?: throw RuntimeException("Fant ikke navn for sykmelding ${sykmeldingMessage.sykmelding.id}")

        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .map { periode ->
                    skapReisetilskuddsoknad(periode, sykmeldingMessage, navn)
                }
                .forEach { reisetilskudd ->
                    reisetilskuddSoknadDao.lagreSoknad(reisetilskudd)
                    kafkaProducer.send(
                        ProducerRecord(
                            AivenKafkaConfig.topic,
                            reisetilskudd.id,
                            reisetilskudd
                        )
                    ).get()
                    log.info("Opprettet reisetilskudd ${reisetilskudd.id}")
                }
        }.onSuccess {
            log.info("Sykmelding ${sykmeldingMessage.sykmelding.id} ferdig behandlet")
        }.onFailure { ex ->
            log.error("Uhåndtert feil ved behandleSykmelding ${sykmeldingMessage.sykmelding.id}", ex)
            throw ex
        }
    }
}
