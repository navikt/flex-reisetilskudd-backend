package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.EnkelReisetilskuddSoknadRepository
import no.nav.helse.flex.db.ReisetilskuddSoknadRepository
import no.nav.helse.flex.db.getById
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AktiverService(
    private val reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository,
    private val enkelReisetilskuddSoknadRepository: EnkelReisetilskuddSoknadRepository,
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>,
    private val metrikk: Metrikk
) {
    val log = logger()

    fun åpneReisetilskudd(now: LocalDate = LocalDate.now()): Int {
        log.info("Leter etter reisetilskudd som skal bli ÅPNE")

        val reisetilskuddSomSkalÅpnes = enkelReisetilskuddSoknadRepository.finnReisetilskuddSomSkalÅpnes(now)
        log.info("Fant ${reisetilskuddSomSkalÅpnes.size} reisetilskudd som skal bli ÅPNE")

        var i = 0
        reisetilskuddSomSkalÅpnes.forEach { reisetilskudd ->
            val id = reisetilskudd.id
            try {
                enkelReisetilskuddSoknadRepository.save(reisetilskudd.copy(status = ReisetilskuddStatus.ÅPEN))
                i++
                val oppdatertReisetilskudd = reisetilskuddSoknadRepository.getById(id)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        id,
                        oppdatertReisetilskudd
                    )
                ).get()
                metrikk.ÅPNE_REISETILSKUDD.increment()
            } catch (e: Exception) {
                log.error("Feilet ved aktivering av åpnet reisetilskudd med id $id", e)
            }
        }

        log.info("$i reisetilskudd ble ÅPNE")
        return i
    }

    fun sendbareReisetilskudd(now: LocalDate = LocalDate.now()): Int {
        log.info("Leter etter reisetilskudd som skal bli SENDBAR")

        val reisetilskuddSomSkalBliSendbar = enkelReisetilskuddSoknadRepository.finnReisetilskuddSomSkalBliSendbar(now)
        log.info("Fant ${reisetilskuddSomSkalBliSendbar.size} reisetilskudd som skal bli SENDBAR")

        var i = 0
        reisetilskuddSomSkalBliSendbar.forEach { reisetilskudd ->
            val id = reisetilskudd.id

            try {
                enkelReisetilskuddSoknadRepository.save(reisetilskudd.copy(status = ReisetilskuddStatus.SENDBAR))
                i++
                val oppdatertReisetilskudd = reisetilskuddSoknadRepository.getById(id)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        id,
                        oppdatertReisetilskudd
                    )
                ).get()
                metrikk.SENDBARE_REISETILSKUDD.increment()
            } catch (e: Exception) {
                log.error("Feilet ved aktivering av sendbart reisetilskudd med id $id", e)
            }
        }

        log.info("$i reisetilskudd ble SENDBAR")
        return i
    }
}
