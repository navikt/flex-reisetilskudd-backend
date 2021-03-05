package no.nav.helse.flex.metrikk

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.springframework.stereotype.Component

@Component
class Metrikk(val registry: MeterRegistry) {

    fun utelattSykmeldingFraSoknadOpprettelse(grunn: String, tags: Set<Tag> = emptySet()) {
        val tagsListe = ArrayList<Tag>()
        tagsListe.addAll(tags)
        tagsListe.add(Tag.of("grunn", grunn))
        registry.counter("sykmelding_utelatt_opprettelse_counter", tagsListe).increment()
    }

    val mottattSykmelding = registry.counter("mottatt_sykmelding_counter")
    val sendtReisetilskudd = registry.counter("sendt_reisetilskudd_counter")
    val avbruttReisetilskudd = registry.counter("avbrutt_reisetilskudd_counter")
    val gjenapnetReisetilskudd = registry.counter("gjenapnet_reisetilskudd_counter")
    val apneReisetilskudd = registry.counter("aapne_reisetilskudd_counter")
    val sendbartReisetilskudd = registry.counter("sendbar_reisetilskudd_counter")
    val sykmeldingHeltUtafor = registry.counter("sykmelding_helt_utafor_arbeidsgiverperioden")
    val sykmeldingDelvisUtafor = registry.counter("sykmelding_delvis_utafor_arbeidsgiverperioden")
}
