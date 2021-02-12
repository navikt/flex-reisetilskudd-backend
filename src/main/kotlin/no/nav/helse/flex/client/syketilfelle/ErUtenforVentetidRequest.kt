package no.nav.helse.flex.client.syketilfelle

import no.nav.helse.flex.domain.syketilfelle.Tilleggsopplysninger
import no.nav.helse.flex.kafka.SykmeldingMessage

data class ErUtenforVentetidRequest(
    val tilleggsopplysninger: Tilleggsopplysninger? = null,
    val sykmeldingKafkaMessage: SykmeldingMessage? = null
)
