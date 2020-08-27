package no.nav.syfo.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.reisetilskudd.ReisetilskuddService
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class SykmeldingKafkaService(
    val kafkaConsumer: KafkaConsumer<String, SykmeldingMessage>,
    val applicationState: ApplicationState,
    val reisetilskuddService: ReisetilskuddService
) {
    suspend fun run() {
        kafkaConsumer.subscribe(listOf("syfo-sendt-sykmelding", "syfo-bekreftet-sykmelding"))
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach {
                val sykmeldingMessage = it.value()
                if (sykmeldingMessage.sykmelding.sykmeldingsperioder.any { periode -> periode.reisetilskudd }) {
                    log.info("Mottok sykmelding som vi bryr oss om ${sykmeldingMessage.sykmelding.id}")
                    reisetilskuddService.behandleSykmelding(sykmeldingMessage)
                } else {
                    log.info("Mottok sykmelding som vi ikke bryr oss om: ${sykmeldingMessage.sykmelding.id}")
                }
            }
            delay(1)
        }
    }
}
