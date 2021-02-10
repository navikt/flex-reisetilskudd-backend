package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.soknadsoppsett.skapReisetilskuddsoknad
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class ReisetilskuddService(
    private val enkelReisetilskuddSoknadRepository: EnkelReisetilskuddSoknadRepository,
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>,
    private val metrikk: Metrikk,
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao
) {
    private val log = logger()
    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        val navn = "Navn Navnesen" // TODO hent navn fra PDL
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

    fun sendReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        val avbrutt = reisetilskuddSoknad.tilEnkel().copy(sendt = Instant.now(), status = ReisetilskuddStatus.SENDT)
        enkelReisetilskuddSoknadRepository.save(avbrutt)
        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)
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
        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)
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

        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)

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

    fun hentReisetilskuddene(fnr: String): List<ReisetilskuddSoknad> {
        return reisetilskuddSoknadDao.finnMedFnr(fnr)
    }

    fun hentReisetilskudd(id: String): ReisetilskuddSoknad? {
        return reisetilskuddSoknadDao.finnSoknad(id)
    }
}
