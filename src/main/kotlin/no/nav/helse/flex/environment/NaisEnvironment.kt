package no.nav.helse.flex.environment

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NaisEnvironment(
    @Value("\${NAIS_CLUSTER_NAME}")
    private val cluster: String,
) {

    fun erDevEllerDockerCompose(): Boolean = (cluster == "dev-gcp" || cluster == "flex")
    fun erProduksjon(): Boolean = cluster == "prod-gcp"
    fun erIkkeProduksjon(): Boolean = !erProduksjon()
}
