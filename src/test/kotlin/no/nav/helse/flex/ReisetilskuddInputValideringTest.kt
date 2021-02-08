package no.nav.helse.flex

import no.nav.helse.flex.db.ReisetilskuddSoknadRepository
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.ReisetilskuddStatus.FREMTIDIG
import no.nav.helse.flex.domain.ReisetilskuddStatus.ÅPEN
import no.nav.helse.flex.utils.TestHelper
import no.nav.helse.flex.utils.hentSøknadResultActions
import no.nav.helse.flex.utils.sendSøknadResultActions
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.util.*

@SpringBootTest
@Testcontainers
@DirtiesContext
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class ReisetilskuddInputValideringTest : TestHelper {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainerWithProps()

        @Container
        val kafkaContainer = KafkaContainerWithProps()

        val fnr = "12345678901"
    }

    @Autowired
    override lateinit var server: MockOAuth2Server

    @Autowired
    override lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var reisetilskuddSoknadRepository: ReisetilskuddSoknadRepository

    @Test
    fun `Man kan ikke sende en FREMTIDIG eller ÅPEN søknad`() {
        val reisetilskudd = reisetilskuddSoknadRepository.save(reisetilskudd(FREMTIDIG))

        val reisetilskudd2 = reisetilskuddSoknadRepository.save(reisetilskudd(ÅPEN))

        val json1 = this.sendSøknadResultActions(reisetilskudd.id!!, fnr)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn().response.contentAsString
        json1 `should be equal to` "{\"reason\":\"FREMTIDIG ikke støttet for operasjon send\"}"

        val json2 = this.sendSøknadResultActions(reisetilskudd2.id!!, fnr)
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andReturn().response.contentAsString
        json2 `should be equal to` "{\"reason\":\"ÅPEN ikke støttet for operasjon send\"}"
    }

    @Test
    fun `Ukjent id gir 404`() {

        val json1 = this.hentSøknadResultActions(UUID.randomUUID().toString(), fnr)
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andReturn().response.contentAsString
        json1 `should be equal to` "{\"reason\":\"Søknad ikke funnet\"}"
    }

    @Test
    fun `En annen persons reisetilskudd id gir 403`() {
        val reisetilskudd = reisetilskuddSoknadRepository.save(reisetilskudd(FREMTIDIG))

        val json1 = this.hentSøknadResultActions(reisetilskudd.id!!, "123423232")
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn().response.contentAsString
        json1 `should be equal to` "{\"reason\":\"Er ikke eier\"}"
    }

    fun reisetilskudd(status: ReisetilskuddStatus): ReisetilskuddSoknad {
        return ReisetilskuddSoknad(
            fnr = fnr,
            fom = LocalDate.now().plusDays(1),
            tom = LocalDate.now().plusDays(3),
            arbeidsgiverNavn = "dsf",
            arbeidsgiverOrgnummer = "sdfsdf",
            status = status,
            sykmeldingId = UUID.randomUUID().toString(),
            opprettet = Instant.now(),
            endret = Instant.now(),
        )
    }
}
