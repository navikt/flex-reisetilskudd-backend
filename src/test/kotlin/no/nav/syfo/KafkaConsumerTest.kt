package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import kotlin.test.assertFailsWith

class KafkaConsumerTest : Spek({
    describe("Test kafkaconsumer") {
        it("Should fail on creating KafkaConsumer") {
            val env = Environment(kafkaBootstrapServers = "localhost:8080")
            val vaultSecrets =
                objectMapper.readValue<VaultSecrets>(Paths.get("/secrets/credentials.json").toFile())
            val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
            val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                "${env.applicationName}-consumer",
                JacksonKafkaDeserializer::class
            )
            consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            assertFailsWith<KafkaException> { KafkaConsumer<String, EnkelSykmelding>(consumerProperties) }
        }
        it("Should create kafkaConsumer without failure") {
            val env = Environment(kafkaBootstrapServers = "localhost:8080")
            val vaultSecrets =
                objectMapper.readValue<VaultSecrets>(Paths.get("/secrets/credentials.json").toFile())
            val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
            val consumerProperties = kafkaBaseConfig.toConsumerConfig(
                "${env.applicationName}-consumer",
                JacksonKafkaDeserializer::class
            )
            consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val kafkaConsumer = KafkaConsumer<String, EnkelSykmelding>(consumerProperties)
        }
    }
})
