package no.nav.helse.flex

import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.Svar
import no.nav.helse.flex.reisetilskudd.domain.Transportmiddel
import no.nav.helse.flex.utils.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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
        val tom = LocalDate.now().minusDays(1)
        val fom = LocalDate.now().minusDays(5)
        val sykmeldingId = UUID.randomUUID().toString()
    }

    @Autowired
    override lateinit var server: MockOAuth2Server

    @Autowired
    override lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sykmeldingKafkaProducer: KafkaProducer<String, SykmeldingMessage>

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
        reisetilskudd[0].egenBil shouldBeEqualTo 0.0
        reisetilskudd[0].sykler.shouldBeNull()
        reisetilskudd[0].kollektivtransport shouldBeEqualTo 0.0
        reisetilskudd[0].oppfølgende.`should be false`()
        reisetilskudd[0].sendt.shouldBeNull()
        reisetilskudd[0].avbrutt.shouldBeNull()
        reisetilskudd[0].går.shouldBeNull()
        reisetilskudd[0].kvitteringer.shouldBeEmpty()
        reisetilskudd[0].orgNummer.shouldBeNull()
        reisetilskudd[0].orgNavn.shouldBeNull()
        reisetilskudd[0].utbetalingTilArbeidsgiver.shouldBeNull()
    }

    @Test
    @Order(3)
    fun `Vi kan avbryte søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        val avbruttSøknad = this.avbrytSøknad(fnr, reisetilskudd.first().reisetilskuddId)
        avbruttSøknad.status shouldBeEqualTo ReisetilskuddStatus.AVBRUTT
        avbruttSøknad.avbrutt.shouldNotBeNull()
    }

    @Test
    @Order(4)
    fun `Vi kan gjenåpne søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr)

        val gjenåpnet = this.gjenåpneSøknad(fnr, reisetilskudd.first().reisetilskuddId)
        gjenåpnet.status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        gjenåpnet.avbrutt.shouldBeNull()
    }

    @Test
    @Order(5)
    fun `Vi kan besvare et av spørsmålene`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        reisetilskudd.sykler.shouldBeNull()

        val besvart = this.svar(fnr, reisetilskudd.reisetilskuddId, Svar(sykler = true))
        besvart.sykler?.shouldBeTrue()

        val a = "test"
    }

    @Test
    @Order(6)
    fun `Vi kan laste opp en kvittering`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        reisetilskudd.kvitteringer.shouldBeEmpty()

        val kvittering = this.lagreKvittering(
            fnr,
            reisetilskudd.reisetilskuddId,
            Kvittering(
                blobId = "123456",
                belop = 133700,
                storrelse = 12,
                transportmiddel = Transportmiddel.EGEN_BIL,
                datoForReise = LocalDate.now(),
                navn = "bilde123.jpg"
            )
        )

        kvittering.datoForReise.`should be equal to`(LocalDate.now())
        kvittering.kvitteringId.shouldNotBeNull()

        val oppdatertReisetilskudd = this.hentSoknader(fnr).first()
        oppdatertReisetilskudd.kvitteringer.shouldNotBeEmpty()
    }
/*
    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        with(testApp) {
            val kvitteringer = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].kvitteringer

            kvitteringer.size `should be equal to` 1
            val kvittering = kvitteringer.first()

            kvittering.datoForReise.`should be equal to`(LocalDate.now())
            kvittering.kvitteringId.shouldNotBeNull()
            kvittering.storrelse.`should be equal to`(12)
            kvittering.belop.`should be equal to`(133700)
        }
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {
        with(testApp) {
            val reisetilskudd = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0]

            reisetilskudd.kvitteringer.size.`should be equal to`(1)

            with(
                engine.handleRequest(
                    HttpMethod.Delete,
                    "/api/v1/reisetilskudd/${reisetilskudd.reisetilskuddId}/kvittering/${reisetilskudd.kvitteringer[0].kvitteringId}"
                ) {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }

            val reisetilskuddEtter = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0]

            reisetilskuddEtter.kvitteringer.size.`should be equal to`(0)
        }
    }

    @Test
    @Order(9)
    fun `Vi kan sende inn søknaden`() {
        with(testApp) {
            val id = engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                medSelvbetjeningToken(fnr)
            }.response.content!!.tilReisetilskuddListe()[0].reisetilskuddId
            with(
                engine.handleRequest(HttpMethod.Post, "/api/v1/reisetilskudd/$id/send") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
            }
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken(fnr)
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskudd = response.content!!.tilReisetilskuddListe()
                reisetilskudd.size `should be equal to` 1

                reisetilskudd[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
                reisetilskudd[0].sendt.shouldNotBeNull()
            }
        }
    }


    */
}
