package no.nav.helse.flex

import no.nav.helse.flex.db.ReisetilskuddSoknadRepository
import no.nav.helse.flex.db.SporsmalRepository
import no.nav.helse.flex.db.SvarRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private class PostgreSQLContainer11 : PostgreSQLContainer<PostgreSQLContainer11>("postgres:11.4-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractContainerBaseTest {

    @Autowired
    lateinit var reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository

    @Autowired
    lateinit var sporsmalRepository: SporsmalRepository

    @Autowired
    lateinit var svarRepository: SvarRepository

    val log = logger()

    companion object {
        init {
            PostgreSQLContainer11().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.3")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
                System.setProperty("KAFKA_BOOTSTRAP_SERVERS_URL", it.bootstrapServers)
            }
        }
    }

    @AfterAll
    fun `Vi tømmer databasen`() {
        svarRepository.deleteAll()
        sporsmalRepository.deleteAll()
        reisetilskuddSoknadRepository.deleteAll()
    }
}
