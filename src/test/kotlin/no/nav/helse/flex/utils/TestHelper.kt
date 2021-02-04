package no.nav.helse.flex.utils

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Svar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

interface TestHelper {
    val mockMvc: MockMvc
    val server: MockOAuth2Server
}

fun TestHelper.hentSoknader(fnr: String): List<Reisetilskudd> {
    val json = this.mockMvc.perform(
        get("/api/v1/reisetilskudd")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.avbrytSøknad(fnr: String, reisetilskuddId: String): Reisetilskudd {
    val json = this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/avbryt")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.sendSøknad(fnr: String, reisetilskuddId: String): Reisetilskudd {
    val json =
        sendSøknadResultActions(reisetilskuddId, fnr).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.sendSøknadResultActions(
    reisetilskuddId: String,
    fnr: String
) = this.mockMvc.perform(
    post("/api/v1/reisetilskudd/$reisetilskuddId/send")
        .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
        .contentType(MediaType.APPLICATION_JSON)
)

fun TestHelper.hentSøknadResultActions(
    reisetilskuddId: String,
    fnr: String
) = this.mockMvc.perform(
    get("/api/v1/reisetilskudd/$reisetilskuddId")
        .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
        .contentType(MediaType.APPLICATION_JSON)
)

fun TestHelper.gjenåpneSøknad(fnr: String, reisetilskuddId: String): Reisetilskudd {
    val json = this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/gjenapne")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.svar(fnr: String, reisetilskuddId: String, svar: Svar): Reisetilskudd {
    val json = this.mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v1/reisetilskudd/$reisetilskuddId", svar)
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .content(objectMapper.writeValueAsString(svar))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.lagreKvittering(fnr: String, reisetilskuddId: String, kvittering: Kvittering): Kvittering {
    val json = this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/kvittering", kvittering)
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .content(objectMapper.writeValueAsString(kvittering))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isCreated).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.slettKvittering(fnr: String, reisetilskuddId: String, kvitteringId: String) {
    this.mockMvc.perform(
        MockMvcRequestBuilders.delete("/api/v1/reisetilskudd/$reisetilskuddId/kvittering/$kvitteringId")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
    ).andExpect(status().isNoContent).andReturn()
}
