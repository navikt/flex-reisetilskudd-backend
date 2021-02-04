package no.nav.helse.flex

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@EnableMockOAuth2Server
@DirtiesContext
class ApplicationContextTest {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()
    }

    @Test
    fun contextLoads() {
    }
}
