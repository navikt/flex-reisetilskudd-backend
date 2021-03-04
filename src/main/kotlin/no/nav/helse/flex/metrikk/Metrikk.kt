package no.nav.helse.flex.metrikk

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class Metrikk(registry: MeterRegistry) {


    val tombstoneSykmelding = registry.counter("tombstone_sykmelding_counter")
    val mottattSykmelding = registry.counter("mottatt_sykmelding_counter")
    val sykmeldingIkkeReisetilskudd = registry.counter("YYYY")
    val sykmeldingAllePerioderIkkeReisetilskudd = registry.counter("YYYY")
    val sykmeldingGradertPeriode = registry.counter("YYYY")
    val sykmeldingMismatchAvTypeOgReisetilskuddFlagg = registry.counter("YYYY")
    val sykmelding = registry.counter("YYYY")

    val SENDT_REISETILSKUDD = registry.counter("sendt_reisetilskudd_counter")

    val AVBRUTT_REISETILSKUDD = registry.counter("avbrutt_reisetilskudd_counter")

    val GJENÅPNET_REISETILSKUDD = registry.counter("gjenapnet_reisetilskudd_counter")

    val ÅPNE_REISETILSKUDD = registry.counter("reisetilskudd_counter")

    val SENDBARE_REISETILSKUDD = registry.counter("sendbar_reisetilskudd_counter")
}
