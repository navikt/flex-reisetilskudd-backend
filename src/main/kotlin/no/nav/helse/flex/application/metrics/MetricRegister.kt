package no.nav.helse.flex.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = ""

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val SENDT_REISETILSKUDD: Counter = Counter.build()
    .labelNames(METRICS_NS)
    .name("sendt_reisetilskudd_søknad_counter")
    .help("Antall reisetilskudd søknader som er sendt")
    .register()

val AVBRUTT_REISETILSKUDD: Counter = Counter.build()
    .labelNames(METRICS_NS)
    .name("avbrutt_reisetilskudd_søknad_counter")
    .help("Antall reisetilskudd søknader som er avbrutt")
    .register()

val GJENÅPNET_REISETILSKUDD: Counter = Counter.build()
    .labelNames(METRICS_NS)
    .name("gjenåpnet_reisetilskudd_søknad_counter")
    .help("Antall reisetilskudd søknader som er gjenåpnet")
    .register()

val ÅPNE_REISETILSKUDD: Counter = Counter.build()
    .labelNames(METRICS_NS)
    .name("reisetilskudd_søknad_åpnet_counter")
    .help("Antall åpnet reisetilskudd søknader")
    .register()

val SENDBARE_REISETILSKUDD: Counter = Counter.build()
    .labelNames(METRICS_NS)
    .name("sendbar_reisetilskudd_søknad_counter")
    .help("Antall reisetilskudd søknader som er innsendingsklare")
    .register()
