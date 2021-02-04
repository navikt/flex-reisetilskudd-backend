package no.nav.helse.flex.utils

import no.nav.helse.flex.kafka.Arbeidssituasjon
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.model.sykmelding.model.BehandlerDTO
import no.nav.syfo.model.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun getSykmeldingDto(
    sykmeldingId: String = UUID.randomUUID().toString(),
    fom: LocalDate = LocalDate.of(2020, 2, 1),
    tom: LocalDate = LocalDate.of(2020, 2, 15)
): EnkelSykmelding {
    return EnkelSykmelding(
        id = sykmeldingId,
        sykmeldingsperioder = listOf(
            SykmeldingsperiodeDTO(
                fom = fom,
                tom = tom,
                type = PeriodetypeDTO.REISETILSKUDD,
                reisetilskudd = true,
                aktivitetIkkeMulig = null,
                behandlingsdager = null,
                gradert = null,
                innspillTilArbeidsgiver = null
            )
        ),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        arbeidsgiver = ArbeidsgiverDTO(null, null),
        syketilfelleStartDato = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        behandler = BehandlerDTO(
            fornavn = "Lege",
            mellomnavn = null,
            etternavn = "Legesen",
            aktoerId = "aktor",
            fnr = "fnr",
            hpr = null,
            her = null,
            adresse = AdresseDTO(
                gate = null,
                postnummer = null,
                kommune = null,
                postboks = null,
                land = null
            ),
            tlf = null
        ),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        legekontorOrgnummer = null,
        meldingTilArbeidsgiver = null,
        navnFastlege = null,
        tiltakArbeidsplassen = null,
        prognose = null,
        papirsykmelding = false
    )
}

fun skapSykmeldingStatusKafkaMessageDTO(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.ARBEIDSTAKER,
    statusEvent: String = STATUS_SENDT,
    fnr: String,
    timestamp: OffsetDateTime = OffsetDateTime.now(),
    arbeidsgiver: ArbeidsgiverStatusDTO? = null,
    sykmeldingId: String = UUID.randomUUID().toString()
): SykmeldingStatusKafkaMessageDTO {
    return SykmeldingStatusKafkaMessageDTO(
        event = SykmeldingStatusKafkaEventDTO(
            statusEvent = statusEvent,
            sykmeldingId = sykmeldingId,
            arbeidsgiver = arbeidsgiver,
            timestamp = timestamp,
            sporsmals = listOf(
                SporsmalOgSvarDTO(
                    tekst = "Hva jobber du som?",
                    shortName = ShortNameDTO.ARBEIDSSITUASJON,
                    svartype = SvartypeDTO.ARBEIDSSITUASJON,
                    svar = arbeidssituasjon.name
                )
            )
        ),
        kafkaMetadata = KafkaMetadataDTO(
            sykmeldingId = sykmeldingId,
            timestamp = timestamp,
            source = "Test",
            fnr = fnr
        )
    )
}

fun lagSykmeldingMessage(
    fnr: String = "fnr",
    sykmeldingsperioder: List<SykmeldingsperiodeDTO> = listOf(
        SykmeldingsperiodeDTO(
            fom = LocalDate.now().minusDays(10),
            tom = LocalDate.now(),
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true,
            aktivitetIkkeMulig = null,
            behandlingsdager = null,
            gradert = null,
            innspillTilArbeidsgiver = null
        )
    )
): SykmeldingMessage {
    val sykmelding = getSykmeldingDto().copy(
        sykmeldingsperioder = sykmeldingsperioder
    )
    val kafka = skapSykmeldingStatusKafkaMessageDTO(
        fnr = fnr,
        sykmeldingId = sykmelding.id
    )
    val melding = SykmeldingMessage(
        sykmelding = sykmelding,
        kafkaMetadata = kafka.kafkaMetadata,
        event = kafka.event
    )
    return melding
}
