package no.nav.helse.flex.reisetilskudd.transactions

import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.log
import no.nav.helse.flex.reisetilskudd.db.avbrytReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.eierReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.finnReisetilskuddSomSkalBliSendbar
import no.nav.helse.flex.reisetilskudd.db.finnReisetilskuddSomSkalÅpnes
import no.nav.helse.flex.reisetilskudd.db.gjenapneReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.hentKvittering
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.hentReisetilskuddene
import no.nav.helse.flex.reisetilskudd.db.lagreKvittering
import no.nav.helse.flex.reisetilskudd.db.lagreReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.oppdaterReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.sendReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.sendbarReisetilskudd
import no.nav.helse.flex.reisetilskudd.db.slettKvittering
import no.nav.helse.flex.reisetilskudd.db.åpneReisetilskudd
import no.nav.helse.flex.reisetilskudd.domain.Kvittering
import no.nav.helse.flex.reisetilskudd.domain.Reisetilskudd
import no.nav.helse.flex.reisetilskudd.util.reisetilskuddStatus
import org.apache.kafka.clients.producer.ProducerRecord
import java.lang.Exception
import java.sql.Connection
import java.time.LocalDate

class TransactionHandler(
    val databaseConnection: Connection,
    val aivenKafkaConfig: AivenKafkaConfig
) {
    val kafkaProducer = aivenKafkaConfig.producer().also {
        it.initTransactions()
    }

    init {
        kafkaProducer.beginTransaction()
        databaseConnection.autoCommit = false
        databaseConnection.setSavepoint()
    }

    fun commitTransaction() {
        databaseConnection.commit()
        kafkaProducer.commitTransaction()
    }

    fun rollback() {
        databaseConnection.rollback()
        kafkaProducer.abortTransaction()
    }

    fun closeTransaction() {
        databaseConnection.close()
        kafkaProducer.close()
    }
}

/**
 * Alt blir håndtert som transactions og ved feil rulles alt innenfor en transaction { } tilbake
 */
open class Transactions(
    private val database: DatabaseInterface,
    private val aivenKafkaConfig: AivenKafkaConfig
) : UnhandledTransactions(database) {
    // Arver av UnhandledTransactions for å få tilgang til noen metoder som ikke krever en transaction { }
    // De andre metodene som krever transaction må wrappes inn i en transaction { }

    fun transaction(
        runThisBlock: TransactionHandler.() -> Unit
    ) = run {
        TransactionHandler(
            databaseConnection = database.connection,
            aivenKafkaConfig = aivenKafkaConfig
        ).let {
            try {
                runThisBlock(it)
                it.commitTransaction()
            } catch (e: Exception) {
                log.error("Feil i Transaction, ruller tilbake: ${e.message}")
                it.rollback()
                throw e
            } finally {
                it.closeTransaction()
            }
        }
    }
}

fun TransactionHandler.lagreReisetilskudd(reisetilskudd: Reisetilskudd) {
    databaseConnection.hentReisetilskudd(reisetilskudd.reisetilskuddId)?.let { return }
    databaseConnection.lagreReisetilskudd(reisetilskudd)
    kafkaProducer.send(
        ProducerRecord(
            AivenKafkaConfig.topic,
            reisetilskudd.reisetilskuddId,
            reisetilskudd
        )
    ).get()
}

fun TransactionHandler.oppdaterReisetilskudd(reisetilskudd: Reisetilskudd) {
    databaseConnection.oppdaterReisetilskudd(reisetilskudd)
}

fun TransactionHandler.sendReisetilskudd(fnr: String, reisetilskuddId: String) {
    val reisetilskudd = databaseConnection.run {
        sendReisetilskudd(fnr, reisetilskuddId)
        hentReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
    }
    kafkaProducer.send(
        ProducerRecord(
            AivenKafkaConfig.topic,
            reisetilskudd.reisetilskuddId,
            reisetilskudd
        )
    ).get()
}

fun TransactionHandler.avbrytReisetilskudd(fnr: String, reisetilskuddId: String) {
    val reisetilskudd = databaseConnection.run {
        avbrytReisetilskudd(fnr, reisetilskuddId)
        hentReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
    }
    kafkaProducer.send(
        ProducerRecord(
            AivenKafkaConfig.topic,
            reisetilskudd.reisetilskuddId,
            reisetilskudd
        )
    ).get()
}

fun TransactionHandler.gjenapneReisetilskudd(fnr: String, reisetilskuddId: String) {
    val reisetilskudd = databaseConnection.run {
        val rt = hentReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd skal finnes")
        gjenapneReisetilskudd(fnr, reisetilskuddId, reisetilskuddStatus(rt.fom, rt.tom))
        hentReisetilskudd(reisetilskuddId) ?: throw RuntimeException("Reisetilskudd id skal finnes")
    }
    kafkaProducer.send(
        ProducerRecord(
            AivenKafkaConfig.topic,
            reisetilskudd.reisetilskuddId,
            reisetilskudd
        )
    ).get()
}

fun TransactionHandler.lagreKvittering(kvittering: Kvittering, reisetilskuddId: String, kvitteringId: String) {
    databaseConnection.lagreKvittering(kvittering, reisetilskuddId, kvitteringId)
}

fun TransactionHandler.slettKvittering(kvitteringId: String, reisetilskuddId: String): Int {
    return databaseConnection.slettKvittering(kvitteringId, reisetilskuddId)
}

fun TransactionHandler.finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<String> {
    return databaseConnection.finnReisetilskuddSomSkalÅpnes(now)
}

fun TransactionHandler.finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<String> {
    return databaseConnection.finnReisetilskuddSomSkalBliSendbar(now)
}

fun TransactionHandler.åpneReisetilskudd(id: String) {
    return databaseConnection.åpneReisetilskudd(id)
}

fun TransactionHandler.sendbarReisetilskudd(id: String) {
    return databaseConnection.sendbarReisetilskudd(id)
}

/**
 * Disse er allerede definert for UnhandledTransactions, men for å jobbe med aktive endringer inne i en transaction så må disse defineres med samme connection
 */
fun TransactionHandler.hentReisetilskudd(reisetilskuddId: String): Reisetilskudd? {
    return databaseConnection.hentReisetilskudd(reisetilskuddId)
}
fun TransactionHandler.hentReisetilskuddene(fnr: String): List<Reisetilskudd> {
    return databaseConnection.hentReisetilskuddene(fnr)
}

/**
 * Uhåndterte transaksjoner som ikke endrer på data
 */
open class UnhandledTransactions(
    private val database: DatabaseInterface
) {
    fun hentReisetilskuddene(fnr: String): List<Reisetilskudd> {
        database.connection.use { return it.hentReisetilskuddene(fnr) }
    }

    fun hentReisetilskudd(reisetilskuddId: String): Reisetilskudd? {
        database.connection.use { return it.hentReisetilskudd(reisetilskuddId) }
    }

    fun hentKvittering(kvitteringId: String): Kvittering? {
        database.connection.use { return it.hentKvittering(kvitteringId) }
    }

    fun eierReisetilskudd(fnr: String, id: String): Boolean {
        database.connection.use { return it.eierReisetilskudd(fnr, id) }
    }
}
