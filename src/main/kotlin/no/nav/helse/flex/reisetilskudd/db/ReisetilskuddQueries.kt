package no.nav.helse.flex.reisetilskudd.db

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.db.toList
import no.nav.helse.flex.reisetilskudd.domain.KvitteringDTO
import no.nav.helse.flex.reisetilskudd.domain.ReisetilskuddDTO
import no.nav.helse.flex.reisetilskudd.domain.Transportmiddel
import no.nav.helse.flex.reisetilskudd.domain.toInt
import no.nav.helse.flex.reisetilskudd.domain.toOptionalBoolean
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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

fun DatabaseInterface.sendReisetilskudd(fnr: String, reisetilskuddId: String) {
    connection.use { it.sendReisetilskudd(fnr, reisetilskuddId) }
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

private fun Connection.hentReisetilskudd(fnr: String): List<ReisetilskuddDTO> {
    val reisetilskudd = this.prepareStatement(
        """
            SELECT * FROM reisetilskudd
            WHERE fnr = ?
        """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toReisetilskuddDTO() }
    }
    reisetilskudd.forEach {
        it.kvitteringer = hentKvitteringer(it.reisetilskuddId)
    }
    return reisetilskudd
}

private fun Connection.hentReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddDTO? {
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
        it.executeQuery().toList { toReisetilskuddDTO(kvitteringer) }.firstOrNull()
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
           SET sendt = ?
           WHERE reisetilskudd_id = ?
           AND fnr = ?
        """
    ).use {
        it.setTimestamp(1, Timestamp.from(now))
        it.setString(2, reisetilskuddId)
        it.setString(3, fnr)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    val now = Instant.now()

    this.prepareStatement(
        """
           INSERT INTO reisetilskudd 
           (reisetilskudd_id, sykmelding_id, fnr, fom, tom, arbeidsgiver_orgnummer, arbeidsgiver_navn, opprettet, endret) 
           VALUES(?,?,?,?,?,?,?,?,?)
        """
    ).use {
        it.setString(1, reisetilskuddDTO.reisetilskuddId)
        it.setString(2, reisetilskuddDTO.sykmeldingId)
        it.setString(3, reisetilskuddDTO.fnr)
        it.setDate(4, Date.valueOf(reisetilskuddDTO.fom))
        it.setDate(5, Date.valueOf(reisetilskuddDTO.tom))
        it.setString(6, reisetilskuddDTO.orgNummer)
        it.setString(7, reisetilskuddDTO.orgNavn)
        it.setTimestamp(8, Timestamp.from(now))
        it.setTimestamp(9, Timestamp.from(now))
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.oppdaterReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {
    val now = Instant.now()

    this.prepareStatement(
        """
            UPDATE reisetilskudd
            SET (utbetaling_til_arbeidsgiver, gar, sykler, egen_bil, kollektivtransport, endret) = 
                (?, ?, ?, ?, ?, ?)                                
            WHERE reisetilskudd_id = ?
        """
    ).use {
        it.setInt(1, reisetilskuddDTO.utbetalingTilArbeidsgiver.toInt())
        it.setInt(2, reisetilskuddDTO.går.toInt())
        it.setInt(3, reisetilskuddDTO.sykler.toInt())
        it.setDouble(4, reisetilskuddDTO.egenBil)
        it.setDouble(5, reisetilskuddDTO.kollektivtransport)
        it.setTimestamp(6, Timestamp.from(now))
        it.setString(7, reisetilskuddDTO.reisetilskuddId)
        it.executeUpdate()
    }
    this.commit()
}

private fun Connection.lagreKvittering(kvitteringDTO: KvitteringDTO) {
    val now = Instant.now()

    this.prepareStatement(
        """
                INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, navn, belop, fom, tom, transportmiddel, storrelse, opprettet)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
    ).use {
        it.setString(1, kvitteringDTO.kvitteringId)
        it.setString(2, kvitteringDTO.reisetilskuddId)
        it.setString(3, kvitteringDTO.navn)
        it.setDouble(4, kvitteringDTO.belop)
        it.setDate(5, Date.valueOf(kvitteringDTO.fom))
        it.setDate(6, if (kvitteringDTO.tom != null) Date.valueOf(kvitteringDTO.tom) else null)
        it.setString(7, kvitteringDTO.transportmiddel.name)
        it.setLong(8, kvitteringDTO.storrelse)
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

private fun Connection.hentKvitteringer(reisetilskuddId: String): List<KvitteringDTO> {
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

fun ResultSet.toReisetilskuddDTO(kvitteringer: List<KvitteringDTO> = emptyList()): ReisetilskuddDTO {
    return ReisetilskuddDTO(
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
        kvitteringer = kvitteringer
    )
}

fun ResultSet.toKvitteringDTO(): KvitteringDTO {
    return KvitteringDTO(
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
