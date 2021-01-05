package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.toReisetilskuddDTO
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.*
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
        log.info("Gjenåpnet reisetilskudd ${reisetilskudd.reisetilskuddId}")
    }

    fun lagreKvittering(kvittering: Kvittering, reisetilskuddId: String) {
        database.lagreKvittering(kvittering, reisetilskuddId)
    }

    fun slettKvittering(kvitteringId: String, reisetilskuddId: String) =
        database.slettKvittering(kvitteringId, reisetilskuddId)
}
