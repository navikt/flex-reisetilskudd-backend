package no.nav.helse.flex.utils

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.objectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

interface TestHelper {
    val mockMvc: MockMvc
    val server: MockOAuth2Server
}

fun TestHelper.hentSoknader(fnr: String): List<ReisetilskuddSoknad> {
    val json = this.mockMvc.perform(
        get("/api/v1/reisetilskudd")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.avbrytSøknad(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {
    val json = this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/avbryt")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.sendSøknad(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {
    val json = sendSøknadResultActions(reisetilskuddId, fnr)
        .andExpect(status().isOk)
        .andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.oppdaterSporsmalMedResult(fnr: String, reisetilskuddId: String, sporsmal: Sporsmal): ResultActions {
    return this.mockMvc.perform(
        put("/api/v1/reisetilskudd/$reisetilskuddId/sporsmal/${sporsmal.id}")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .content(objectMapper.writeValueAsString(sporsmal))
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun TestHelper.oppdaterSporsmal(fnr: String, reisetilskuddId: String, sporsmal: Sporsmal): OppdaterSporsmalResponse {
    val json = oppdaterSporsmalMedResult(fnr, reisetilskuddId, sporsmal)
        .andExpect(status().isOk)
        .andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun TestHelper.slettSvarMedResult(
    fnr: String,
    reisetilskuddId: String,
    sporsmalId: String,
    svarId: String
): ResultActions {
    return this.mockMvc.perform(
        delete("/api/v1/reisetilskudd/$reisetilskuddId/sporsmal/$sporsmalId/svar/$svarId")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
    )
}

fun TestHelper.slettSvar(fnr: String, reisetilskuddId: String, sporsmalId: String, svarId: String) {
    slettSvarMedResult(fnr, reisetilskuddId, sporsmalId, svarId).andExpect(status().isNoContent).andReturn()
}

fun TestHelper.lagreSvarMedResult(fnr: String, reisetilskuddId: String, sporsmalId: String, svar: Svar): ResultActions {
    return this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/sporsmal/$sporsmalId/svar")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .content(objectMapper.writeValueAsString(svar))
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun TestHelper.lagreSvar(fnr: String, reisetilskuddId: String, sporsmalId: String, svar: Svar): Sporsmal {
    val json = lagreSvarMedResult(fnr, reisetilskuddId, sporsmalId, svar)
        .andExpect(status().isCreated)
        .andReturn().response.contentAsString

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

fun TestHelper.gjenåpneSøknad(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {
    val json = this.mockMvc.perform(
        post("/api/v1/reisetilskudd/$reisetilskuddId/gjenapne")
            .header("Authorization", "Bearer ${server.token(fnr = fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}
