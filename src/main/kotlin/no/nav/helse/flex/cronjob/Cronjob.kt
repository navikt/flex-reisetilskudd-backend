package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class Cronjob(
    val aktiverService: AktiverService,
    val leaderElection: LeaderElection
) {
    val log = logger()

    @Scheduled(cron = "\${cronjob}")
    fun run() {
        if (leaderElection.isLeader()) {

            log.info("Kjører cronjob")

            aktiverService.sendbareReisetilskudd()
            aktiverService.åpneReisetilskudd()

            log.info("Ferdig med cronjob")
        }
    }
}
