package no.nav.syfo.kafka.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import org.apache.kafka.common.serialization.Deserializer

class JacksonKafkaDeserializer : Deserializer<SykmeldingKafkaMessage> {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {}
    override fun deserialize(topic: String?, data: ByteArray): EnkelSykmelding {
        return objectMapper.readValue<SykmeldingKafkaMessage>(data).sykmelding
    }
    override fun close() {}
}

data class SykmeldingKafkaMessage(val sykmelding: EnkelSykmelding)
