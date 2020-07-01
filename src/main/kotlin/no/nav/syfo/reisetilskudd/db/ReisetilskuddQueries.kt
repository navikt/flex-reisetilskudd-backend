package no.nav.syfo.reisetilskudd.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import java.sql.Connection
import java.sql.ResultSet

fun DatabaseInterface.hentReisetilskudd(fnr: String): List<ReisetilskuddModel> {
    connection.use { return it.hentReisetilskudd(fnr) }
}

fun DatabaseInterface.lagreReisetilskudd(id: String) {
    connection.use { it.lagreReisetilskudd(id) }
}

private fun Connection.hentReisetilskudd(fnr: String): List<ReisetilskuddModel> =
    this.prepareStatement(
        """
            SELECT * FROM melding
            WHERE fnr = ?
        """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toReisetilskuddModel() }
    }

private fun Connection.lagreReisetilskudd(id: String) {
    this.prepareStatement(
        """
           INSERT INTO melding 
           (sykmelding_id, fnr) 
           VALUES(?,'01010112345')
        """
    ).use {
        it.setString(1, id)
        it.executeUpdate()
    }
    this.commit()
}

fun ResultSet.toReisetilskuddModel(): ReisetilskuddModel {
    return ReisetilskuddModel(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr")
    )
}
