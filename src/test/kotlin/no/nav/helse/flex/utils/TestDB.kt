package no.nav.helse.flex.utils

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.helse.flex.db.DatabaseInterface
import org.flywaydb.core.Flyway
import java.sql.Connection

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = try {
            EmbeddedPostgres.start()
        } catch (e: Exception) {
            EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
        }

        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM kvitteringer").executeUpdate()
        connection.prepareStatement("DELETE FROM reisetilskudd").executeUpdate()
        connection.commit()
    }
}
