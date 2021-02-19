package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.Application
import no.nav.helse.flex.client.bucketuploader.BucketUploaderClient
import no.nav.helse.flex.client.bucketuploader.VedleggRespons
import no.nav.helse.flex.utils.TestHelper
import no.nav.helse.flex.utils.serialisertTilString
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import java.net.URI

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
internal class BucketUploaderServiceTest : TestHelper {

    @Autowired
    lateinit var bucketUploaderClient: BucketUploaderClient

    @Autowired
    override lateinit var server: MockOAuth2Server

    @Autowired
    override lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var flexBucketUploaderRestTemplate: RestTemplate

    private lateinit var flexBucketUploader: MockRestServiceServer

    @BeforeEach
    fun init() {
        flexBucketUploader = MockRestServiceServer.createServer(flexBucketUploaderRestTemplate)
    }

    @Test
    fun `Tester slett kall mot bucket-uploder`() {
        val blobName = "blobname"

        flexBucketUploader.expect(
            once(),
            requestTo(URI("http://flex-bucket-uploader/maskin/slett/$blobName"))
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        VedleggRespons(
                            id = "123",
                            melding = "test"
                        ).serialisertTilString()
                    )
            )

        bucketUploaderClient.slettKvittering(blobName) `should be` true
    }
}
