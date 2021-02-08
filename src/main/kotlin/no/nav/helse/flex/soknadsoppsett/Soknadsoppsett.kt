package no.nav.helse.flex.soknadsoppsett

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.KriterieForVisningAvUndersporsmal.JA
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.reisetilskudd.reisetilskuddStatus
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import java.time.Instant
import java.util.*

fun skapReisetilskuddsoknad(periode: SykmeldingsperiodeDTO, sykmeldingMessage: SykmeldingMessage): ReisetilskuddSoknad {
    return ReisetilskuddSoknad(
        id = UUID.randomUUID().toString(),
        sykmeldingId = sykmeldingMessage.sykmelding.id,
        status = reisetilskuddStatus(periode.fom, periode.tom),
        fnr = sykmeldingMessage.kafkaMetadata.fnr,
        fom = periode.fom,
        tom = periode.tom,
        arbeidsgiverNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
        arbeidsgiverOrgnummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer,
        opprettet = Instant.now(),
        endret = Instant.now(),
        sporsmal = listOf(
            Sporsmal(
                id = UUID.randomUUID().toString(),
                tag = ANSVARSERKLARING,
                svartype = JA_NEI,
                overskrift = "Vi stoler på deg",
                undertekst = "Jeg vet at jeg kan miste retten til sykepenger hvis jeg ikke har gitt riktige opplysninger. Jeg vet også at jeg må betale tilbake hvis jeg har gitt feil opplysninger eller latt være å informere.",
                sporsmalstekst = "Jeg , [navn], bekrefter at jeg vil gi riktige og fullstendige opplysninger.", // TODO hente navn fra PDL? KAn vi ha <strong> her?
            ),
            Sporsmal(
                id = UUID.randomUUID().toString(),
                tag = TRANSPORT_TIL_DAGLIG,
                svartype = JA_NEI,
                overskrift = "Transport til daglig",
                sporsmalstekst = "bruker du vanligvis bil eller offentlig transport til og fra arbeidsplassen?",
                kriterieForVisningAvUndersporsmal = JA,
                undersporsmal = listOf(
                    Sporsmal(
                        id = UUID.randomUUID().toString(),
                        tag = TYPE_TRANSPORT,
                        svartype = CHECKBOX,
                        sporsmalstekst = "Hva slags type transport bruker du?", // TODO enda flere underspørsål
                    )
                )
            ),
            Sporsmal(
                id = UUID.randomUUID().toString(),
                tag = BIL,
                svartype = JA_NEI,
                overskrift = "Reise med bil",
                sporsmalstekst = "Reiser du med bil til og fra jobben mellom 1. og 30. juni 2020?", // TODO tekst fra datoer
                kriterieForVisningAvUndersporsmal = JA,
                undersporsmal = listOf(
                    Sporsmal(
                        id = UUID.randomUUID().toString(),
                        tag = BIL_DATOER,
                        svartype = DATOER,
                        sporsmalstekst = "Hvilke dager reiste du med bil`", // TODO min max etc
                    )
                )
            ),
            Sporsmal(
                id = UUID.randomUUID().toString(),
                tag = KVITTERINGER, // Frontend håndterer at legg til kvittering vises og bruker egne endepunkter for det?
                svartype = IKKE_RELEVANT,
                overskrift = "Kvitteringer",
                sporsmalstekst = "Last opp kvitteringer for reiser til og fra jobben mellom 1. og 30. juni 2021.", // TODO tekst fra datoer
            ),
            Sporsmal(
                id = UUID.randomUUID().toString(),
                tag = UTBETALING,
                svartype = JA_NEI,
                overskrift = "Utbetaling",
                sporsmalstekst = "Legger arbeidsgiveren din ut for reisene?",
            )
        ).sortedBy { it.tag }
    )
}
