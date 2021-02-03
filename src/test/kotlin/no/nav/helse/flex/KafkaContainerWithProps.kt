package no.nav.helse.flex

import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

class KafkaContainerWithProps : KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.3")) {

    override fun start() {
        super.start()
        System.setProperty("KAFKA_BROKERS", this.bootstrapServers)
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS_URL", this.bootstrapServers)
    }
}
