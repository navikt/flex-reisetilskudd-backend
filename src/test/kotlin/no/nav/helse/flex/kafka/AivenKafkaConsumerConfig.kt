package no.nav.helse.flex.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AivenKafkaConsumerConfig(
    private val aivenKafkaConfig: AivenKafkaConfig,
) {

    @Bean
    fun reisetilskuddKafkaConsumer() = KafkaConsumer<String, String>(consumerConfig())

    private fun consumerConfig() = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to "testing-group-id",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",

    ) + aivenKafkaConfig.commonConfig()
}
