package no.nav.helse.flex.reisetilskudd

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import no.nav.helse.flex.Application
import no.nav.helse.flex.KafkaContainerWithProps
import no.nav.helse.flex.PostgreSQLContainerWithProps
import no.nav.helse.flex.client.bucketuploader.BucketUploaderClient
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.shouldBe
import org.apache.http.HttpHeaders
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@DirtiesContext
@EnableMockOAuth2Server
@Testcontainers
@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
internal class BucketUploaderServiceTest {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()
    }

    @Autowired
    lateinit var bucketUploaderClient: BucketUploaderClient

    @Test
    fun test() {
        stubFor(
            get(urlPathMatching("/maskin/slett/.*"))
                .withHeader(HttpHeaders.AUTHORIZATION, AnythingPattern())
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            //language=json
                            """
                               {
                                "id": "123",
                                "melding": "test"
                               } 
                            """.trimIndent()
                        )
                )
        )

        bucketUploaderClient.slettKvittering("test") shouldBe true
    }
}
