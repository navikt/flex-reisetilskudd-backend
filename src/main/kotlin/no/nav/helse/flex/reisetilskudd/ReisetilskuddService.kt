package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.toReisetilskuddDTO
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.* // ktlint-disable no-wildcard-imports
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ReisetilskuddService(
    private val database: DatabaseInterface,
    private val aivenKafkaConfig: AivenKafkaConfig
) {
    private lateinit var kafkaProducer: KafkaProducer<String, Reisetilskudd>

    fun behandleSykmelding(melding: SykmeldingMessage) {
        melding.toReisetilskuddDTO().forEach { reisetilskudd ->
            try {
                lagreReisetilskudd(reisetilskudd)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        reisetilskudd.reisetilskuddId,
                        reisetilskudd
                    )
                ).get()
                log.info("Opprettet reisetilskudd ${reisetilskudd.reisetilskuddId}")
            } catch (e: Exception) {
                log.warn("Feiler på reisetilskudd ${reisetilskudd.reisetilskuddId}", e)
                throw e
            }
        }
    }

    fun settOppKafkaProducer() {
        kafkaProducer = aivenKafkaConfig.producer()
    }

    fun lukkProducer() {
        kafkaProducer.close()
    }

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

    fun hentReisetilskudd(fnr: String, reisetilskuddId: String) =
        database.hentReisetilskudd(fnr, reisetilskuddId)

    private fun lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
        database.lagreReisetilskudd(reisetilskudd)
    }

    fun oppdaterReisetilskudd(reisetilskudd: Reisetilskudd) {
        database.oppdaterReisetilskudd(reisetilskudd)
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String) =
        database.sendReisetilskudd(fnr, reisetilskuddId)

    fun lagreKvittering(kvittering: Kvittering) {
        database.lagreKvittering(kvittering)
    }

    fun eierReisetilskudd(fnr: String, id: String) =
        database.eierReisetilskudd(fnr, id)

    fun eierKvittering(fnr: String, id: String) =
        database.eierKvittering(fnr, id)

    fun slettKvittering(id: String) =
        database.slettKvittering(id)
}
