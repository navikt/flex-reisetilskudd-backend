package no.nav.helse.flex.kafka

import kotlinx.coroutines.delay
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.lang.Exception
import java.time.Duration

class SykmeldingKafkaService(
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingMessage?>,
    private val applicationState: ApplicationState,
    private val reisetilskuddService: ReisetilskuddService,
    private val delayStart: Long = 10_000L
) {
    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error("Uhåndtert feil i SykmeldingKafkaService, restarter om $delayStart ms", ex)
                kafkaConsumer.unsubscribe()
                reisetilskuddService.lukkProducer()
            }
            delay(delayStart)
        }
    }

    private fun run() {
        log.info("Starter SykmeldingKafkaService")

        kafkaConsumer.subscribe(listOf("syfo-sendt-sykmelding", "syfo-bekreftet-sykmelding"))
        reisetilskuddService.settOppKafkaProducer()

        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach {
                val sykmeldingMessage = it.value()
                if (sykmeldingMessage == null) {
                    log.info("Mottok tombstone på topic ${it.topic()} med key ${it.key()}")
                } else if (sykmeldingMessage.sykmelding.sykmeldingsperioder.any { periode -> periode.reisetilskudd }) {
                    log.info("Mottok sykmelding som vi bryr oss om ${sykmeldingMessage.sykmelding.id}")
                    reisetilskuddService.behandleSykmelding(sykmeldingMessage)
                } else {
                    log.info("Mottok sykmelding som vi ikke bryr oss om: ${sykmeldingMessage.sykmelding.id}")
                }
            }
        }
    }
}
