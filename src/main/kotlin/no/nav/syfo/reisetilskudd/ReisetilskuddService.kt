package no.nav.syfo.reisetilskudd

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.reisetilskudd.domain.KvitteringJson
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO
import no.nav.syfo.kafka.SykmeldingMessage
import no.nav.syfo.kafka.toReisetilskuddDTO
import no.nav.syfo.reisetilskudd.db.*

class ReisetilskuddService(private val database: DatabaseInterface) {

    fun behandleSykmelding(melding: SykmeldingMessage) {
        melding.toReisetilskuddDTO().forEach {
            lagreReisetilskudd(it)
        }
    }

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

    fun lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
        database.lagreReisetilskudd(reisetilskuddDTO)
    }

    fun lagreKvittering(kvitteringJson: KvitteringJson) {
        database.lagreKvittering(kvitteringJson)
    }

    fun eierReisetilskudd(fnr: String, id: String) =
        database.eierReisetilskudd(fnr, id)

    fun eierKvittering(fnr: String, id: String) =
        database.eierKvittering(fnr, id)

    fun slettKvittering(id: String) =
        database.slettKvittering(id)
}
