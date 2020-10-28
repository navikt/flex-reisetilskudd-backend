package no.nav.helse.flex.kafka

import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import java.util.UUID

data class SykmeldingMessage(
    val sykmelding: EnkelSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)

fun SykmeldingMessage.toReisetilskuddDTO(): List<Reisetilskudd> =
    this.sykmelding.sykmeldingsperioder.filter {
        it.reisetilskudd
    }.map {
        Reisetilskudd(
            status = ReisetilskuddStatus.ÅPEN,
            oppfølgende = false,
            reisetilskuddId = UUID.randomUUID().toString(),
            sykmeldingId = this.sykmelding.id,
            fnr = this.kafkaMetadata.fnr,
            fom = it.fom,
            tom = it.tom,
            orgNavn = this.event.arbeidsgiver?.orgNavn,
            orgNummer = this.event.arbeidsgiver?.orgnummer
        )
    }
