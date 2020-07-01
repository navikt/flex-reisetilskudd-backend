package no.nav.syfo.reisetilskudd.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet

fun DatabaseInterface.hentReisetilskudd(fnr: String) : List<ReisetilskuddModel> {
    connection.use { connection -> return connection.hentReisetilskudd(fnr) }
}

private fun Connection.hentReisetilskudd(fnr: String) : List<ReisetilskuddModel> =
    this.prepareStatement(
        """
            SELECT * FROM melding
            WHERE fnr = ?
        """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList {toReisetilskuddModel() }
    }

fun ResultSet.toReisetilskuddModel () : ReisetilskuddModel{
    return ReisetilskuddModel(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr")
    )
}