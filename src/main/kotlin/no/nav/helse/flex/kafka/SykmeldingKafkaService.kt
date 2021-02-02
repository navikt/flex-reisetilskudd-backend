package no.nav.helse.flex.kafka

import kotlinx.coroutines.delay
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.lang.Exception
import java.time.Duration

class SykmeldingKafkaService(
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingMessage?>,
    private val applicationState: ApplicationState,
    private val reisetilskuddService: ReisetilskuddService,
    private val delayStart: Long = 10_000L,
    private val environment: Environment
) {
    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error("Uhåndtert feil i SykmeldingKafkaService, restarter om $delayStart ms", ex)
                kafkaConsumer.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private fun run() {
        log.info("Starter SykmeldingKafkaService")

        kafkaConsumer.subscribe(listOf("syfo-sendt-sykmelding", "syfo-bekreftet-sykmelding"))

        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach {
                val sykmeldingMessage = it.value()

                if (sykmeldingMessage == null) {
                    log.info("Mottok tombstone på topic ${it.topic()} med key ${it.key()}")
                } else if (sykmeldingMessage.ikkeInneholderReisetilskudd()) {
                    log.info("Mottok sykmelding som vi ikke bryr oss om: ${sykmeldingMessage.sykmelding.id}")
                } else if (sykmeldingMessage.ikkeAllePerioderErReisetilskudd()) {
                    log.info("Mottok sykmelding der ikke alle perioder er reisetilskudd: ${sykmeldingMessage.sykmelding.id}")
                } else if (sykmeldingMessage.inneholderGradertPeriode()) {
                    log.info("Mottok sykmelding med gradert periode: ${sykmeldingMessage.sykmelding.id}")
                } else if (sykmeldingMessage.mismatchAvTypeOgReisetilskuddFlagg()) {
                    log.warn("Mottok sykmelding der reisetilskudd flagg ikke stemmer overens med type: ${sykmeldingMessage.sykmelding.id}")
                } else if (sykmeldingMessage.erIkkeArbeidstaker()) {
                    log.info("Mottok sykmelding med reisetilskudd hvor arbeidssituasjon er ${sykmeldingMessage.hentArbeidssituasjon()}: ${sykmeldingMessage.sykmelding.id}")
                } else if (sykmeldingMessage.erDefinitivtReisetilskudd()) {
                    if (environment.cluster == "prod-gcp") {
                        log.info("Mottok sykmelding som vi bryr oss om ${sykmeldingMessage.sykmelding.id}, men oppretter ikke siden vi ikke er live i prod")
                    } else {
                        log.info("Mottok sykmelding som vi bryr oss om ${sykmeldingMessage.sykmelding.id}")
                        reisetilskuddService.behandleSykmelding(sykmeldingMessage)
                    }
                } else {
                    log.warn("Mottok sykmelding ${sykmeldingMessage.sykmelding.id} med udefinert utfall, skal ikke skje!")
                }
            }
        }
    }

    private fun SykmeldingMessage.ikkeInneholderReisetilskudd(): Boolean {
        return this.sykmelding.sykmeldingsperioder.none { periode -> periode.reisetilskudd }
    }

    private fun SykmeldingMessage.ikkeAllePerioderErReisetilskudd(): Boolean {
        return !this.sykmelding.sykmeldingsperioder.all { periode -> periode.reisetilskudd }
    }

    private fun SykmeldingMessage.inneholderGradertPeriode(): Boolean {
        return this.sykmelding.sykmeldingsperioder.any { periode ->
            periode.gradert != null && periode.gradert?.grad != 100
        }
    }

    private fun SykmeldingMessage.mismatchAvTypeOgReisetilskuddFlagg(): Boolean {
        return this.sykmelding.sykmeldingsperioder.any { periode ->
            (periode.reisetilskudd && periode.type != PeriodetypeDTO.REISETILSKUDD) ||
                (!periode.reisetilskudd && periode.type == PeriodetypeDTO.REISETILSKUDD)
        }
    }

    private fun SykmeldingMessage.erDefinitivtReisetilskudd(): Boolean {
        return this.sykmelding.sykmeldingsperioder.all { periode ->
            periode.reisetilskudd && periode.type == PeriodetypeDTO.REISETILSKUDD
        }
    }

    private fun SykmeldingMessage.erIkkeArbeidstaker(): Boolean {
        return !this.erArbeidstaker()
    }

    private fun SykmeldingMessage.erArbeidstaker(): Boolean {
        return this.hentArbeidssituasjon() == Arbeidssituasjon.ARBEIDSTAKER
    }

    private fun SykmeldingMessage.hentArbeidssituasjon(): Arbeidssituasjon? {
        this.event.sporsmals?.firstOrNull { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.let { return Arbeidssituasjon.valueOf(it) }
        return null
    }
}
