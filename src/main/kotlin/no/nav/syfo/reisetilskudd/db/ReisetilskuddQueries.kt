package no.nav.syfo.reisetilskudd.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.reisetilskudd.domain.KvitteringDTO
import no.nav.syfo.reisetilskudd.domain.ReisetilskuddDTO
import no.nav.syfo.reisetilskudd.domain.SvarDTO
import no.nav.syfo.reisetilskudd.domain.Transportmiddel
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Types.BOOLEAN
import java.sql.Types.DOUBLE
import java.time.LocalDate

fun DatabaseInterface.hentReisetilskudd(fnr: String): List<ReisetilskuddDTO> {
    connection.use { return it.hentReisetilskudd(fnr) }
}

fun DatabaseInterface.hentReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddDTO? {
    connection.use { return it.hentReisetilskudd(fnr, reisetilskuddId) }
}

fun DatabaseInterface.lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    connection.use { it.lagreReisetilskudd(reisetilskuddDTO) }
}

fun DatabaseInterface.oppdaterReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    connection.use { it.oppdaterReisetilskudd(reisetilskuddDTO) }
}

fun DatabaseInterface.lagreKvittering(kvitteringDTO: KvitteringDTO) {
    connection.use { it.lagreKvittering(kvitteringDTO) }
}

fun DatabaseInterface.eierReisetilskudd(fnr: String, id: String): Boolean {
    connection.use { return it.eierReisetilskudd(fnr, id) }
}

fun DatabaseInterface.eierKvittering(fnr: String, id: String): Boolean {
    connection.use { return it.eierKvittering(fnr, id) }
}

fun DatabaseInterface.slettKvittering(id: String) {
    connection.use { it.slettKvittering(id) }
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


private fun Connection.hentReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddDTO? =
    this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE fnr = ?
            AND reisetilskudd_id = ?
        """
    ).use {
        it.setString(1, fnr)
        it.setString(2, reisetilskuddId)
        it.executeQuery().toList { toReisetilskuddDTO() }.firstOrNull()
    }

private fun Connection.eierReisetilskudd(fnr: String, id: String): Boolean =
    this.prepareStatement(
        """
            select * FROM reisetilskudd
            WHERE fnr = ? AND reisetilskudd_id = ?
    """
    ).use {
        it.setString(1, fnr)
        it.setString(2, id)
        it.executeQuery().toList {
            getString("reisetilskudd_id")
        }.isNotEmpty()
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

private fun Connection.oppdaterReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {

    this.prepareStatement(
        """
            UPDATE reisetilskudd
            SET (utbetaling_til_arbeidsgiver, gar, sykler, egen_bil, kollektivtransport) = 
                (?, ?, ?, ?, ?)                                
            WHERE reisetilskudd_id = ?
        """
    ).use {
        it.setObject(1, reisetilskuddDTO.utbetalingTilArbeidsgiver , BOOLEAN)
        it.setObject(2, reisetilskuddDTO.går, BOOLEAN)
        it.setObject(3, reisetilskuddDTO.sykler, BOOLEAN)
        if (reisetilskuddDTO.egenBil != null) it.setDouble(4, reisetilskuddDTO.egenBil!!) else it.setNull(4, DOUBLE)
        if (reisetilskuddDTO.kollektivtransport != null) it.setDouble(5, reisetilskuddDTO.kollektivtransport!!) else it.setNull(5, DOUBLE)
        it.setString(6, reisetilskuddDTO.reisetilskuddId)
    }
    this.commit()
}

private fun Connection.lagreKvittering(kvitteringDTO: KvitteringDTO) {
    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, belop, fom, tom, transportmiddel)
                VALUES(?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, kvitteringDTO.kvitteringId)
        it.setString(2, kvitteringDTO.reisetilskuddId)
        it.setDouble(3, kvitteringDTO.belop)
        it.setDate(4, Date.valueOf(kvitteringDTO.fom))
        it.setDate(5, if (kvitteringDTO.tom != null) Date.valueOf(kvitteringDTO.tom) else null)
        it.setString(6, kvitteringDTO.transportmiddel.name)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.slettKvittering(id: String) {
    this.prepareStatement(
        """
            DELETE FROM kvitteringer
            WHERE kvittering_id = ?
        """
    ).use {
        it.setString(1, id)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.eierKvittering(fnr: String, id: String): Boolean {
    return this.prepareStatement(
        """
            SELECT * FROM kvitteringer kv, reisetilskudd re
            WHERE kv.kvittering_id = ?
            AND kv.reisetilskudd_id = re.reisetilskudd_id
            AND re.fnr = ?
        """
    ).use {
        it.setString(1, id)
        it.setString(2, fnr)
        it.executeQuery().toList {
            getString("kvittering_id")
        }.isNotEmpty()
    }
}

fun ResultSet.toReisetilskuddDTO(): ReisetilskuddDTO {
    return ReisetilskuddDTO(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr"),
        reisetilskuddId = getString("reisetilskudd_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        orgNummer = getString("arbeidsgiver_orgnummer"),
        orgNavn = getString("arbeidsgiver_navn"),
        utbetalingTilArbeidsgiver = getBooleanOrNull("utbetaling_til_arbeidsgiver"),
        går = getBooleanOrNull("gar"),
        sykler = getBooleanOrNull("sykler"),
        egenBil = getDoubleOrNull("egen_bil"),
        kollektivtransport = getDoubleOrNull("kollektivtransport")
    )
}

fun ResultSet.getBooleanOrNull(columnLabel: String): Boolean? {
    var result: Boolean? = getBoolean(columnLabel)
    if (wasNull()) {
        result = null
    }
    return result
}

fun ResultSet.getDoubleOrNull(columnLabel: String): Double? {
    var result: Double? = getDouble(columnLabel)
    if (wasNull()) {
        result = null
    }
    return result
}

fun ResultSet.toKvitteringDTO(): KvitteringDTO {
    return KvitteringDTO(
        reisetilskuddId = getString("reisetilskudd_id"),
        kvitteringId = getString("kvittering_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        belop = getDouble("belop"),
        transportmiddel = Transportmiddel.valueOf(getString("transportmiddel"))
    )
}
