package no.nav.helse.flex

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.util.* // ktlint-disable no-wildcard-imports
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.helse.flex.application.ApplicationServer
import no.nav.helse.flex.application.ApplicationState
import no.nav.helse.flex.application.createApplicationEngine
import no.nav.helse.flex.application.getWellKnown
import no.nav.helse.flex.db.Database
import no.nav.helse.flex.kafka.* // ktlint-disable no-wildcard-imports
import no.nav.helse.flex.kafka.util.JacksonKafkaDeserializer
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.helse.flex.flex-reisetilskudd-backend")

@KtorExperimentalAPI
fun main() {
    log.info("Starter flex-reisetilskudd-backend")

    val envs = System.getenv()

    log.info("Fant ${envs.size} envs")
    for (envName in envs.keys) {
        log.info("Envname: $envName")
    }

    val env = Environment()

    // Sov litt slik at sidecars er klare
    Thread.sleep(env.sidecarInitialDelay)
    log.info("Sov i ${env.sidecarInitialDelay} ms i håp om at sidecars er klare")

    val wellKnown = getWellKnown(env.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val kafkaBaseConfig = loadBaseConfig(env, env.hentKafkaCredentials()).envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${env.applicationName}-consumer",
        JacksonKafkaDeserializer::class
    )
    consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
    val kafkaConsumer = KafkaConsumer<String, SykmeldingMessage?>(consumerProperties)
    val database = Database(env)

    val reisetilskuddService = ReisetilskuddService(database)

    val sykmeldingKafkaService = SykmeldingKafkaService(kafkaConsumer, applicationState, reisetilskuddService)
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
        sykmeldingKafkaService.run()
    }
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
