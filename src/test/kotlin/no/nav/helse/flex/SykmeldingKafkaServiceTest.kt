package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.client.syketilfelle.OppfolgingstilfelleDTO
import no.nav.helse.flex.client.syketilfelle.PeriodeDTO
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.utils.TestHelper
import no.nav.helse.flex.utils.hentSoknader
import no.nav.helse.flex.utils.lagSykmeldingMessage
import no.nav.helse.flex.utils.serialisertTilString
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
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
import java.util.concurrent.TimeUnit

@SpringBootTest
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class SykmeldingKafkaServiceTest : TestHelper, AbstractContainerBaseTest() {
    companion object {

        val aktorId = "aktorid123"

        val getPersonResponse = GetPersonResponse(
            errors = emptyList(),
            data = ResponseData(
                hentPerson = HentPerson(navn = listOf(Navn(fornavn = "ÅGE", mellomnavn = "MELOMNØVN", etternavn = "ETTERNæVN"))),
                hentIdenter = HentIdenter(listOf(PdlIdent(gruppe = AKTORID, ident = aktorId)))
            )
        )

        val reisetilskuddPeriode = SykmeldingsperiodeDTO(
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true,
            fom = LocalDate.now().minusDays(10),
            tom = LocalDate.now(),
            gradert = null,
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            aktivitetIkkeMulig = null
        )
        val tomPerioder = lagSykmeldingMessage(
            sykmeldingsperioder = emptyList()
        )
        val utenReisetilskudd = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false
                )
            )
        )
        val ikkeAllePerioderErReisetilskudd = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode,
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false
                )
            )
        )
        val gradertPerioe = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    gradert = GradertDTO(
                        grad = 50,
                        reisetilskudd = true
                    )
                )
            )
        )
        val feilVedScanning = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = true
                )
            )
        )
        val enGyldigPeriode = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode
            )
        )
        val flereGyldigePerioder = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode,
                reisetilskuddPeriode.copy(
                    fom = LocalDate.now().plusDays(1),
                    tom = LocalDate.now().plusDays(10)
                )
            )
        )
    }

    @Autowired
    override lateinit var server: MockOAuth2Server

    @Autowired
    override lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var flexFssProxyRestTemplate: RestTemplate

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
        val soknader = this.hentSoknader("fnr")
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `Sykmelding uten perioder`() {
        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                tomPerioder.sykmelding.id,
                tomPerioder
            )
        ).get()
        this.hentSoknader("fnr").size `should be equal to` 0
    }

    @Test
    @Order(2)
    fun `Sykmelding uten reisetilskudd`() {
        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                utenReisetilskudd.sykmelding.id,
                utenReisetilskudd
            )
        ).get()
        this.hentSoknader("fnr").size `should be equal to` 0
    }

    @Test
    @Order(3)
    fun `Sykmelding der ikke alle periode er reisetilskudd`() {
        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                ikkeAllePerioderErReisetilskudd.sykmelding.id,
                ikkeAllePerioderErReisetilskudd
            )
        ).get()
        this.hentSoknader("fnr").size `should be equal to` 0
    }

    @Test
    @Order(4)
    fun `Sykmelding inneholder gradert perioe`() {
        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                gradertPerioe.sykmelding.id,
                gradertPerioe
            )
        ).get()
        this.hentSoknader("fnr").size `should be equal to` 0
    }

    @Test
    @Order(5)
    fun `Sykmelding med feilVedScanning`() {
        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                feilVedScanning.sykmelding.id,
                feilVedScanning
            )
        ).get()
        this.hentSoknader("fnr").size `should be equal to` 0
    }

    @Test
    @Order(6)
    fun `Sykmelding innenfor arbeidsgiverperioden og skal ikke opprette søknad`() {
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
                            antallBrukteDager = 10,
                            oppbruktArbeidsgvierperiode = false,
                            arbeidsgiverperiode = PeriodeDTO(
                                fom = LocalDate.now().minusDays(9),
                                tom = LocalDate.now().plusDays(6)
                            )
                        ).serialisertTilString()
                    )
            )

        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                enGyldigPeriode.sykmelding.id,
                enGyldigPeriode
            )
        ).get()

        await().during(5, TimeUnit.SECONDS).until { this.hentSoknader("fnr").size == 0 }

        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(7)
    fun `Sykmelding med en gyldig periode`() {
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

        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                enGyldigPeriode.sykmelding.id,
                enGyldigPeriode
            )
        ).get()

        await().during(5, TimeUnit.SECONDS).until { this.hentSoknader("fnr").size == 1 }

        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(8)
    fun `Sykmelding med flere gyldige perioder`() {
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

        sykmeldingKafkaProducer.send(
            ProducerRecord(
                "syfo-sendt-sykmelding",
                flereGyldigePerioder.sykmelding.id,
                flereGyldigePerioder
            )
        ).get()

        await().during(5, TimeUnit.SECONDS).until { this.hentSoknader("fnr").size == 3 }

        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(9)
    fun `Reisetilskuddene er tilgjengelig`() {

        val reisetilskuddene = this.hentSoknader("fnr")

        reisetilskuddene.size `should be equal to` 3
        reisetilskuddene.filter {
            it.sykmeldingId == enGyldigPeriode.sykmelding.id
        }.size `should be equal to` 1
        reisetilskuddene.filter {
            it.sykmeldingId == flereGyldigePerioder.sykmelding.id
        }.size `should be equal to` 2
    }

    @Test
    @Order(10)
    fun `3 reisetilskudd ble produsert`() {
        ventPåProduserterReisetilskudd(3)
    }
}
