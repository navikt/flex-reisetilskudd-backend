package no.nav.helse.flex.application.cronjob

import no.nav.helse.flex.application.metrics.SENDBARE_REISETILSKUDD
import no.nav.helse.flex.application.metrics.ÅPNE_REISETILSKUDD
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.transactions.Transactions
import no.nav.helse.flex.reisetilskudd.transactions.finnReisetilskuddSomSkalBliSendbar
import no.nav.helse.flex.reisetilskudd.transactions.finnReisetilskuddSomSkalÅpnes
import no.nav.helse.flex.reisetilskudd.transactions.hentReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.sendbarReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.åpneReisetilskudd
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate

class AktiverService(
    database: DatabaseInterface,
    aivenKafkaConfig: AivenKafkaConfig
) : Transactions(
    database,
    aivenKafkaConfig
) {
    fun åpneReisetilskudd(now: LocalDate = LocalDate.now()) {
        log.info("Leter etter reisetilskudd som skal bli ÅPNE")

        transaction {
            val reisetilskuddSomSkalÅpnes = this.finnReisetilskuddSomSkalÅpnes(now)
            log.info("Fant ${reisetilskuddSomSkalÅpnes.size} reisetilskudd som skal bli ÅPNE")

            var i = 0
            reisetilskuddSomSkalÅpnes.forEach { id ->
                try {
                    this.åpneReisetilskudd(id)
                    i++
                    val reisetilskudd = this.hentReisetilskudd(id)
                    kafkaProducer.send(
                        ProducerRecord(
                            AivenKafkaConfig.topic,
                            id,
                            reisetilskudd
                        )
                    ).get()
                    ÅPNE_REISETILSKUDD.inc()
                } catch (e: Exception) {
                    log.error("Feilet ved aktivering av åpnet reisetilskudd med id $id", e)
                }
            }
            log.info("$i reisetilskudd ble ÅPNE")
        }
    }

    fun sendbareReisetilskudd(now: LocalDate = LocalDate.now()) {
        log.info("Leter etter reisetilskudd som skal bli SENDBAR")

        transaction {
            val reisetilskuddSomSkalBliSendbar = this.finnReisetilskuddSomSkalBliSendbar(now)
            log.info("Fant ${reisetilskuddSomSkalBliSendbar.size} reisetilskudd som skal bli SENDBAR")

            var i = 0
            reisetilskuddSomSkalBliSendbar.forEach { id ->
                try {
                    this.sendbarReisetilskudd(id)
                    i++
                    val reisetilskudd = this.hentReisetilskudd(id)
                    kafkaProducer.send(
                        ProducerRecord(
                            AivenKafkaConfig.topic,
                            id,
                            reisetilskudd
                        )
                    ).get()
                    SENDBARE_REISETILSKUDD.inc()
                } catch (e: Exception) {
                    log.error("Feilet ved aktivering av sendbart reisetilskudd med id $id", e)
                }
            }

            log.info("$i reisetilskudd ble SENDBAR")
        }
    }
}
