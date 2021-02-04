package no.nav.helse.flex.db

import no.nav.helse.flex.domain.Kvittering
import no.nav.helse.flex.domain.Reisetilskudd
import no.nav.helse.flex.domain.ReisetilskuddStatus
import no.nav.helse.flex.domain.Transportmiddel
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.sql.DataSource

@Service
@Transactional
@Repository
class Database(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    dataSource: DataSource
) {

    val connection: Connection = dataSource.connection // TODO slett

    fun hentReisetilskuddene(fnr: String): List<Reisetilskudd> {
        val reisetilskudd = namedParameterJdbcTemplate.query(
            """
            SELECT * FROM reisetilskudd
            WHERE fnr = :fnr
        """,
            MapSqlParameterSource().addValue("fnr", fnr),
            reisetilskuddRowMapper()
        )

        return reisetilskudd.map {
            it.copy(kvitteringer = hentKvitteringer(it.reisetilskuddId))
        }
    }

    fun hentReisetilskudd(reisetilskuddId: String): Reisetilskudd? {
        val reisetilskudd = namedParameterJdbcTemplate.query(
            """
            SELECT * FROM reisetilskudd
            WHERE reisetilskudd_id = :reisetilskuddId
        """,
            MapSqlParameterSource().addValue("reisetilskuddId", reisetilskuddId),
            reisetilskuddRowMapper()
        )

        return reisetilskudd.map {
            it.copy(kvitteringer = hentKvitteringer(it.reisetilskuddId))
        }.firstOrNull()
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

    private fun reisetilskuddRowMapper(): RowMapper<Reisetilskudd> {
        return RowMapper { resultSet, _ ->
            with(resultSet) {
                Reisetilskudd(
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
                    kvitteringer = emptyList(),
                    oppfølgende = getInt("oppfolgende").toBoolean(),
                    status = ReisetilskuddStatus.valueOf(getString("status"))
                )
            }
        }
    }

    private fun kvitteringRowMapper(): RowMapper<Kvittering> {
        return RowMapper { resultSet, _ ->
            with(resultSet) {
                Kvittering(
                    kvitteringId = getString("kvittering_id"),
                    blobId = getString("blob_id"),
                    navn = getString("navn"),
                    datoForReise = getObject("dato_for_reise", LocalDate::class.java),
                    belop = getInt("belop"),
                    storrelse = getLong("storrelse"),
                    transportmiddel = Transportmiddel.valueOf(getString("transportmiddel"))
                )
            }
        }
    }
}
