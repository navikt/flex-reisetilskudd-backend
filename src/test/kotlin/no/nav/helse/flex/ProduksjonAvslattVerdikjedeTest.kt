package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.client.syketilfelle.OppfolgingstilfelleDTO
import no.nav.helse.flex.client.syketilfelle.PeriodeDTO
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.utils.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["NAIS_CLUSTER_NAME=prod-gcp"])
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ProduksjonAvslattVerdikjedeTest : TestHelper, AbstractContainerBaseTest() {

    companion object {
        val fnr = "12345678901"
        val aktorId = "aktorid123"
        val tom = LocalDate.now().minusDays(1)
        val fom = LocalDate.now().minusDays(5)
        val sykmeldingId = UUID.randomUUID().toString()
    }

    @Autowired
    override lateinit var server: MockOAuth2Server

    @Autowired
    override lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var flexFssProxyRestTemplate: RestTemplate

    @Autowired
    lateinit var sykmeldingKafkaProducer: KafkaProducer<String, SykmeldingMessage>

    @Autowired
    private lateinit var flexBucketUploaderRestTemplate: RestTemplate

    private lateinit var flexBucketUploader: MockRestServiceServer

    private lateinit var flexFssProxyMockServer: MockRestServiceServer

    @Autowired
    private lateinit var metrikk: Metrikk

    @BeforeEach
    fun init() {
        flexFssProxyMockServer = MockRestServiceServer.createServer(flexFssProxyRestTemplate)
        flexBucketUploader = MockRestServiceServer.createServer(flexBucketUploaderRestTemplate)
    }

    @Test
    @Order(0)
    fun `Det er ingen reisetilskudd til å begynne med`() {
        val soknader = this.hentSoknader(fnr)
        soknader.shouldBeEmpty()
        metrikk.sykmeldingHeltUtafor.count() `should be equal to` 0.0
    }

    @Test
    @Order(1)
    fun `En sykmelding med reisetilskudd konsumereres`() {
        val getPersonResponse = GetPersonResponse(
            errors = emptyList(),
            data = ResponseData(
                hentPerson = HentPerson(navn = listOf(Navn(fornavn = "ÅGE", mellomnavn = "MELOMNØVN", etternavn = "ETTERNæVN"))),
                hentIdenter = HentIdenter(listOf(PdlIdent(gruppe = AKTORID, ident = aktorId)))
            )
        )

        flexFssProxyMockServer.expect(
            once(),
            requestTo(URI("http://flex-fss-proxy/api/pdl/graphql"))
        )
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(getPersonResponse.serialisertTilString())
            )

        flexFssProxyMockServer.expect(
            once(),
            requestTo(URI("http://flex-fss-proxy/reisetilskudd/$aktorId/oppfolgingstilfelle"))
        )
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        OppfolgingstilfelleDTO(
                            antallBrukteDager = 20,
                            oppbruktArbeidsgvierperiode = true,
                            arbeidsgiverperiode = PeriodeDTO(
                                fom = LocalDate.now().minusDays(30),
                                tom = LocalDate.now().minusDays(10)
                            )
                        ).serialisertTilString()
                    )
            )

        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, sykmeldingId = sykmeldingId)
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId, fom = fom, tom = tom)

        sykmeldingBehandlet(fnr = ReisetilskuddVerdikjedeTest.fnr, sykmeldingId = sykmeldingId) `should be equal to` false

        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                sykmelding.id,
                SykmeldingMessage(
                    sykmelding = sykmelding,
                    event = sykmeldingStatusKafkaMessageDTO.event,
                    kafkaMetadata = sykmeldingStatusKafkaMessageDTO.kafkaMetadata
                )
            )
        ).get()

        await().atMost(3, TimeUnit.SECONDS).until { metrikk.sykmeldingHeltUtafor.count() == 1.0 }
        flexFssProxyMockServer.verify()
        metrikk.sykmeldingHeltUtafor.count() `should be equal to` 1.0
        sykmeldingBehandlet(fnr = ReisetilskuddVerdikjedeTest.fnr, sykmeldingId = sykmeldingId) `should be equal to` true
    }

    @Test
    @Order(2)
    fun `Ingen reisetilskudd er tilgjengelig`() {
        val reisetilskudd = this.hentSoknader(fnr)
        reisetilskudd.size `should be equal to` 0
    }

    @Test
    @Order(12)
    fun `0 produsert`() {
        ventPåProduserteReisetilskudd(0)
    }
}
