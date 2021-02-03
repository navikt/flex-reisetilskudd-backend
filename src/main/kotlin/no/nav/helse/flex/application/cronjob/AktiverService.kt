package no.nav.helse.flex.application.cronjob

import no.nav.helse.flex.application.DatabaseInterface
import no.nav.helse.flex.application.metrics.Metrikk
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.finnReisetilskuddSomSkalBliSendbar
import no.nav.helse.flex.reisetilskudd.db.finnReisetilskuddSomSkalÅpnes
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.sendbarReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.åpneReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AktiverService(
    private val database: DatabaseInterface,
    private val kafkaProducer: KafkaProducer<String, Reisetilskudd>,
    private val metrikk: Metrikk
) {
    val log = log()

    fun åpneReisetilskudd(now: LocalDate = LocalDate.now()): Int {
        log.info("Leter etter reisetilskudd som skal bli ÅPNE")

        val reisetilskuddSomSkalÅpnes = database.finnReisetilskuddSomSkalÅpnes(now)
        log.info("Fant ${reisetilskuddSomSkalÅpnes.size} reisetilskudd som skal bli ÅPNE")

        var i = 0
        reisetilskuddSomSkalÅpnes.forEach { id ->
            try {
                database.åpneReisetilskudd(id)
                i++
                val reisetilskudd = database.hentReisetilskudd(id)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        id,
                        reisetilskudd
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

        val reisetilskuddSomSkalBliSendbar = database.finnReisetilskuddSomSkalBliSendbar(now)
        log.info("Fant ${reisetilskuddSomSkalBliSendbar.size} reisetilskudd som skal bli SENDBAR")

        var i = 0
        reisetilskuddSomSkalBliSendbar.forEach { id ->
            try {
                database.sendbarReisetilskudd(id)
                i++
                val reisetilskudd = database.hentReisetilskudd(id)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        id,
                        reisetilskudd
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
