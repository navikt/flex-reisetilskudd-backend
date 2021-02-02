package no.nav.helse.flex.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.application.objectMapper
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import org.apache.kafka.common.serialization.Deserializer

class JacksonKafkaDeserializer : Deserializer<SykmeldingMessage> {

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {}
    override fun deserialize(topic: String?, data: ByteArray): SykmeldingMessage {
        return objectMapper.readValue(data)
    }

    override fun close() {}
}

class JacksonKafkaReisetilskuddDeserializer : Deserializer<Reisetilskudd> {

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {}
    override fun deserialize(topic: String?, data: ByteArray): Reisetilskudd {
        return objectMapper.readValue(data)
    }

    override fun close() {}
}
