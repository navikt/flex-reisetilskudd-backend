package no.nav.helse.flex.kafka

import no.nav.syfo.model.sykmelding.kafka.EnkelSykmelding
import no.nav.syfo.model.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

data class SykmeldingMessage(
    val sykmelding: EnkelSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)

fun SykmeldingMessage.reisetilskuddPerioder(): List<SykmeldingsperiodeDTO> =
    this.sykmelding.sykmeldingsperioder.filter {
        it.reisetilskudd
        // TODO: Finnes det flere perioder som skal med, eller noen perioder som ikke skal med?
    }

fun List<SykmeldingsperiodeDTO>.splittLangeSykmeldingperioder(): List<SykmeldingsperiodeDTO> {
    val soknadsPerioder: MutableList<SykmeldingsperiodeDTO> = mutableListOf()

    // Hvis det er flere perioder så kan disse få forskjellige lengder, men ingen går over 31 dager
    this.forEach { periode ->
        val lengdePaaPeriode = ChronoUnit.DAYS.between(periode.fom, periode.tom) + 1
        val antallDeler = ceil(lengdePaaPeriode / 31.0)
        val grunnlengde = floor(lengdePaaPeriode / antallDeler)
        var rest = lengdePaaPeriode % grunnlengde

        var soknadFOM = periode.fom

        var i = 0
        while (i < antallDeler) {
            val lengde = grunnlengde.toInt() + if (rest-- > 0) 1 else 0
            val delperiode = periode.copy(fom = soknadFOM, tom = soknadFOM.plusDays((lengde - 1).toLong()))
            soknadsPerioder.add(delperiode)
            soknadFOM = delperiode.tom.plusDays(1)
            i++
        }
    }

    return soknadsPerioder
}

fun List<SykmeldingsperiodeDTO>.tidligstePeriodeFoerst(): List<SykmeldingsperiodeDTO> =
    this.sortedBy { it.fom }
