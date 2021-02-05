package no.nav.helse.flex.config

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@EnableJwtTokenValidation
@Configuration
class SecurityConfiguration

object OIDCIssuer {
    const val SELVBETJENING = "selvbetjening"
}
