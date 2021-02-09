package no.nav.helse.flex

import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.domain.Transportmiddel
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.reisetilskudd.ReisetilskuddService
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

    @Autowired
    lateinit var reisetilskuddService: ReisetilskuddService

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

        reisetilskudd[0].sendt.shouldBeNull()
        reisetilskudd[0].avbrutt.shouldBeNull()
        reisetilskudd[0].kvitteringer.shouldBeEmpty()
        reisetilskudd[0].arbeidsgiverOrgnummer.shouldBeNull()
        reisetilskudd[0].arbeidsgiverNavn.shouldBeNull()

        reisetilskudd[0].sporsmal.map { it.tag } shouldBeEqualTo listOf(
            ANSVARSERKLARING,
            TRANSPORT_TIL_DAGLIG,
            REISE_MED_BIL,
            KVITTERINGER,
            UTBETALING
        )
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
        val reisetilskudd = this.hentSoknader(fnr).first()
        reisetilskudd.kvitteringer.shouldBeEmpty()

        val kvittering = this.lagreKvittering(
            fnr,
            reisetilskudd.id,
            Kvittering(
                id = UUID.randomUUID().toString(),
                blobId = "123456",
                belop = 133700,
                typeUtgift = Transportmiddel.EGEN_BIL,
                datoForUtgift = LocalDate.now(),
            )
        )

        kvittering.datoForUtgift.`should be equal to`(LocalDate.now())
        kvittering.id.shouldNotBeNull()

        val oppdatertReisetilskudd = this.hentSoknader(fnr).first()
        oppdatertReisetilskudd.kvitteringer.shouldNotBeEmpty()
    }

    @Test
    @Order(7)
    fun `Vi kan se den opplastede kvitteringen`() {
        val kvitteringer = this.hentSoknader(fnr).first().kvitteringer

        kvitteringer.size `should be equal to` 1
        val kvittering = kvitteringer.first()

        kvittering.datoForUtgift.`should be equal to`(LocalDate.now())
        kvittering.id.shouldNotBeNull()
        kvittering.belop.`should be equal to`(133700)
    }

    @Test
    @Order(8)
    fun `Vi kan slette en kvittering`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        reisetilskudd.kvitteringer.size `should be equal to` 1
        this.slettKvittering(fnr, reisetilskudd.id, reisetilskudd.kvitteringer[0].id)

        val reisetilskuddEtter = this.hentSoknader(fnr).first()
        reisetilskuddEtter.kvitteringer.size.`should be equal to`(0)
    }

    @Test
    @Order(9)
    fun `Vi kan sende inn søknaden`() {
        val reisetilskudd = this.hentSoknader(fnr).first()
        val sendtSøknad = SoknadBesvarer(reisetilskudd, this.mockMvc, server, fnr)
            .sendSoknad()
        sendtSøknad.status shouldBeEqualTo ReisetilskuddStatus.SENDT
        sendtSøknad.sendt.shouldNotBeNull()
    }
}
