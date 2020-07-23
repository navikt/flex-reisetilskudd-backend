package no.nav.syfo.reisetilskudd.api.utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset

data class Respons (
    val message: String
)

fun Respons.toTextContent(httpStatusCode: HttpStatusCode = HttpStatusCode.OK) =
    TextContent(this.toJson(), ContentType.Application.Json.withCharset(Charsets.UTF_8), httpStatusCode)

private fun Respons.toJson(): String {
    val objectMapper = ObjectMapper()
    return objectMapper.writeValueAsString(this)
}
