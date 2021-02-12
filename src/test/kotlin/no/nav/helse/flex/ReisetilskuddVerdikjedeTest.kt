package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.domain.Utgiftstype.PARKERING
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootTest
@Testcontainers
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ReisetilskuddVerdikjedeTest : TestHelper {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()

        val fnr = "12345678901"
        val aktorId = "aktor"
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
    lateinit var reisetilskuddService: ReisetilskuddService

    private lateinit var flexFssProxyMockServer: MockRestServiceServer

    @BeforeEach
    fun init() {
        flexFssProxyMockServer = MockRestServiceServer.createServer(flexFssProxyRestTemplate)
    }

    @Test
    fun `ingen token returnerer 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v1/reisetilskudd")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
    }

    @Test
    fun `Nivå 3 token returnerer 401`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v1/reisetilskudd")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${server.token(fnr = fnr, claims = mapOf("acr" to "Level3"))}")
        )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
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
        val getPersonResponse = GetPersonResponse(
            errors = emptyList(),
            data = ResponseData(
                hentPerson = HentPerson(navn = listOf(Navn(fornavn = "ÅGE", mellomnavn = "MELOMNØVN", etternavn = "ETTERNæVN"))),
                hentIdenter = HentIdenter(listOf(PdlIdent(gruppe = AKTORID, "aktorid123")))
            )
        )

        flexFssProxyMockServer.expect(
            once(),
            requestTo(URI("http://flex-fss-proxy/reisetilskudd/$aktorId/$sykmeldingId/erUtenforVentetid"))
        )
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(true))
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

        await().atMost(3, TimeUnit.SECONDS).until { this.hentSoknader(fnr).size == 1 }
        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(2)
    fun `Et reisetilskudd er tilgjengelig`() {
        val reisetilskudd = this.hentSoknader(fnr)
        reisetilskudd.size `should be equal to` 1
        reisetilskudd[0].fnr shouldBeEqualTo fnr
        reisetilskudd[0].fom shouldBeEqualTo fom
        reisetilskudd[0].tom shouldBeEqualTo tom
        reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        reisetilskudd[0].sykmeldingId shouldBeEqualTo sykmeldingId

        reisetilskudd[0].sendt.shouldBeNull()
        reisetilskudd[0].avbrutt.shouldBeNull()
        reisetilskudd[0].arbeidsgiverOrgnummer.shouldBeNull()
        reisetilskudd[0].arbeidsgiverNavn.shouldBeNull()

        reisetilskudd[0].sporsmal.map { it.tag } shouldBeEqualTo listOf(
            ANSVARSERKLARING,
            TRANSPORT_TIL_DAGLIG,
            REISE_MED_BIL,
            KVITTERINGER,
            UTBETALING
        )

        reisetilskudd[0].sporsmal.first { it.tag == ANSVARSERKLARING }.sporsmalstekst shouldBeEqualTo "Jeg, <strong>Åge Melomnøvn Etternævn</strong>, bekrefter at jeg vil gi riktige og fullstendige opplysninger."
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        val avbruttSøknad = this.avbrytSøknad(fnr, reisetilskudd.first().id)
        avbruttSøknad.status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
        avbruttSøknad.avbrutt.shouldNotBeNull()
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        val gjenåpnet = this.gjenåpneSøknad(fnr, reisetilskudd.first().id)
        gjenåpnet.status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        gjenåpnet.avbrutt.shouldBeNull()
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this.mockMvc, server, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")

        val svaret = this.hentSoknader(fnr).first().sporsmal.find { it.tag == ANSVARSERKLARING }!!.svar.first()
        svaret.verdi shouldBeEqualTo "CHECKED"
    }

    @Test
    @Order(6)
    fun `Vi kan laste opp en kvittering`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal.filter { it.tag == KVITTERINGER }.first()
        val svar = Svar(
            kvittering = Kvittering(
                blobId = "9a186e3c-aeeb-4566-a865-15aa9139d364",
                belop = 133700,
                typeUtgift = PARKERING,
                datoForUtgift = LocalDate.now(),
            )
        )

        val spmSomBleSvart = lagreSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id, svar)

        val returnertSvar = spmSomBleSvart.svar.first().kvittering!!

        returnertSvar.datoForUtgift.`should be equal to`(LocalDate.now())
        returnertSvar.typeUtgift.`should be equal to`(PARKERING)
    }

    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpm.svar.size `should be equal to` 1

        val svaret = kvitteringSpm.svar.first().kvittering!!

        svaret.datoForUtgift.`should be equal to`(LocalDate.now())
        svaret.blobId.`should be equal to`("9a186e3c-aeeb-4566-a865-15aa9139d364")
        svaret.belop.`should be equal to`(133700)
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {
        val reisetilskuddSoknad = this.hentSoknader(fnr).first()
        val kvitteringSpm = reisetilskuddSoknad.sporsmal.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpm.svar.size `should be equal to` 1

        val svaret = kvitteringSpm.svar.first()
        slettSvar(fnr, reisetilskuddSoknad.id, kvitteringSpm.id, svaret.id!!)

        val reisetilskuddSoknadEtter = this.hentSoknader(fnr).first()
        val kvitteringSpmEtter = reisetilskuddSoknadEtter.sporsmal.filter { it.tag == KVITTERINGER }.first()
        kvitteringSpmEtter.svar.size `should be equal to` 0
    }

    @Test
    @Order(9)
    fun `Vi tester å sende inn søknaden før alle svar er besvart og får bad request`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        this.sendSøknadResultActions(reisetilskudd.id, fnr)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(10)
    fun `Vi besvarer et spørsmål med feil type verdi`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        val utbetaling = reisetilskudd.sporsmal.first { it.tag == UTBETALING }.byttSvar(svar = "TJA")

        val json = oppdaterSporsmalMedResult(fnr, reisetilskudd.id, utbetaling)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn().response.contentAsString
        json shouldBeEqualTo "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(10)
    fun `Vi besvarer resten av spørsmålene`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        SoknadBesvarer(reisetilskudd, this.mockMvc, server, fnr)
            .besvarSporsmal(ANSVARSERKLARING, "CHECKED")
            .besvarSporsmal(TRANSPORT_TIL_DAGLIG, "NEI")
            .besvarSporsmal(REISE_MED_BIL, "NEI")
            .besvarSporsmal(UTBETALING, "JA")
    }

    @Test
    @Order(11)
    fun `Vi kan sende inn søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        val sendtSøknad = SoknadBesvarer(reisetilskudd, this.mockMvc, server, fnr)
            .sendSoknad()
        sendtSøknad.status shouldBeEqualTo ReisetilskuddStatus.SENDT
        sendtSøknad.sendt.shouldNotBeNull()
    }
}
