package no.nav.syfo.kafka

import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO
import java.util.UUID

data class SykmeldingMessage(
    val sykmelding: EnkelSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)

fun SykmeldingMessage.toReisetilskuddDTO(): List<ReisetilskuddDTO> =
    this.sykmelding.sykmeldingsperioder.filter {
        it.reisetilskudd
    }.map {
        ReisetilskuddDTO(
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = this.sykmelding.id,
            fnr = this.kafkaMetadata.fnr,
            fom = it.fom,
            tom = it.tom,
            orgNavn = this.event.arbeidsgiver?.orgNavn,
            orgNummer = this.event.arbeidsgiver?.orgnummer
        )
    }
