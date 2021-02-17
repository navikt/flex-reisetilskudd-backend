package no.nav.helse.flex

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest
@EnableMockOAuth2Server
@DirtiesContext
class ApplicationContextTest : AbstractContainerBaseTest() {

    @Test
    fun contextLoads() {
    }
}
