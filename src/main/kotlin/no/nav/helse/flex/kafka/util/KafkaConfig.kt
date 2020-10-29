package no.nav.helse.flex.kafka.util

import no.nav.helse.flex.Environment
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.Integer.MAX_VALUE

class KafkaConfig(val environment: Environment) {
    private val JAVA_KEYSTORE = "jks"
    private val PKCS12 = "PKCS12"

    fun producer() = KafkaProducer<String, Reisetilskudd>(producerConfig())

    private fun producerConfig() = mapOf(
        KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java,
        ACKS_CONFIG to "all",
        RETRIES_CONFIG to MAX_VALUE,
        RETRY_BACKOFF_MS_CONFIG to 100
    ) + commonConfig()

    private fun commonConfig() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to environment.bootstrapServers
    ) + securityConfig(environment)

    private fun securityConfig(environment: Environment) = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
        SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to environment.sslTruststoreLocation,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to environment.sslTruststorePassword,
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to environment.sslKeystoreLocation,
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to environment.sslKeystorePassword,
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to environment.sslKeystorePassword
    )

    companion object Topics {
        val topic = "aapen-flex-reisetilskudd"
    }
}
