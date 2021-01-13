package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.application.metrics.AVBRUTT_REISETILSKUDD
import no.nav.helse.flex.application.metrics.GJENÅPNET_REISETILSKUDD
import no.nav.helse.flex.application.metrics.SENDT_REISETILSKUDD
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.*
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*

class ReisetilskuddService(
    private val database: DatabaseInterface,
    private val aivenKafkaConfig: AivenKafkaConfig
) {
    private lateinit var kafkaProducer: KafkaProducer<String, Reisetilskudd>

    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .mapIndexed { idx, periode ->
                    Reisetilskudd(
                        reisetilskuddId = UUID.nameUUIDFromBytes("${sykmeldingMessage.sykmelding.id}-${periode.fom}-${periode.tom}".toByteArray()).toString(),
                        sykmeldingId = sykmeldingMessage.sykmelding.id,
                        status = reisetilskuddStatus(periode.fom, periode.tom),
                        oppfølgende = idx > 0,
                        fnr = sykmeldingMessage.kafkaMetadata.fnr,
                        fom = periode.fom,
                        tom = periode.tom,
                        orgNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
                        orgNummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer
                    )
                }
                .forEach { reisetilskudd ->
                    // TODO: Transaksjons håndtering
                    lagreReisetilskudd(reisetilskudd)
                    kafkaProducer.send(
                        ProducerRecord(
                            AivenKafkaConfig.topic,
                            reisetilskudd.reisetilskuddId,
                            reisetilskudd
                        )
                    ).get()
                    log.info("Opprettet reisetilskudd ${reisetilskudd.reisetilskuddId}")
                }
        }.onSuccess {
            log.info("Sykmelding ${sykmeldingMessage.sykmelding.id} ferdig behandlet")
        }.onFailure { ex ->
            log.error("Uhåndtert feil ved behandleSykmelding ${sykmeldingMessage.sykmelding.id}", ex)
            throw ex
        }
    }

    fun settOppKafkaProducer() {
        kafkaProducer = aivenKafkaConfig.producer()
    }

    fun lukkProducer() {
        kafkaProducer.close()
    }

    fun hentReisetilskuddene(fnr: String) =
        database.hentReisetilskuddene(fnr)

    fun hentReisetilskudd(reisetilskuddId: String) =
        database.hentReisetilskudd(reisetilskuddId)

    private fun lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
        database.lagreReisetilskudd(reisetilskudd)
    }

    fun oppdaterReisetilskudd(reisetilskudd: Reisetilskudd): Reisetilskudd {
        return database.oppdaterReisetilskudd(reisetilskudd)
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.sendReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.reisetilskuddId,
                reisetilskudd
            )
        ).get()
        SENDT_REISETILSKUDD.inc()
        log.info("Sendte reisetilskudd ${reisetilskudd.reisetilskuddId}")
    }

    fun avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.avbrytReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.reisetilskuddId,
                reisetilskudd
            )
        ).get()
        AVBRUTT_REISETILSKUDD.inc()
        log.info("Avbrøt reisetilskudd ${reisetilskudd.reisetilskuddId}")
    }

    fun gjenapneReisetilskudd(fnr: String, reisetilskuddId: String) {
        val reisetilskudd = database.gjenapneReisetilskudd(fnr, reisetilskuddId)
        kafkaProducer.send(
            ProducerRecord(
                AivenKafkaConfig.topic,
                reisetilskudd.reisetilskuddId,
                reisetilskudd
            )
        ).get()
        GJENÅPNET_REISETILSKUDD.inc()
        log.info("Gjenåpnet reisetilskudd ${reisetilskudd.reisetilskuddId}")
    }

    fun lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
        return database.lagreKvittering(kvittering, reisetilskuddId)
    }

    fun slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
        return database.slettKvittering(kvitteringId, reisetilskuddId)
    }
}
