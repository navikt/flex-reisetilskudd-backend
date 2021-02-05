package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Component
@Transactional
class ReisetilskuddService(
    private val database: Database,
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>,
    private val metrikk: Metrikk,

    ) {
    private val log = logger()
    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .mapIndexed { idx, periode ->
                    ReisetilskuddSoknad(
                        id = UUID.nameUUIDFromBytes("${sykmeldingMessage.sykmelding.id}-${periode.fom}-${periode.tom}".toByteArray())
                            .toString(),
                        sykmeldingId = sykmeldingMessage.sykmelding.id,
                        status = reisetilskuddStatus(periode.fom, periode.tom),
                        oppfølgende = idx > 0,
                        fnr = sykmeldingMessage.kafkaMetadata.fnr,
                        fom = periode.fom,
                        tom = periode.tom,
                        orgNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
                        orgNummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer,
                        opprettet = Instant.now()
                    )
                }
                .forEach { reisetilskudd ->
                    lagreReisetilskudd(reisetilskudd)
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

    fun hentReisetilskuddene(fnr: String) =
        database.hentReisetilskuddene(fnr)

    fun hentReisetilskudd(reisetilskuddId: String) =
        database.finnReisetilskudd(reisetilskuddId)

    private fun lagreReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad) {
        database.lagreReisetilskudd(reisetilskuddSoknad)
    }

    fun oppdaterReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        return database.oppdaterReisetilskudd(reisetilskuddSoknad)
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.sendReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.SENDT_REISETILSKUDD.increment()
        log.info("Sendte reisetilskudd ${reisetilskudd.id}")
    }

    fun avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.avbrytReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.AVBRUTT_REISETILSKUDD.increment()

        log.info("Avbrøt reisetilskudd ${reisetilskudd.id}")
    }

    fun gjenapneReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.gjenapneReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.id,
                reisetilskudd
            )
        ).get()
        metrikk.GJENÅPNET_REISETILSKUDD.increment()
        log.info("Gjenåpnet reisetilskudd ${reisetilskudd.id}")
    }

    fun lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
        return database.lagreKvittering(kvittering, reisetilskuddId)
    }

    fun slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
        return database.slettKvittering(kvitteringId, reisetilskuddId)
    }
}
