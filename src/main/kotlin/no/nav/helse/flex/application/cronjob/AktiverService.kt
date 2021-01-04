package no.nav.helse.flex.application.cronjob

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.aktiverReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.finnReisetilskuddSomSkalAktiveres
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate

class AktiverService(
    private val database: DatabaseInterface,
    private val kafkaProducer: KafkaProducer<String, Reisetilskudd>
) {
    fun aktiverReisetilskudd(now: LocalDate = LocalDate.now()): Int {
        log.info("Leter etter reisetilskudd som skal aktiveres")

        val reisetilskuddSomSkalAktiveres = database.finnReisetilskuddSomSkalAktiveres(now)
        log.info("Fant ${reisetilskuddSomSkalAktiveres.size} reisetilskudd som skal aktiveres")

        var i = 0
        reisetilskuddSomSkalAktiveres.forEach { id ->
            try {
                database.aktiverReisetilskudd(id)
                i++
                val reisetilskudd = database.hentReisetilskudd(id)
                kafkaProducer.send(
                    ProducerRecord(
                        AivenKafkaConfig.topic,
                        id,
                        reisetilskudd
                    )
                ).get()
                // TODO: Metrikk
            } catch (e: Exception) {
                log.error("Feilet ved aktivering av reisetilskudd med id $id", e)
            }
        }

        log.info("Aktivert $i reisetilskudd")
        return i
    }
}
