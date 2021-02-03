package no.nav.helse.flex.application

import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
class DatabaseInterface(private val dataSource: DataSource) {

    val connection = dataSource.connection
}
