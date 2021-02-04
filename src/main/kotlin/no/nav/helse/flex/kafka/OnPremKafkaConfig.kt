package no.nav.helse.flex.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.io.Serializable
import java.time.Duration

@Configuration
class OnPremKafkaConfig(
    @Value("\${KAFKA_BOOTSTRAP_SERVERS_URL}") private val kafkaBootstrapServers: String,
    @Value("\${KAFKA_SECURITY_PROTOCOL:SASL_SSL}") private val kafkaSecurityProtocol: String,
    @Value("\${KAFKA_AUTO_OFFSET_RESET:none}") private val kafkaAutoOffsetReset: String,
    @Value("\${SERVICEUSER_USERNAME}") private val serviceuserUsername: String,
    @Value("\${SERVICEUSER_PASSWORD}") private val serviceuserPassword: String,
) {
    fun commonConfig(): Map<String, String> {
        return mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${serviceuserUsername}\" password=\"${serviceuserPassword}\";",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        )
    }

    fun skapSykmeldingKafkaConsumerConfig(): Map<String, Serializable> {

        val config = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "flex-reisetilskudd-backend-consumer",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JacksonKafkaDeserializer::class.java,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1"
        ) + commonConfig()

        return config
    }

    @Bean
    fun sykmeldingKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = DefaultKafkaConsumerFactory(skapSykmeldingKafkaConsumerConfig())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.containerProperties.authorizationExceptionRetryInterval = Duration.ofSeconds(2)

        return factory
    }
}
