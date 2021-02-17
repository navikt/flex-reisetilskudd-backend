package no.nav.helse.flex

import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private class PostgreSQLContainer11 : PostgreSQLContainer<PostgreSQLContainer11>("postgres:11.4-alpine")

abstract class AbstractContainerBaseTest {
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
}
