package no.nav.syfo.reisetilskudd.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.reisetilskudd.domain.KvitteringJson
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate

fun DatabaseInterface.hentReisetilskudd(fnr: String): List<ReisetilskuddDTO> {
    connection.use { return it.hentReisetilskudd(fnr) }
}

fun DatabaseInterface.lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    connection.use { it.lagreReisetilskudd(reisetilskuddDTO) }
}

fun DatabaseInterface.lagreKvittering(kvitteringJson: KvitteringJson) {
    connection.use { it.lagreKvittering(kvitteringJson) }
}

fun DatabaseInterface.eierReisetilskudd(fnr: String, id: String): Boolean {
    connection.use { return it.eierReisetilskudd(fnr, id) }
}

fun DatabaseInterface.eierKvittering(fnr: String, id: String): Boolean {
    connection.use { return it.eierKvittering(fnr, id) }
}

fun DatabaseInterface.slettKvittering(id: String) {
    connection.use{ it.slettKvittering(id) }
}

private fun Connection.hentReisetilskudd(fnr: String): List<ReisetilskuddDTO> =
    this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE fnr = ?
        """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toReisetilskuddDTO() }
    }

private fun Connection.eierReisetilskudd(fnr: String, id: String): Boolean =
    this.prepareStatement(
        """
            SELECT exists(select 1 FROM reisetilskudd
            WHERE fnr = ? AND reisetilskudd_id = ?)
    """
    ).use {
        it.setString(1, fnr)
        it.setString(2, id)
        it.executeQuery().next()
    }

private fun Connection.lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    this.prepareStatement(
        """
           INSERT INTO reisetilskudd 
           (reisetilskudd_id, sykmelding_id, fnr, aktor_id, fom, tom, arbeidsgiver_orgnummer, arbeidsgiver_navn) 
           VALUES(?,?,?,'01010112345',?,?,?,?)
        """
    ).use {
        it.setString(1, reisetilskuddDTO.reisetilskuddId)
        it.setString(2, reisetilskuddDTO.sykmeldingId)
        it.setString(3, reisetilskuddDTO.fnr)
        it.setDate(4, Date.valueOf(reisetilskuddDTO.fom))
        it.setDate(5, Date.valueOf(reisetilskuddDTO.tom))
        it.setString(6, reisetilskuddDTO.orgNummer)
        it.setString(7, reisetilskuddDTO.orgNavn)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.lagreKvittering(kvitteringJson: KvitteringJson) {
    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, belop, fom, tom, transportmiddel)
                VALUES(?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, kvitteringJson.kvitteringId)
        it.setString(2, kvitteringJson.reisetilskuddId)
        it.setDouble(3, kvitteringJson.belop)
        it.setDate(4, Date.valueOf(kvitteringJson.fom))
        it.setDate(5, if (kvitteringJson.tom != null) Date.valueOf(kvitteringJson.tom) else null)
        it.setString(6, kvitteringJson.transportmiddel.name)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.slettKvittering(id: String) {
    this.prepareStatement(
        """
            DELETE FROM kvitteringer
            WHERE kvittering_id = ?
        """).use {
        it.setString(1, id)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.eierKvittering(fnr: String, id: String): Boolean =
    this.prepareStatement(
        """
            SELECT exists(SELECT 1 FROM kvitteringer kv, reisetilskudd re
            WHERE kv.kvittering_id = ? 
            AND kv.reisetilskudd_id = re.reisetilskudd_id 
            AND re.fnr = ?)
        """).use {
        it.setString(1, id)
        it.setString(2, fnr)
        it.executeQuery().next()
    }

fun ResultSet.toReisetilskuddDTO(): ReisetilskuddDTO {
    return ReisetilskuddDTO(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr"),
        reisetilskuddId = getString("reisetilskudd_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        orgNummer = getString("arbeidsgiver_orgnummer"),
        orgNavn = getString("arbeidsgiver_navn")
    )
}
