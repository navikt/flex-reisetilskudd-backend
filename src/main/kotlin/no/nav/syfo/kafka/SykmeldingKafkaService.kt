package no.nav.syfo.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class SykmeldingKafkaService (
    val kafkaConsumer: KafkaConsumer<String, EnkelSykmelding>,
    val env: Environment,
    val applicationState: ApplicationState
) {
    suspend fun run() {
        kafkaConsumer.subscribe(listOf(env.bekreftetSykmeldingTopic, env.sendtSykmeldingTopic))
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach {
                val sykmelding = it.value()
                log.info("Mottok sykmelding: ${sykmelding.id}")
            }
            delay(1)
        }
    }
}
