package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.application.metrics.AVBRUTT_REISETILSKUDD
import no.nav.helse.flex.application.metrics.GJENÅPNET_REISETILSKUDD
import no.nav.helse.flex.application.metrics.SENDT_REISETILSKUDD
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.reisetilskuddPerioder
import no.nav.helse.flex.kafka.splittLangeSykmeldingperioder
import no.nav.helse.flex.kafka.tidligstePeriodeFoerst
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.Transactions
import no.nav.helse.flex.reisetilskudd.transactions.avbrytReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.gjenapneReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.lagreKvittering
import no.nav.helse.flex.reisetilskudd.transactions.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.oppdaterReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.sendReisetilskudd
import no.nav.helse.flex.reisetilskudd.transactions.slettKvittering
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import java.time.Instant
import java.util.*

class ReisetilskuddService(
    database: DatabaseInterface,
    aivenKafkaConfig: AivenKafkaConfig
) : Transactions(
    database,
    aivenKafkaConfig
) {
    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        sykmeldingMessage.runCatching {
            this.reisetilskuddPerioder()
                .splittLangeSykmeldingperioder()
                .tidligstePeriodeFoerst()
                .mapIndexed { idx, periode ->
                    Reisetilskudd(
                        reisetilskuddId = UUID.nameUUIDFromBytes("${sykmeldingMessage.sykmelding.id}-${periode.fom}-${periode.tom}".toByteArray()).toString(),
                        sykmeldingId = sykmeldingMessage.sykmelding.id,
                        status = reisetilskuddStatus(periode.fom, periode.tom),
                        oppfølgende = idx > 0,
                        fnr = sykmeldingMessage.kafkaMetadata.fnr,
                        fom = periode.fom,
                        tom = periode.tom,
                        orgNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
                        orgNummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer,
                        opprettet = Instant.now()
                    )
                }
                .let { reisetilskuddene ->
                    transaction {
                        reisetilskuddene.forEach { reisetilskudd ->
                            this.lagreReisetilskudd(reisetilskudd)
                            log.info("Opprettet reisetilskudd ${reisetilskudd.reisetilskuddId}")
                        }
                    }
                }
        }.onSuccess {
            log.info("Sykmelding ${sykmeldingMessage.sykmelding.id} ferdig behandlet")
        }.onFailure { ex ->
            log.error("Uhåndtert feil ved behandleSykmelding ${sykmeldingMessage.sykmelding.id}", ex)
            throw ex
        }
    }

    fun oppdaterReisetilskudd(reisetilskudd: Reisetilskudd): Reisetilskudd {
        transaction {
            this.oppdaterReisetilskudd(reisetilskudd)
        }
        return hentReisetilskudd(reisetilskuddId = reisetilskudd.reisetilskuddId)!!
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String) {
        transaction {
            this.sendReisetilskudd(fnr, reisetilskuddId)
        }

        SENDT_REISETILSKUDD.inc()
        log.info("Sendte reisetilskudd $reisetilskuddId")
    }

    fun avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
        transaction {
            this.avbrytReisetilskudd(fnr, reisetilskuddId)
        }

        AVBRUTT_REISETILSKUDD.inc()
        log.info("Avbrøt reisetilskudd $reisetilskuddId")
    }

    fun gjenapneReisetilskudd(fnr: String, reisetilskuddId: String) {
        transaction {
            this.gjenapneReisetilskudd(fnr, reisetilskuddId)
        }

        GJENÅPNET_REISETILSKUDD.inc()
        log.info("Gjenåpnet reisetilskudd $reisetilskuddId")
    }

    fun lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
        val kvitteringId = UUID.randomUUID().toString()
        transaction {
            this.lagreKvittering(kvittering, reisetilskuddId, kvitteringId)
        }
        return hentKvittering(kvitteringId)!!
    }

    fun slettKvittering(kvitteringId: String, reisetilskuddId: String) {
        transaction {
            this.slettKvittering(kvitteringId, reisetilskuddId)
        }
    }
}
