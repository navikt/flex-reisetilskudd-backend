package no.nav.helse.flex.client.pdl

import java.util.*

const val AKTORID = "AKTORID"

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ResponseData(
    val hentPerson: HentPerson? = null,
    val hentIdenter: HentIdenter? = null,
)

data class HentIdenter(
    val identer: List<PdlIdent>
)

data class PdlIdent(val gruppe: String, val ident: String)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)

data class HentPerson(
    val navn: List<Navn>? = null,
    val adressebeskyttelse: List<Adressebeskyttelse>? = null
)

data class Adressebeskyttelse(
    val gradering: String
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

private val norskLocale = Locale("nb")

private fun String.storForbokstav(): String {
    return this.toLowerCase(norskLocale).capitalize(norskLocale)
}

fun Navn.format(): String =
    if (mellomnavn != null) {
        "${fornavn.storForbokstav()} ${mellomnavn.storForbokstav()} ${etternavn.storForbokstav()}"
    } else {
        "${fornavn.storForbokstav()} ${etternavn.storForbokstav()}"
    }
