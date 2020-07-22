package no.nav.syfo.reisetilskudd

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.ReisetilskuddDTO
import no.nav.syfo.domain.SykmeldingMessage
import no.nav.syfo.reisetilskudd.db.hentReisetilskudd
import no.nav.syfo.reisetilskudd.db.lagreReisetilskudd
import java.util.UUID

class ReisetilskuddService(private val database: DatabaseInterface) {

    fun hentReisetilskudd(fnr: String) =
        database.hentReisetilskudd(fnr)

    fun lagreReisetilskudd(reisetilskuddDTO: ReisetilskuddDTO) {

        database.lagreReisetilskudd(reisetilskuddDTO)
    }

    fun behandleSykmelding(sykmeldingMessage: SykmeldingMessage) {
        val sykmelding = sykmeldingMessage.sykmelding
        sykmelding.sykmeldingsperioder.filter {
            it.reisetilskudd
        }.forEach {
            val reisetilskuddId = UUID.randomUUID()
            val reisetilskuddDTO = ReisetilskuddDTO(
                reisetilskuddId = reisetilskuddId.toString(),
                sykmeldingId = sykmelding.id,
                fnr = sykmeldingMessage.kafkaMetadata.fnr,
                fom = it.fom,
                tom = it.tom,
                orgNavn = sykmeldingMessage.event.arbeidsgiver?.orgNavn,
                orgNummer = sykmeldingMessage.event.arbeidsgiver?.orgnummer
            )
            lagreReisetilskudd(reisetilskuddDTO)
        }
    }
}
