package no.nav.syfo.reisetilskudd

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.SykmeldingMessage
import no.nav.syfo.kafka.toReisetilskuddDTO
import no.nav.syfo.reisetilskudd.db.eierKvittering
import no.nav.syfo.reisetilskudd.db.eierReisetilskudd
import no.nav.syfo.reisetilskudd.db.hentReisetilskudd
import no.nav.syfo.reisetilskudd.db.lagreKvittering
import no.nav.syfo.reisetilskudd.db.lagreReisetilskudd
import no.nav.syfo.reisetilskudd.db.oppdaterReisetilskudd
import no.nav.syfo.reisetilskudd.db.slettKvittering
import no.nav.syfo.reisetilskudd.domain.KvitteringDTO
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO

class ReisetilskuddService(private val database: DatabaseInterface) {

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

    fun lagreKvittering(kvitteringDTO: KvitteringDTO) {
        database.lagreKvittering(kvitteringDTO)
    }

    fun eierReisetilskudd(fnr: String, id: String) =
        database.eierReisetilskudd(fnr, id)

    fun eierKvittering(fnr: String, id: String) =
        database.eierKvittering(fnr, id)

    fun slettKvittering(id: String) =
        database.slettKvittering(id)
}
