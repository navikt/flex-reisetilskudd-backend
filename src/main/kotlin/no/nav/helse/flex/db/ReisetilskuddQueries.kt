package no.nav.helse.flex.db

import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Reisetilskudd
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.reisetilskudd.reisetilskuddStatus
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

fun Database.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
    this.finnReisetilskudd(reisetilskudd.reisetilskuddId)?.let { return }
    return this.connection.lagreReisetilskudd(reisetilskudd)
}

fun Database.oppdaterReisetilskudd(reisetilskudd: Reisetilskudd): Reisetilskudd {
    this.connection.oppdaterReisetilskudd(reisetilskudd)
    return this.finnReisetilskudd(reisetilskudd.reisetilskuddId)!!
}

fun Database.avbrytReisetilskudd(fnr: String, reisetilskuddId: String): Reisetilskudd {

    this.connection.avbrytReisetilskudd(fnr, reisetilskuddId)
    return this.finnReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
}

fun Database.gjenapneReisetilskudd(fnr: String, reisetilskuddId: String): Reisetilskudd {

    val reisetilskudd =
        this.finnReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd skal finnes")
    this.connection.gjenapneReisetilskudd(
        fnr,
        reisetilskuddId,
        reisetilskuddStatus(reisetilskudd.fom, reisetilskudd.tom)
    )
    return this.finnReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
}

fun Database.lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
    return this.connection.lagreKvittering(kvittering, reisetilskuddId)
}

fun Database.slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
    return this.connection.slettKvittering(kvitteringId, reisetilskuddId)
}

fun Database.finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> {
    return this.connection.finnReisetilskuddSomSkalÅpnes(now)
}

fun Database.finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> {
    return this.connection.finnReisetilskuddSomSkalBliSendbar(now)
}

fun Database.åpneReisetilskudd(id: String) {
    this.connection.åpneReisetilskudd(id)
}

fun Database.sendbarReisetilskudd(id: String) {
    this.connection.sendbarReisetilskudd(id)
}

private fun Connection.avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
    val now = Instant.now()
    this.prepareStatement(
        """
           UPDATE reisetilskudd 
           SET (status, avbrutt) = (?, ?)
           WHERE reisetilskudd_id = ?
           AND fnr = ?
           AND sendt is null
           AND (status = 'ÅPEN' OR status = 'FREMTIDIG' OR status = 'SENDBAR')
        """
    ).use {
        it.setString(1, ReisetilskuddStatus.AVBRUTT.name)
        it.setTimestamp(2, Timestamp.from(now))
        it.setString(3, reisetilskuddId)
        it.setString(4, fnr)
        it.executeUpdate()
    }
}

private fun Connection.gjenapneReisetilskudd(fnr: String, reisetilskuddId: String, status: ReisetilskuddStatus) {
    this.prepareStatement(
        """
           UPDATE reisetilskudd 
           SET (status, avbrutt) = (?, ?)
           WHERE reisetilskudd_id = ?
           AND fnr = ?
           AND sendt is null
           AND status = 'AVBRUTT'
        """
    ).use {
        it.setString(1, status.name)
        it.setTimestamp(2, null)
        it.setString(3, reisetilskuddId)
        it.setString(4, fnr)
        it.executeUpdate()
    }
}

private fun Connection.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {

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
        it.setTimestamp(8, Timestamp.from(reisetilskudd.opprettet))
        it.setTimestamp(9, Timestamp.from(reisetilskudd.opprettet))
        it.setString(10, reisetilskudd.status.name)
        it.setInt(11, reisetilskudd.oppfølgende.toInt())
        it.executeUpdate()
    }
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
}

private fun Connection.lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
    val now = Instant.now()
    val id = UUID.randomUUID().toString()

    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, navn, belop, dato_for_reise, blob_id, transportmiddel, storrelse, opprettet)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, id)
        it.setString(2, reisetilskuddId)
        it.setString(3, kvittering.navn)
        it.setInt(4, kvittering.belop)
        it.setDate(5, Date.valueOf(kvittering.datoForReise))
        it.setString(6, kvittering.blobId)
        it.setString(7, kvittering.transportmiddel.name)
        it.setLong(8, kvittering.storrelse)
        it.setTimestamp(9, Timestamp.from(now))
        it.executeUpdate()
    }
    return kvittering.copy(kvitteringId = id)
}

private fun Connection.slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
    this.prepareStatement(
        """
            DELETE FROM kvitteringer
            WHERE kvittering_id = ?
            AND reisetilskudd_id = ?

        """
    ).use {
        it.setString(1, kvitteringId)
        it.setString(2, reisetilskuddId)
        val executeUpdate = it.executeUpdate()
        return executeUpdate
    }
}

private fun Connection.finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> =
    this.prepareStatement(
        """
            select * FROM reisetilskudd
            WHERE status = ? 
            AND fom <= ?
    """
    ).use {
        it.setString(1, ReisetilskuddStatus.FREMTIDIG.name)
        it.setDate(2, Date.valueOf(now))
        it.executeQuery().toList {
            getString("reisetilskudd_id")
        }
    }

private fun Connection.finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> =
    this.prepareStatement(
        """
            select * FROM reisetilskudd
            WHERE status = ? 
            AND tom < ?
    """
    ).use {
        it.setString(1, ReisetilskuddStatus.ÅPEN.name)
        it.setDate(2, Date.valueOf(now))
        it.executeQuery().toList {
            getString("reisetilskudd_id")
        }
    }

private fun Connection.åpneReisetilskudd(id: String) {
    this.prepareStatement(
        """
            UPDATE reisetilskudd 
            SET status = ? 
            WHERE reisetilskudd_id = ? 
            AND status = ?
        """
    ).use {
        it.setString(1, ReisetilskuddStatus.ÅPEN.name)
        it.setString(2, id)
        it.setString(3, ReisetilskuddStatus.FREMTIDIG.name)
        it.executeUpdate()
    }
}

private fun Connection.sendbarReisetilskudd(id: String) {
    this.prepareStatement(
        """
            UPDATE reisetilskudd 
            SET status = ? 
            WHERE reisetilskudd_id = ? 
            AND status = ?
        """
    ).use {
        it.setString(1, ReisetilskuddStatus.SENDBAR.name)
        it.setString(2, id)
        it.setString(3, ReisetilskuddStatus.ÅPEN.name)
        it.executeUpdate()
    }
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
