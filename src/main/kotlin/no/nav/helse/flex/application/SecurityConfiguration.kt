package no.nav.helse.flex.application

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@EnableJwtTokenValidation
@Configuration
class SecurityConfiguration
