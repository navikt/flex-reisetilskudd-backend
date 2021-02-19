package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.kafka.reisetilskuddTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class ReisetilskuddKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, ReisetilskuddSoknad>

) {
    fun produserer(reisetilskuddSoknad: ReisetilskuddSoknad) {
        kafkaProducer.send(
            ProducerRecord(
                reisetilskuddTopic,
                reisetilskuddSoknad.id,
                reisetilskuddSoknad
            )
        ).get()
    }
}
