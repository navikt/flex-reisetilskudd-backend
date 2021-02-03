package no.nav.helse.flex.reisetilskudd.api

import no.nav.helse.flex.log
import no.nav.security.token.support.core.exceptions.JwtTokenInvalidClaimException
import no.nav.security.token.support.core.exceptions.JwtTokenMissingException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import javax.servlet.http.HttpServletRequest


@ControllerAdvice
class GlobalExceptionHandler {

    private val log = log()


    @ExceptionHandler(java.lang.Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {


        log.error("Internal server error - ${ex.message} - ${request.method}: ${request.requestURI}", ex)
        return skapResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)


    }
}

private fun skapResponseEntity(status: HttpStatus): ResponseEntity<Any> =
    ResponseEntity(ApiError(status.reasonPhrase), status)

private data class ApiError(val reason: String)
