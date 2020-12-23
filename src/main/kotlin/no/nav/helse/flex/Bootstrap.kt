package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.util.*
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.helse.flex.application.ApplicationServer
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.createApplicationEngine
import no.nav.helse.flex.application.cronjob.setUpCronJob
import no.nav.helse.flex.application.getWellKnown
import no.nav.helse.flex.db.Database
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingKafkaService
import no.nav.helse.flex.kafka.skapSykmeldingKafkaConsumer
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.helse.flex.flex-reisetilskudd-backend")

@KtorExperimentalAPI
fun main() {
    log.info("Starter flex-reisetilskudd-backend")

    val env = Environment()

    // Sov litt slik at sidecars er klare
    log.info("Sover i ${env.sidecarInitialDelay} ms i håp om at sidecars er klare")
    Thread.sleep(env.sidecarInitialDelay)

    val wellKnown = getWellKnown(env.loginserviceIdportenDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    DefaultExports.initialize()
    val applicationState = ApplicationState()

    val kafkaConsumer = skapSykmeldingKafkaConsumer(env)

    val kafkaAivenConfig = AivenKafkaConfig(environment = env)

    val database = Database(env)

    val reisetilskuddService = ReisetilskuddService(database, kafkaAivenConfig)

    val sykmeldingKafkaService = SykmeldingKafkaService(
        kafkaConsumer = kafkaConsumer,
        applicationState = applicationState,
        reisetilskuddService = reisetilskuddService,
        environment = env
    )
    val applicationEngine = createApplicationEngine(
        env = env,
        reisetilskuddService = reisetilskuddService,
        jwkProvider = jwkProvider,
        applicationState = applicationState,
        issuer = wellKnown.issuer
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    createListener(applicationState) {
        sykmeldingKafkaService.start()
    }
    setUpCronJob(env = env)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk galt $ex")
            ex.printStackTrace()
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
