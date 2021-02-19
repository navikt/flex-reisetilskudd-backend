package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.db.ReisetilskuddSoknadRepository
import no.nav.helse.flex.db.SporsmalRepository
import no.nav.helse.flex.db.SvarRepository
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.kafka.reisetilskuddTopic
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.concurrent.TimeUnit

private class PostgreSQLContainer11 : PostgreSQLContainer<PostgreSQLContainer11>("postgres:11.4-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractContainerBaseTest {

    @Autowired
    lateinit var reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository

    @Autowired
    lateinit var sporsmalRepository: SporsmalRepository

    @Autowired
    lateinit var svarRepository: SvarRepository

    @Autowired
    private lateinit var reistilskuddKafkaConsumer: KafkaConsumer<String, String>

    companion object {
        init {
            PostgreSQLContainer11().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.1.0")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
                System.setProperty("KAFKA_BOOTSTRAP_SERVERS_URL", it.bootstrapServers)
            }
        }
    }

    private fun KafkaConsumer<String, String>.subscribeHvisIkkeSubscribed() {
        if (this.subscription().isEmpty()) {
            this.subscribe(listOf(reisetilskuddTopic))
        }
    }

    fun hentPoduserteReisetilskuddRecords(duration: Duration = Duration.ofMillis(100)): ConsumerRecords<String, String> {
        val records = reistilskuddKafkaConsumer.poll(duration).also { reistilskuddKafkaConsumer.commitSync() }
        return records
    }

    fun hentPoduserteReisetilskudd(duration: Duration = Duration.ofMillis(100)): List<ReisetilskuddSoknad> {
        return hentPoduserteReisetilskuddRecords(duration)
            .iterator()
            .asSequence()
            .map { it.value() }
            .toList()
            .map { objectMapper.readValue(it) }
    }

    fun ventPåProduserterReisetilskudd(antall: Int = 1, sekunder: Long = 2): List<ReisetilskuddSoknad> {
        val alle = ArrayList<ReisetilskuddSoknad>()
        Awaitility.await().atMost(sekunder, TimeUnit.SECONDS).until {
            alle.addAll(hentPoduserteReisetilskudd())
            alle.size == antall
        }
        return alle
    }

    @AfterAll
    fun `Vi leser reisetilskudd kafka topicet og feil hvis noe finnes og slik at subklassetestene leser alt`() {
        hentPoduserteReisetilskuddRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser reisetilskudd kafka topicet og feiler om noe eksisterer`() {
        reistilskuddKafkaConsumer.subscribeHvisIkkeSubscribed()
        hentPoduserteReisetilskuddRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi tømmer databasen`() {
        svarRepository.deleteAll()
        sporsmalRepository.deleteAll()
        reisetilskuddSoknadRepository.deleteAll()
    }
}
