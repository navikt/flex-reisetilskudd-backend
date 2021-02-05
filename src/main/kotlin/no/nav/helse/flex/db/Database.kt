package no.nav.helse.flex.db

import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.ReisetilskuddSoknad
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.Transportmiddel
import no.nav.helse.flex.reisetilskudd.reisetilskuddStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Component
class Database {
    fun finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> {
        return emptyList()
    }

    fun finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> {
        return emptyList()
    }

    fun åpneReisetilskudd(id: ReisetilskuddSoknad) {
        TODO("Not yet implemented")
    }

    fun åpneReisetilskudd(id: String) {
        TODO("Not yet implemented")
    }

    fun sendbarReisetilskudd(id: String) {
        TODO("Not yet implemented")
    }

}
/*
{

    fun hentReisetilskuddene(fnr: String): List<ReisetilskuddSoknad> {
        val reisetilskudd = namedParameterJdbcTemplate.query(
            """
            SELECT * FROM reisetilskudd
            WHERE fnr = :fnr
        """,
            MapSqlParameterSource()
                .addValue("fnr", fnr),
            reisetilskuddRowMapper()
        )

        return reisetilskudd.map {
            it.copy(kvitteringer = hentKvitteringer(it.id))
        }
    }

    fun sendReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {
        val now = Instant.now()

        namedParameterJdbcTemplate.update(
            """
           UPDATE reisetilskudd
           SET (sendt, status) = (:sendt,'SENDT')
           WHERE reisetilskudd_id = :id
           AND fnr = :fnr
           AND sendt is null
           AND status = 'SENDBAR'
        """,
            MapSqlParameterSource()
                .addValue("sendt", Timestamp.from(now))
                .addValue("id", reisetilskuddId)
                .addValue("fnr", fnr)
        )
        return hentReisetilskudd(reisetilskuddId)
    }

    fun avbrytReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {
        val now = Instant.now()

        namedParameterJdbcTemplate.update(
            """
           UPDATE reisetilskudd
           SET (status, avbrutt) = ('AVBRUTT', :avbrutt)
           WHERE reisetilskudd_id = :id
           AND fnr = :fnr
           AND sendt is null
           AND (status = 'ÅPEN' OR status = 'FREMTIDIG' OR status = 'SENDBAR')
        """,
            MapSqlParameterSource()
                .addValue("avbrutt", Timestamp.from(now))
                .addValue("id", reisetilskuddId)
                .addValue("fnr", fnr)
        )

        return hentReisetilskudd(reisetilskuddId)
    }



    fun gjenapneReisetilskudd(fnr: String, reisetilskuddId: String): ReisetilskuddSoknad {

        val reisetilskudd = hentReisetilskudd(reisetilskuddId)

        val status = reisetilskuddStatus(reisetilskudd.fom, reisetilskudd.tom)

        namedParameterJdbcTemplate.update(
            """
              UPDATE reisetilskudd
           SET (status, avbrutt) = (:1, :2)
           WHERE reisetilskudd_id = :3
           AND fnr = :4
           AND sendt is null
           AND status = 'AVBRUTT'
        """,
            MapSqlParameterSource()
                .addValue("1", status.name)
                .addValue("2", null)
                .addValue("3", reisetilskuddId)
                .addValue("4", fnr)
        )
        return this.hentReisetilskudd(reisetilskuddId)
    }

    fun åpneReisetilskudd(id: String) {

        namedParameterJdbcTemplate.update(
            """
            UPDATE reisetilskudd
            SET status = :1
            WHERE reisetilskudd_id = :2
            AND status = :3
        """,
            MapSqlParameterSource()
                .addValue("1", ReisetilskuddStatus.ÅPEN.name)
                .addValue("2", id)
                .addValue("3", ReisetilskuddStatus.FREMTIDIG.name)
        )
    }

    fun sendbarReisetilskudd(id: String) {
        namedParameterJdbcTemplate.update(
            """
            UPDATE reisetilskudd
            SET status = :1
            WHERE reisetilskudd_id = :2
            AND status = :3
        """,
            MapSqlParameterSource()
                .addValue("1", ReisetilskuddStatus.SENDBAR.name)
                .addValue("2", id)
                .addValue("3", ReisetilskuddStatus.ÅPEN.name)
        )
    }

    fun lagreKvittering(kvittering: Kvittering, reisetilskuddId: String): Kvittering {
        val now = Instant.now()
        val id = UUID.randomUUID().toString()

        namedParameterJdbcTemplate.update(
            """
              INSERT INTO kvitteringer
                (kvittering_id, reisetilskudd_id, navn, belop, dato_for_reise, blob_id, transportmiddel, storrelse, opprettet)
                VALUES(:1, :2, :3, :4, :5, :6, :7, :8, :9)
        """,
            MapSqlParameterSource()
                .addValue("1", id)
                .addValue("2", reisetilskuddId)
                .addValue("3", kvittering.navn)
                .addValue("4", kvittering.belop)
                .addValue("5", Date.valueOf(kvittering.datoForUtgift))
                .addValue("6", kvittering.blobId)
                .addValue("7", kvittering.typeUtgift.name)
                .addValue("8", kvittering.storrelse)
                .addValue("9", Timestamp.from(now))
        )

        return kvittering.copy(id = id)
    }

    fun slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
        return namedParameterJdbcTemplate.update(
            """
                   DELETE FROM kvitteringer
            WHERE kvittering_id = :1
            AND reisetilskudd_id = :2
        """,
            MapSqlParameterSource()
                .addValue("1", kvitteringId)
                .addValue("2", reisetilskuddId)
        )
    }

    fun hentReisetilskudd(reisetilskuddId: String): ReisetilskuddSoknad {
        return finnReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd skal finnes")
    }

    fun finnReisetilskudd(reisetilskuddId: String): ReisetilskuddSoknad? {
        val reisetilskudd = namedParameterJdbcTemplate.query(
            """
            SELECT * FROM reisetilskudd
            WHERE reisetilskudd_id = :reisetilskuddId
        """,
            MapSqlParameterSource().addValue("reisetilskuddId", reisetilskuddId),
            reisetilskuddRowMapper()
        )

        return reisetilskudd.map {
            it.copy(kvitteringer = hentKvitteringer(it.id))
        }.firstOrNull()
    }

    fun lagreReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        finnReisetilskudd(reisetilskuddSoknad.id)?.let { return it }
        namedParameterJdbcTemplate.update(
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
           :1,:2,:3,:4,:5,:6,:7,:8,:9,:10,:11)
        """,
            MapSqlParameterSource()
                .addValue("1", reisetilskuddSoknad.id)
                .addValue("2", reisetilskuddSoknad.sykmeldingId)
                .addValue("3", reisetilskuddSoknad.fnr)
                .addValue("4", Date.valueOf(reisetilskuddSoknad.fom))
                .addValue("5", Date.valueOf(reisetilskuddSoknad.tom))
                .addValue("6", reisetilskuddSoknad.orgNummer)
                .addValue("7", reisetilskuddSoknad.orgNavn)
                .addValue("8", Timestamp.from(reisetilskuddSoknad.opprettet))
                .addValue("9", Timestamp.from(reisetilskuddSoknad.opprettet))
                .addValue("10", reisetilskuddSoknad.status.name)
                .addValue("11", reisetilskuddSoknad.oppfølgende.toInt())
        )
        return hentReisetilskudd(reisetilskuddSoknad.id)
    }

    private fun hentKvitteringer(reisetilskuddId: String): List<Kvittering> {

        return namedParameterJdbcTemplate.query(
            """
            SELECT * FROM kvitteringer
            WHERE reisetilskudd_id = :id
        """,
            MapSqlParameterSource().addValue("id", reisetilskuddId),
            kvitteringRowMapper()
        )
    }

    fun finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> {
        return namedParameterJdbcTemplate.query(
            """
              select * FROM reisetilskudd
            WHERE status = :1
            AND tom < :2
        """,
            MapSqlParameterSource()
                .addValue("1", ReisetilskuddStatus.ÅPEN.name)
                .addValue("2", Date.valueOf(now)),
            reisetilskuddIdRowMapper()
        )
    }

    fun finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> {
        return namedParameterJdbcTemplate.query(
            """
               select * FROM reisetilskudd
            WHERE status = :1
            AND fom <= :2
        """,
            MapSqlParameterSource()
                .addValue("1", ReisetilskuddStatus.FREMTIDIG.name)
                .addValue("2", Date.valueOf(now)),
            reisetilskuddIdRowMapper()
        )
    }

    private fun reisetilskuddIdRowMapper(): RowMapper<String> {
        return RowMapper { resultSet, _ ->
            with(resultSet) {
                getString("reisetilskudd_id")
            }
        }
    }

    private fun reisetilskuddRowMapper(): RowMapper<ReisetilskuddSoknad> {
        return RowMapper { resultSet, _ ->
            with(resultSet) {
                ReisetilskuddSoknad(
                    sykmeldingId = getString("sykmelding_id"),
                    fnr = getString("fnr"),
                    id = getString("reisetilskudd_id"),
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
                    kvitteringer = emptyList(),
                    status = ReisetilskuddStatus.valueOf(getString("status"))
                )
            }
        }
    }

    private fun kvitteringRowMapper(): RowMapper<Kvittering> {
        return RowMapper { resultSet, _ ->
            with(resultSet) {
                Kvittering(
                    id = getString("kvittering_id"),
                    blobId = getString("blob_id"),
                    navn = getString("navn"),
                    datoForUtgift = getObject("dato_for_reise", LocalDate::class.java),
                    belop = getInt("belop"),
                    typeUtgift = Transportmiddel.valueOf(getString("transportmiddel"))
                )
            }
        }
    }
}

private fun Boolean?.toInt(): Int {
    return when {
        this == true -> 1
        this == false -> -1
        else -> 0
    }
}

private fun Int.toOptionalBoolean(): Boolean? {
    return when {
        this == 1 -> true
        this == -1 -> false
        else -> null
    }
}

private fun Int.toBoolean(): Boolean {
    return when {
        this == 1 -> true
        this == -1 -> false
        else -> throw IllegalArgumentException("$this må være -1 eller 1")
    }
}

*/
