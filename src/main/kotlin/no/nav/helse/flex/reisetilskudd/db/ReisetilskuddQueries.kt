package no.nav.helse.flex.reisetilskudd.db

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
import java.time.OffsetDateTime

fun Connection.hentReisetilskuddene(fnr: String): List<Reisetilskudd> {
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

fun Connection.hentReisetilskudd(reisetilskuddId: String): Reisetilskudd? {
    val kvitteringer = hentKvitteringer(reisetilskuddId)
    return this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE reisetilskudd_id = ?
        """
    ).use {
        it.setString(1, reisetilskuddId)
        it.executeQuery().toList { toReisetilskudd(kvitteringer) }.firstOrNull()
    }
}

fun Connection.eierReisetilskudd(fnr: String, id: String): Boolean =
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

fun Connection.sendReisetilskudd(fnr: String, reisetilskuddId: String) {
    val now = Instant.now()

    this.prepareStatement(
        """
           UPDATE reisetilskudd 
           SET (sendt, status) = (?,'SENDT')
           WHERE reisetilskudd_id = ?
           AND fnr = ?
           AND sendt is null
           AND status = 'SENDBAR'
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(now))
        it.setString(2, reisetilskuddId)
        it.setString(3, fnr)
        it.executeUpdate()
    }
}

fun Connection.avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
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

fun Connection.gjenapneReisetilskudd(fnr: String, reisetilskuddId: String, status: ReisetilskuddStatus) {
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

fun Connection.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
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

fun Connection.oppdaterReisetilskudd(reisetilskudd: Reisetilskudd) {
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

fun Connection.lagreKvittering(kvittering: Kvittering, reisetilskuddId: String, kvitteringId: String) {
    val now = Instant.now()
    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, navn, belop, dato_for_reise, blob_id, transportmiddel, storrelse, opprettet)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, kvitteringId)
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
}

fun Connection.slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
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

fun Connection.hentKvittering(kvitteringId: String): Kvittering? {
    return this.prepareStatement(
        """
            SELECT * FROM kvitteringer
            WHERE kvittering_id = ?
        """
    ).use {
        it.setString(1, kvitteringId)
        it.executeQuery().toList { toKvitteringDTO() }.firstOrNull()
    }
}

fun Connection.hentKvitteringer(reisetilskuddId: String): List<Kvittering> {
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

fun Connection.finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> =
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

fun Connection.finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> =
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

fun Connection.åpneReisetilskudd(id: String) {
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

fun Connection.sendbarReisetilskudd(id: String) {
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

fun ResultSet.toReisetilskudd(kvitteringer: List<Kvittering> = emptyList()): Reisetilskudd {
    return Reisetilskudd(
        sykmeldingId = getString("sykmelding_id"),
        fnr = getString("fnr"),
        reisetilskuddId = getString("reisetilskudd_id"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        orgNummer = getString("arbeidsgiver_orgnummer"),
        orgNavn = getString("arbeidsgiver_navn"),
        sendt = getObject("sendt", OffsetDateTime::class.java)?.toInstant(),
        avbrutt = getObject("avbrutt", OffsetDateTime::class.java)?.toInstant(),
        opprettet = getObject("opprettet", OffsetDateTime::class.java).toInstant(),
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
        kvitteringId = getString("kvittering_id"),
        blobId = getString("blob_id"),
        navn = getString("navn"),
        datoForReise = getObject("dato_for_reise", LocalDate::class.java),
        belop = getInt("belop"),
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
