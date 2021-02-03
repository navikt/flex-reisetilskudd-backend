package no.nav.helse.flex.application.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class Metrikk(registry: MeterRegistry) {

    val SENDT_REISETILSKUDD = registry.counter("sendt_reisetilskudd_counter")

    val AVBRUTT_REISETILSKUDD = registry.counter("avbrutt_reisetilskudd_counter")

    val GJENÅPNET_REISETILSKUDD = registry.counter("gjenapnet_reisetilskudd_counter")

    val ÅPNE_REISETILSKUDD = registry.counter("reisetilskudd_counter")

    val SENDBARE_REISETILSKUDD = registry.counter("sendbar_reisetilskudd_counter")
}
