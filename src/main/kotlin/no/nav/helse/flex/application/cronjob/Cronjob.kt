package no.nav.helse.flex.application.cronjob

import io.ktor.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.helse.flex.Environment
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import java.lang.Exception
import java.time.*

@KtorExperimentalAPI
class Cronjob(
    private val applicationState: ApplicationState,
    private val env: Environment,
    private val database: DatabaseInterface,
    private val aivenKafkaConfig: AivenKafkaConfig,
    private val podLeaderCoordinator: PodLeaderCoordinator
) {
    suspend fun start() = coroutineScope {
        val (initialDelay, interval) = hentKjøretider(env)

        log.info("Schedulerer cronjob start: $initialDelay ms, interval: $interval ms")
        delay(initialDelay)

        while (applicationState.alive) {
            val job = launch { run() }
            delay(interval)
            if (job.isActive) {
                log.warn("Cronjob er ikke ferdig, venter til den er ferdig")
                job.join()
            }
        }

        log.info("Avslutter cronjob")
    }

    internal fun run() {
        try {
            if (podLeaderCoordinator.isLeader() && env.cluster != "prod-gcp") {
                log.info("Kjører reisetilskudd cronjob")
                val kafkaProducer = aivenKafkaConfig.producer()
                val aktiverService = AktiverService(database, kafkaProducer)

                aktiverService.åpneReisetilskudd()
                aktiverService.sendbareReisetilskudd()

                kafkaProducer.close()
            }
        } catch (ex: Exception) {
            log.error("Feil i cronjob, kjøres på nytt neste gang", ex)
        }
    }

    private fun hentKjøretider(env: Environment): Pair<Long, Long> {
        val osloTz = ZoneId.of("Europe/Oslo")
        val now = ZonedDateTime.now(osloTz)
        if (env.cluster == "dev-gcp" || env.cluster == "flex") {
            val omEtMinutt = Duration.ofMinutes(1).toMillis()
            val femMinutter = Duration.ofMinutes(5).toMillis()
            return Pair(omEtMinutt, femMinutter)
        }
        if (env.cluster == "prod-gcp") {
            val nesteNatt = now.next(LocalTime.of(2, 0, 0)) - now.toInstant().toEpochMilli()
            val enDag = Duration.ofDays(1).toMillis()
            return Pair(nesteNatt, enDag)
        }
        throw IllegalStateException("Ukjent cluster name for cronjob ${env.cluster}")
    }

    private fun ZonedDateTime.next(atTime: LocalTime): Long {
        return if (this.toLocalTime().isAfter(atTime)) {
            this.plusDays(1).withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant().toEpochMilli()
        } else {
            this.withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant().toEpochMilli()
        }
    }
}
