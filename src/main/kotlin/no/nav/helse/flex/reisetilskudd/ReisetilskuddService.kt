package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.tilEnkel
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class ReisetilskuddService(
    private val reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository,
    private val enkelReisetilskuddSoknadRepository: EnkelReisetilskuddSoknadRepository,
    private val kvitteringRepository: KvitteringRepository,
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>,
    private val metrikk: Metrikk,

) {
    private val log = logger()
    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .map { periode ->
                    ReisetilskuddSoknad(
                        sykmeldingId = sykmeldingMessage.sykmelding.id,
                        status = reisetilskuddStatus(periode.fom, periode.tom),
                        fnr = sykmeldingMessage.kafkaMetadata.fnr,
                        fom = periode.fom,
                        tom = periode.tom,
                        arbeidsgiverNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
                        arbeidsgiverOrgnummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer,
                        opprettet = Instant.now(),
                        endret = Instant.now()
                    )
                }
                .forEach { reisetilskudd ->
                    reisetilskuddSoknadRepository.save(reisetilskudd)
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

    fun sendReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        val avbrutt = reisetilskuddSoknad.tilEnkel().copy(sendt = Instant.now(), status = ReisetilskuddStatus.SENDT)
        enkelReisetilskuddSoknadRepository.save(avbrutt)
        val reisetilskudd = reisetilskuddSoknadRepository.getById(reisetilskuddSoknad.id!!)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.SENDT_REISETILSKUDD.increment()
        log.info("Sendte reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun avbrytReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        val avbrutt = reisetilskuddSoknad.tilEnkel().copy(avbrutt = Instant.now(), status = ReisetilskuddStatus.AVBRUTT)
        enkelReisetilskuddSoknadRepository.save(avbrutt)
        val reisetilskudd = reisetilskuddSoknadRepository.getById(reisetilskuddSoknad.id!!)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.AVBRUTT_REISETILSKUDD.increment()

        log.info("Avbrøt reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun gjenapneReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        val status = reisetilskuddStatus(reisetilskuddSoknad.fom, reisetilskuddSoknad.tom)

        val gjenåpnet = reisetilskuddSoknad.tilEnkel().copy(avbrutt = null, status = status)
        enkelReisetilskuddSoknadRepository.save(gjenåpnet)

        val reisetilskudd = reisetilskuddSoknadRepository.getById(reisetilskuddSoknad.id!!)

        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.GJENÅPNET_REISETILSKUDD.increment()
        log.info("Gjenåpnet reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun lagreKvittering(kvittering: Kvittering): Kvittering {
        return kvitteringRepository.save(kvittering)
    }

    fun slettKvittering(kvitteringId: String, soknad: ReisetilskuddSoknad) {
        if (soknad.kvitteringer.map { it.id }.contains(kvitteringId)) {
            kvitteringRepository.deleteById(kvitteringId)
        }
    }

    fun hentReisetilskuddene(fnr: String): List<ReisetilskuddSoknad> {
        return reisetilskuddSoknadRepository.findReisetilskuddSoknadByFnr(fnr)
    }

    fun hentReisetilskudd(id: String): ReisetilskuddSoknad? {
        return reisetilskuddSoknadRepository.findByIdOrNull(id)
    }
}
