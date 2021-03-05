package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykmeldingListener(
    private val sykmeldingKafkaService: SykmeldingKafkaService
) {
    val log = logger()

    @KafkaListener(
        topics = ["syfo-sendt-sykmelding", "syfo-bekreftet-sykmelding"],
        id = "sykmelding-sendt-bekreftet",
        idIsGroup = false,
        containerFactory = "sykmeldingKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, SykmeldingMessage?>, acknowledgment: Acknowledgment) {
        val melding = cr.value()

        try {

            sykmeldingKafkaService.prosseser(sykmeldingMessage = cr.value(), topic = cr.topic(), key = cr.key())

            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Uventet feil ved mottak av sykmelding: ${melding?.sykmelding?.id} på topic ${cr.topic()}", e)
            throw e
        }
    }
}
