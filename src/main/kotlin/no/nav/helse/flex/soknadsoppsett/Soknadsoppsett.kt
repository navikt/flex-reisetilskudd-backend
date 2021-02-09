package no.nav.helse.flex.soknadsoppsett

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.KriterieForVisningAvUndersporsmal.CHECKED
import no.nav.helse.flex.domain.KriterieForVisningAvUndersporsmal.JA
import no.nav.helse.flex.domain.Svartype.*
import no.nav.helse.flex.domain.Tag.*
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.reisetilskudd.reisetilskuddStatus
import no.nav.helse.flex.soknadsoppsett.DatoFormaterer.formatterPeriode
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.util.*

fun skapReisetilskuddsoknad(
    periode: SykmeldingsperiodeDTO,
    sykmeldingMessage: SykmeldingMessage,
    navn: String
): ReisetilskuddSoknad {
    val fom = periode.fom
    val tom = periode.tom
    val formattertPeriode = formatterPeriode(
        fom = fom,
        tom = tom
    )
    return ReisetilskuddSoknad(
        sykmeldingId = sykmeldingMessage.sykmelding.id,
        status = reisetilskuddStatus(fom, tom),
        fnr = sykmeldingMessage.kafkaMetadata.fnr,
        fom = fom,
        tom = tom,
        arbeidsgiverNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
        arbeidsgiverOrgnummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer,
        opprettet = Instant.now(),
        endret = Instant.now(),
        sporsmal = listOf(
            Sporsmal(
                tag = ANSVARSERKLARING,
                svartype = JA_NEI,
                overskrift = "Vi stoler på deg",
                undertekst = "Jeg vet at jeg kan miste retten til sykepenger hvis jeg ikke har gitt riktige opplysninger. Jeg vet også at jeg må betale tilbake hvis jeg har gitt feil opplysninger eller latt være å informere.",
                sporsmalstekst = "Jeg, <strong>$navn</strong>, bekrefter at jeg vil gi riktige og fullstendige opplysninger.",
            ),
            Sporsmal(
                tag = TRANSPORT_TIL_DAGLIG,
                svartype = JA_NEI,
                overskrift = "Transport til daglig",
                sporsmalstekst = "bruker du vanligvis bil eller offentlig transport til og fra arbeidsplassen?",
                kriterieForVisningAvUndersporsmal = JA,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = TYPE_TRANSPORT,
                        svartype = CHECKBOX_GRUPPE,
                        sporsmalstekst = "Hva slags type transport bruker du?",
                        undersporsmal = listOf(
                            Sporsmal(
                                tag = OFFENTLIG_TRANSPORT_TIL_DAGLIG,
                                sporsmalstekst = "Offentlig transport",
                                svartype = CHECKBOX,
                                kriterieForVisningAvUndersporsmal = CHECKED,
                                undersporsmal = listOf(
                                    Sporsmal(
                                        tag = OFFENTLIG_TRANSPORT_BELOP,
                                        sporsmalstekst = "Hvor mye betaler du vanligvis i måneden for offentlig transport?",
                                        svartype = BELOP,
                                    )
                                )
                            ),
                            Sporsmal(
                                tag = BIL_TIL_DAGLIG,
                                sporsmalstekst = "Bil",
                                svartype = CHECKBOX,
                                kriterieForVisningAvUndersporsmal = CHECKED,
                                undersporsmal = listOf(
                                    Sporsmal(
                                        tag = KM_HJEM_JOBB,
                                        sporsmalstekst = "Hvor mange km er det fra hjemmet ditt til jobben?",
                                        svartype = KILOMETER,
                                    )
                                )
                            )
                        )

                    )
                )
            ),
            Sporsmal(
                tag = REISE_MED_BIL,
                svartype = JA_NEI,
                overskrift = "Reise med bil",
                sporsmalstekst = "Reiser du med bil til og fra jobben mellom $formattertPeriode?",
                kriterieForVisningAvUndersporsmal = JA,
                undersporsmal = listOf(
                    Sporsmal(
                        tag = BIL_DATOER,
                        svartype = DATOER,
                        min = fom.format(ISO_LOCAL_DATE),
                        max = tom.format(ISO_LOCAL_DATE),
                        sporsmalstekst = "Hvilke dager reiste du med bil",
                    )
                )
            ),
            Sporsmal(
                tag = KVITTERINGER,
                svartype = IKKE_RELEVANT,
                overskrift = "Kvitteringer",
                min = fom.format(ISO_LOCAL_DATE),
                max = tom.format(ISO_LOCAL_DATE),
                sporsmalstekst = "Last opp kvitteringer for reiser til og fra jobben mellom $formattertPeriode.",
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
