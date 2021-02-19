package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.client.syketilfelle.OppfolgingstilfelleDTO
import no.nav.helse.flex.client.syketilfelle.PeriodeDTO
import no.nav.helse.flex.cronjob.AktiverService
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.utils.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.*
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

@SpringBootTest
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class PaabegyntReisetilskuddTest : TestHelper, AbstractContainerBaseTest() {

    companion object {
        val fnr = "12345678901"
        val tom = LocalDate.now().plusDays(1)
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
    private lateinit var aktiverService: AktiverService

    @Autowired
    lateinit var sykmeldingKafkaProducer: KafkaProducer<String, SykmeldingMessage>

    private lateinit var flexFssProxyMockServer: MockRestServiceServer

    @BeforeEach
    fun init() {
        flexFssProxyMockServer = MockRestServiceServer.createServer(flexFssProxyRestTemplate)
    }

    @Test
    @Order(0)
    fun `Det er ingen reisetilskudd til å begynne med`() {
        val soknader = this.hentSoknader(fnr)
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `En sykmelding med reisetilskudd konsumereres`() {
        val aktorId = "aktorid123"
        val getPersonResponse = GetPersonResponse(
            errors = emptyList(),
            data = ResponseData(
                hentPerson = HentPerson(
                    navn = listOf(
                        Navn(
                            fornavn = "ÅGE",
                            mellomnavn = "MELOMNØVN",
                            etternavn = "ETTERNæVN"
                        )
                    )
                ),
                hentIdenter = HentIdenter(listOf(PdlIdent(gruppe = AKTORID, aktorId)))
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
                                fom = fom.minusDays(20),
                                tom = fom
                            )
                        ).serialisertTilString()
                    )
            )

        val sykmeldingStatusKafkaMessageDTO =
            skapSykmeldingStatusKafkaMessageDTO(fnr = fnr, sykmeldingId = sykmeldingId)
        val sykmelding = getSykmeldingDto(sykmeldingId = sykmeldingId, fom = fom, tom = tom)
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

        await().atMost(6, TimeUnit.SECONDS).until { this.hentSoknader(fnr).size == 1 }
        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(2)
    fun `Et åpent reisetilskudd er tilgjengelig`() {
        val reisetilskudd = this.hentSoknader(fnr)
        reisetilskudd.size `should be equal to` 1
        reisetilskudd[0].fnr shouldBeEqualTo fnr
        reisetilskudd[0].fom shouldBeEqualTo fom
        reisetilskudd[0].tom shouldBeEqualTo tom
        reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskudd[0].sykmeldingId shouldBeEqualTo sykmeldingId

        reisetilskudd[0].sporsmal.map { it.tag } shouldBeEqualTo listOf(
            ANSVARSERKLARING,
            TRANSPORT_TIL_DAGLIG,
            REISE_MED_BIL,
            KVITTERINGER,
            UTBETALING
        )

        val kafkaMeldinger = ventPåProduserteReisetilskudd(1)
        kafkaMeldinger[0].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
    }

    @Test
    @Order(3)
    fun `Vi kan besvarer ansvarserklæringen`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this.mockMvc, server, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        val svaret = this.hentSoknader(fnr).first().sporsmal.find { it.tag == ANSVARSERKLARING }!!.svar.first()
        svaret.verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(4)
    fun `Reisetilskuddet er nå PÅBEGYNT`() {
        val reisetilskudd = this.hentSoknader(fnr)
        reisetilskudd.size `should be equal to` 1
        reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.PÅBEGYNT

        val kafkaMeldinger = ventPåProduserteReisetilskudd(1)
        kafkaMeldinger[0].status shouldBeEqualTo ReisetilskuddStatus.PÅBEGYNT
        kafkaMeldinger[0].sporsmal.find { it.tag == ANSVARSERKLARING }!!.svar.first().verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(5)
    fun `Vi kan avbryte søknaden`() {
        val reisetilskudd = this.hentSoknader(ReisetilskuddVerdikjedeTest.fnr)

        val avbruttSøknad = this.avbrytSøknad(ReisetilskuddVerdikjedeTest.fnr, reisetilskudd.first().id)
        avbruttSøknad.status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
        avbruttSøknad.avbrutt.shouldNotBeNull()

        val kafkaMeldinger = ventPåProduserteReisetilskudd(1)
        kafkaMeldinger[0].status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
    }

    @Test
    @Order(6)
    fun `Vi kan gjenåpne søknaden`() {
        val reisetilskudd = this.hentSoknader(ReisetilskuddVerdikjedeTest.fnr)

        val gjenåpnet = this.gjenåpneSøknad(ReisetilskuddVerdikjedeTest.fnr, reisetilskudd.first().id)
        gjenåpnet.status shouldBeEqualTo ReisetilskuddStatus.PÅBEGYNT
        gjenåpnet.avbrutt.shouldBeNull()

        val kafkaMeldinger = ventPåProduserteReisetilskudd(1)
        kafkaMeldinger[0].status shouldBeEqualTo ReisetilskuddStatus.PÅBEGYNT
    }

    @Test
    @Order(7)
    fun `Den påbegynte søknaden blir sendbar`() {

        val antall = aktiverService.sendbareReisetilskudd(now = tom.plusDays(1))
        antall `should be equal to` 1

        val kafkaMeldinger = ventPåProduserteReisetilskudd(1)
        kafkaMeldinger[0].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
    }
}
