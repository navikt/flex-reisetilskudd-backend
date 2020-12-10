package no.nav.helse.flex.kafka

import no.nav.helse.flex.Environment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

private fun commonConfig(env: Environment): Map<String, String> {
    return mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBootstrapServers,
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to env.kafkaSecurityProtocol,
        SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.serviceuserUsername}\" password=\"${env.serviceuserPassword}\";",
        SaslConfigs.SASL_MECHANISM to "PLAIN"
    )
}

fun skapSykmeldingKafkaConsumer(env: Environment): KafkaConsumer<String, SykmeldingMessage?> {

    val config = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to "flex-reisetilskudd-backend-consumer",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to env.kafkaAutoOffsetReset,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JacksonKafkaDeserializer::class.java,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1"
    ) + commonConfig(env)

    return KafkaConsumer(config)
}
