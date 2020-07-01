package no.nav.syfo.reisetilskudd

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.reisetilskudd.db.hentReisetilskudd


class ReisetilskuddService (private val database: DatabaseInterface) {

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

}