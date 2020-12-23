package no.nav.helse.flex.application.cronjob

import io.ktor.util.*
import no.nav.helse.flex.Environment
import no.nav.helse.flex.log
import java.time.*
import java.util.*
import kotlin.concurrent.fixedRateTimer

@KtorExperimentalAPI
fun setUpCronJob(env: Environment) {

    val podLeaderCoordinator = PodLeaderCoordinator(env)
    val (klokkeslett, period) = hentKlokekslettOgPeriode(env)

    log.info("Schedulerer cronjob start: $klokkeslett, periode: $period ms")

    fixedRateTimer(
        startAt = klokkeslett,
        period = period
    ) {
        if (podLeaderCoordinator.isLeader()) {
            log.info("Kjører reisetilskudd cronjob")
        } else {
            log.info("Jeg er ikke leder")
        }
    }
}

internal fun hentKlokekslettOgPeriode(env: Environment): Pair<Date, Long> {
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
