package no.nav.helse.flex.utils

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

interface TestHelper {
    val mockMvc: MockMvc
    val server: MockOAuth2Server
}

fun TestHelper.hentSoknader(fnr: String): List<Reisetilskudd> {
    val json = this.mockMvc.perform(
        MockMvcRequestBuilders.get("/api/v1/reisetilskudd")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.avbrytSøknad(fnr: String, reisetilskuddId: String): Reisetilskudd {
    val json = this.mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v1/reisetilskudd/$reisetilskuddId/avbryt")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.gjenåpneSøknad(fnr: String, reisetilskuddId: String): Reisetilskudd {
    val json = this.mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v1/reisetilskudd/$reisetilskuddId/gjenapne")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}
