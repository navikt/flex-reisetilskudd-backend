package no.nav.helse.flex.reisetilskudd.db

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.db.toList
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.domain.Transportmiddel
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

fun DatabaseInterface.hentReisetilskudd(fnr: String): List<Reisetilskudd> {
    connection.use { return it.hentReisetilskudd(fnr) }
}

fun DatabaseInterface.hentReisetilskudd(fnr: String, reisetilskuddId: String): Reisetilskudd? {
    connection.use { return it.hentReisetilskudd(fnr, reisetilskuddId) }
}

fun DatabaseInterface.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
    connection.use {
        it.hentReisetilskudd(reisetilskudd.fnr, reisetilskudd.reisetilskuddId)?.let { return }
        it.lagreReisetilskudd(reisetilskudd)
    }
}

fun DatabaseInterface.oppdaterReisetilskudd(reisetilskudd: Reisetilskudd) {
    connection.use { it.oppdaterReisetilskudd(reisetilskudd) }
}

fun DatabaseInterface.sendReisetilskudd(fnr: String, reisetilskuddId: String): Reisetilskudd {
    connection.use {
        it.sendReisetilskudd(fnr, reisetilskuddId)
        return it.hentReisetilskudd(fnr, reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
    }
}

fun DatabaseInterface.lagreKvittering(kvittering: Kvittering) {
    connection.use { it.lagreKvittering(kvittering) }
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

private fun Connection.hentReisetilskudd(fnr: String): List<Reisetilskudd> {
    val reisetilskudd = this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE fnr = ?
        """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toReisetilskudd() }
    }
    return reisetilskudd.map {
        it.copy(kvitteringer = hentKvitteringer(it.reisetilskuddId))
    }
}

private fun Connection.hentReisetilskudd(fnr: String, reisetilskuddId: String): Reisetilskudd? {
    val kvitteringer = hentKvitteringer(reisetilskuddId)
    return this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE fnr = ?
            AND reisetilskudd_id = ?
        """
    ).use {
        it.setString(1, fnr)
        it.setString(2, reisetilskuddId)
        it.executeQuery().toList { toReisetilskudd(kvitteringer) }.firstOrNull()
    }
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

private fun Connection.sendReisetilskudd(fnr: String, reisetilskuddId: String) {
    val now = Instant.now()

    this.prepareStatement(
        """
           UPDATE reisetilskudd 
           SET (sendt, status) = (?,'SENDT')
           WHERE reisetilskudd_id = ?
           AND fnr = ?
           AND sendt is null
           AND status = 'ÅPEN'
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(now))
        it.setString(2, reisetilskuddId)
        it.setString(3, fnr)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
    val now = Instant.now()

    this.prepareStatement(
        """
           INSERT INTO reisetilskudd (
           reisetilskudd_id, 
           sykmelding_id, 
           fnr, 
           fom, 
           tom, 
           arbeidsgiver_orgnummer, 
           arbeidsgiver_navn, 
           opprettet, 
           endret, 
           status, 
           oppfolgende) 
           VALUES(
           ?,?,?,?,?,?,?,?,?,?,?)
        """
    ).use {
        it.setString(1, reisetilskudd.reisetilskuddId)
        it.setString(2, reisetilskudd.sykmeldingId)
        it.setString(3, reisetilskudd.fnr)
        it.setDate(4, Date.valueOf(reisetilskudd.fom))
        it.setDate(5, Date.valueOf(reisetilskudd.tom))
        it.setString(6, reisetilskudd.orgNummer)
        it.setString(7, reisetilskudd.orgNavn)
        it.setTimestamp(8, Timestamp.from(now))
        it.setTimestamp(9, Timestamp.from(now))
        it.setString(10, reisetilskudd.status.name)
        it.setInt(11, reisetilskudd.oppfølgende.toInt())
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.oppdaterReisetilskudd(reisetilskudd: Reisetilskudd) {
    val now = Instant.now()

    this.prepareStatement(
        """
            UPDATE reisetilskudd
            SET (utbetaling_til_arbeidsgiver, gar, sykler, egen_bil, kollektivtransport, endret) = 
                (?, ?, ?, ?, ?, ?)                                
            WHERE reisetilskudd_id = ?
        """
    ).use {
        it.setInt(1, reisetilskudd.utbetalingTilArbeidsgiver.toInt())
        it.setInt(2, reisetilskudd.går.toInt())
        it.setInt(3, reisetilskudd.sykler.toInt())
        it.setDouble(4, reisetilskudd.egenBil)
        it.setDouble(5, reisetilskudd.kollektivtransport)
        it.setTimestamp(6, Timestamp.from(now))
        it.setString(7, reisetilskudd.reisetilskuddId)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.lagreKvittering(kvittering: Kvittering) {
    val now = Instant.now()

    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, navn, belop, fom, tom, transportmiddel, storrelse, opprettet)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, kvittering.kvitteringId)
        it.setString(2, kvittering.reisetilskuddId)
        it.setString(3, kvittering.navn)
        it.setDouble(4, kvittering.belop)
        it.setDate(5, Date.valueOf(kvittering.fom))
        it.setDate(6, if (kvittering.tom != null) Date.valueOf(kvittering.tom) else null)
        it.setString(7, kvittering.transportmiddel.name)
        it.setLong(8, kvittering.storrelse)
        it.setTimestamp(9, Timestamp.from(now))
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

private fun Connection.hentKvitteringer(reisetilskuddId: String): List<Kvittering> {
    return this.prepareStatement(
        """
            SELECT * FROM kvitteringer
            WHERE reisetilskudd_id = ?
        """
    ).use {
        it.setString(1, reisetilskuddId)
        it.executeQuery().toList {
            toKvitteringDTO()
        }
    }
}

fun ResultSet.toReisetilskudd(kvitteringer: List<Kvittering> = emptyList()): Reisetilskudd {
    return Reisetilskudd(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr"),
        reisetilskuddId = getString("reisetilskudd_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        orgNummer = getString("arbeidsgiver_orgnummer"),
        orgNavn = getString("arbeidsgiver_navn"),
        sendt = getObject("sendt", LocalDateTime::class.java),
        utbetalingTilArbeidsgiver = getInt("utbetaling_til_arbeidsgiver").toOptionalBoolean(),
        går = getInt("gar").toOptionalBoolean(),
        sykler = getInt("sykler").toOptionalBoolean(),
        egenBil = getDouble("egen_bil"),
        kollektivtransport = getDouble("kollektivtransport"),
        kvitteringer = kvitteringer,
        oppfølgende = getInt("oppfolgende").toBoolean(),
        status = ReisetilskuddStatus.valueOf(getString("status"))
    )
}

fun ResultSet.toKvitteringDTO(): Kvittering {
    return Kvittering(
        reisetilskuddId = getString("reisetilskudd_id"),
        kvitteringId = getString("kvittering_id"),
        navn = getString("navn"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        belop = getDouble("belop"),
        storrelse = getLong("storrelse"),
        transportmiddel = Transportmiddel.valueOf(getString("transportmiddel"))
    )
}

fun Boolean?.toInt(): Int {
    return when {
        this == true -> 1
        this == false -> -1
        else -> 0
    }
}

fun Int.toOptionalBoolean(): Boolean? {
    return when {
        this == 1 -> true
        this == -1 -> false
        else -> null
    }
}

fun Int.toBoolean(): Boolean {
    return when {
        this == 1 -> true
        this == -1 -> false
        else -> throw IllegalArgumentException("$this må være -1 eller 1")
    }
}
