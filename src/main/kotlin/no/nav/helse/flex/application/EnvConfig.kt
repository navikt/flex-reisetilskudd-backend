package no.nav.helse.flex.application

import no.nav.helse.flex.Environment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EnvConfig {

    @Bean
    fun envionment(): Environment {
        return Environment()
    }
}
