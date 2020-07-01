package no.nav.syfo.reisetilskudd

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.reisetilskudd.db.hentReisetilskudd
import no.nav.syfo.reisetilskudd.db.lagreReisetilskudd

class ReisetilskuddService(private val database: DatabaseInterface) {

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

    fun lagreReisetilskudd(id: String) {
        database.lagreReisetilskudd(id)
    }
}
