package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
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
import org.springframework.test.web.client.ExpectedCount.times
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

        val sykmeldinger = listOf(
            tomPerioder,
            utenReisetilskudd,
            ikkeAllePerioderErReisetilskudd,
            gradertPerioe,
            feilVedScanning,
            enGyldigPeriode,
            flereGyldigePerioder
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

    @Test
    @Order(0)
    fun `Det er ingen reisetilskudd til å begynne med`() {
        val soknader = this.hentSoknader("fnr")
        soknader.shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `Alle sykmeldinger publiseres og konsumeres`() {

        flexFssProxyMockServer = MockRestServiceServer.createServer(flexFssProxyRestTemplate)
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
                hentIdenter = HentIdenter(listOf(PdlIdent(gruppe = AKTORID, "aktorid123")))
            )
        )

        flexFssProxyMockServer.expect(
            times(2),
            requestTo(URI("http://flex-fss-proxy/api/pdl/graphql"))
        )
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(getPersonResponse.serialisertTilString())
            )

        sykmeldinger.forEach { syk ->
            sykmeldingKafkaProducer.send(
                ProducerRecord(
                    "syfo-sendt-sykmelding",
                    syk.sykmelding.id,
                    syk
                )
            ).get()
        }
        await().during(5, TimeUnit.SECONDS).until { this.hentSoknader("fnr").size == 3 }

        flexFssProxyMockServer.verify()
    }

    @Test
    @Order(2)
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
}
