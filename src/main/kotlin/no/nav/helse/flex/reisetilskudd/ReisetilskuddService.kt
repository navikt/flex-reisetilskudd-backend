package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.SykmeldingMessage
import no.nav.helse.flex.kafka.toReisetilskuddDTO
import no.nav.helse.flex.reisetilskudd.db.* // ktlint-disable no-wildcard-imports
import no.nav.helse.flex.reisetilskudd.domain.KvitteringDTO
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ReisetilskuddService(private val database: DatabaseInterface) {

    val log: Logger = LoggerFactory.getLogger("no.nav.helse.flex.flex-reisetilskudd-backend")

    fun behandleSykmelding(melding: SykmeldingMessage) {
        melding.toReisetilskuddDTO().forEach {
            lagreReisetilskudd(it)
        }
    }

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

    fun hentReisetilskudd(fnr: String, reisetilskuddId: String) =
        database.hentReisetilskudd(fnr, reisetilskuddId)

    private fun lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
        database.lagreReisetilskudd(reisetilskuddDTO)
    }

    fun oppdaterReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
        database.oppdaterReisetilskudd(reisetilskuddDTO)
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String) =
        database.sendReisetilskudd(fnr, reisetilskuddId)

    fun lagreKvittering(kvitteringDTO: KvitteringDTO) {

        validerKvittering(kvitteringDTO)
        log.info("Vedlegg validert")

        log.info("Legger ettersending til prosessering")
        database.lagreKvittering(kvitteringDTO)
    }

    fun eierReisetilskudd(fnr: String, id: String) =
        database.eierReisetilskudd(fnr, id)

    fun eierKvittering(fnr: String, id: String) =
        database.eierKvittering(fnr, id)

    fun slettKvittering(id: String): Boolean {
        return try {
            database.slettKvittering(id)
            return true
        } catch (ex: Exception) {
            log.error("Kan ikke slette kvittering med id $id. Grunnen er: ${ex.cause}")
            false
        }
    }

}
