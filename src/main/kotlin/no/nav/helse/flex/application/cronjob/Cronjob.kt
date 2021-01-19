package no.nav.helse.flex.application.cronjob

import io.ktor.util.*
import no.nav.helse.flex.Environment
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import java.lang.System.gc
import java.time.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
class Cronjob(
    private val env: Environment,
    private val database: DatabaseInterface,
    private val aivenKafkaConfig: AivenKafkaConfig
) {
    private val kjøretider = hentKlokekslettOgPeriode(env)
    private val scheduler = Executors.newScheduledThreadPool(1)

    fun setUpCronJob() {
        log.info("Schedulerer cronjob start: ${kjøretider.first}, periode: ${kjøretider.second} ms")
        scheduler.scheduleAtFixedRate(
            TidsOppgave(env, database, aivenKafkaConfig),
            10L,
            kjøretider.second,
            TimeUnit.MILLISECONDS
        )
    }

    class TidsOppgave(
        private val env: Environment,
        private val database: DatabaseInterface,
        private val aivenKafkaConfig: AivenKafkaConfig
    ) : Runnable {
        override fun run() {
            val podLeaderCoordinator = PodLeaderCoordinator(env)

            if (podLeaderCoordinator.isLeader() && env.cluster != "prod-gcp") {
                log.info("Kjører reisetilskudd cronjob")
                val kafkaProducer = aivenKafkaConfig.producer()
                val aktiverService = AktiverService(database, kafkaProducer)

                aktiverService.åpneReisetilskudd()
                aktiverService.sendbareReisetilskudd()

                kafkaProducer.close()
            } else {
                log.info("Jeg er ikke leder")
            }

            gc() // Tving garbage collection
        }
    }

    private fun hentKlokekslettOgPeriode(env: Environment): Pair<Date, Long> {
        val osloTz = ZoneId.of("Europe/Oslo")
        val now = ZonedDateTime.now(osloTz)
        if (env.cluster == "dev-gcp" || env.cluster == "flex") {
            val femMinutter = Duration.ofMinutes(2)
            val omEtMinutt = now.plusSeconds(60)
            return Pair(Date.from(omEtMinutt.toInstant()), femMinutter.toMillis())
        }
        if (env.cluster == "prod-gcp") {
            val enDag = Duration.ofDays(1).toMillis()
            val nesteNatt = now.next(LocalTime.of(2, 0, 0))
            return Pair(nesteNatt, enDag)
        }
        throw IllegalStateException("Ukjent cluster name for cronjob ${env.cluster}")
    }

    private fun ZonedDateTime.next(atTime: LocalTime): Date {
        return if (this.toLocalTime().isAfter(atTime)) {
            Date.from(
                this.plusDays(1).withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant()
            )
        } else {
            Date.from(this.withHour(atTime.hour).withMinute(atTime.minute).withSecond(atTime.second).toInstant())
        }
    }
}
