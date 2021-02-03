package no.nav.helse.flex.application

import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

@Component
class DatabaseInterface(dataSource: DataSource) {

    val connection: Connection = dataSource.connection
}
