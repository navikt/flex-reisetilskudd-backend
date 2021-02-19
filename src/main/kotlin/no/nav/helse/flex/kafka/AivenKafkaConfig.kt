package no.nav.helse.flex.kafka

import no.nav.helse.flex.domain.ReisetilskuddSoknad
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AivenKafkaConfig(
    @Value("\${KAFKA_BROKERS}") private val bootstrapServers: String,
    @Value("\${KAFKA_SECURITY_PROTOCOL:SSL}") private val securityProtocol: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}") private val sslTruststoreLocation: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}") private val sslTruststorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}") private val sslKeystoreLocation: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}") private val sslKeystorePassword: String,
) {
    private val JAVA_KEYSTORE = "JKS"
    private val PKCS12 = "PKCS12"

    @Bean
    fun producer() = KafkaProducer<String, ReisetilskuddSoknad>(producerConfig())

    private fun producerConfig() = mapOf(
        KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java,
        ACKS_CONFIG to "all",
        RETRIES_CONFIG to 10,
        RETRY_BACKOFF_MS_CONFIG to 100
    ) + commonConfig()

    fun commonConfig() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
    ) + securityConfig()

    private fun securityConfig() = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to securityProtocol,
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to sslTruststoreLocation,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to sslTruststorePassword,
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to sslKeystoreLocation,
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to sslKeystorePassword,
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to sslKeystorePassword,
    )
}

const val reisetilskuddTopic = "flex." + "aapen-reisetilskudd"
