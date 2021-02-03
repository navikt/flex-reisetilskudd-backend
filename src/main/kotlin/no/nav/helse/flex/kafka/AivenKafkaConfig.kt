package no.nav.helse.flex.kafka

import no.nav.helse.flex.Environment
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AivenKafkaConfig(val environment: Environment) {
    private val JAVA_KEYSTORE = "JKS"
    private val PKCS12 = "PKCS12"

    @Bean
    fun producer() = KafkaProducer<String, Reisetilskudd>(producerConfig())
    fun consumer() = KafkaConsumer<String, Reisetilskudd>(consumerConfig())

    private fun producerConfig() = mapOf(
        KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java,
        ACKS_CONFIG to "all",
        RETRIES_CONFIG to 10,
        RETRY_BACKOFF_MS_CONFIG to 100
    ) + commonConfig()

    private fun consumerConfig() = mapOf(
        GROUP_ID_CONFIG to environment.applicationName,
        ENABLE_AUTO_COMMIT_CONFIG to false,
        KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        VALUE_DESERIALIZER_CLASS_CONFIG to JacksonKafkaDeserializer::class.java,
        AUTO_OFFSET_RESET_CONFIG to environment.kafkaAutoOffsetReset
    ) + commonConfig()

    private fun commonConfig() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to environment.bootstrapServers()
    ) + securityConfig(environment)

    private fun securityConfig(environment: Environment) = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to environment.securityProtocol(),
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to environment.sslTruststoreLocation(),
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to environment.sslTruststorePassword(),
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to environment.sslKeystoreLocation(),
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to environment.sslKeystorePassword(),
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to environment.sslKeystorePassword()
    )

    companion object Topics {
        val topic = "flex." + "aapen-reisetilskudd"
    }
}
