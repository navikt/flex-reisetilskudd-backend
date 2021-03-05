package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.helse.flex.svarvalidering.validerSvarPaSoknad
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class ReisetilskuddService(
    private val enkelReisetilskuddSoknadRepository: EnkelReisetilskuddSoknadRepository,
    private val kafkaProducer: ReisetilskuddKafkaProducer,
    private val metrikk: Metrikk,
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao
) {
    private val log = logger()

    fun sendReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        reisetilskuddSoknad.validerSvarPaSoknad()
        val avbrutt = reisetilskuddSoknad.tilEnkel().copy(sendt = Instant.now(), status = ReisetilskuddStatus.SENDT)
        enkelReisetilskuddSoknadRepository.save(avbrutt)
        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)
        kafkaProducer.send(reisetilskudd)
        metrikk.sendtReisetilskudd.increment()
        log.info("Sendte reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun avbrytReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        val avbrutt = reisetilskuddSoknad.tilEnkel().copy(avbrutt = Instant.now(), status = ReisetilskuddStatus.AVBRUTT)
        enkelReisetilskuddSoknadRepository.save(avbrutt)
        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)
        kafkaProducer.send(reisetilskudd)

        metrikk.avbruttReisetilskudd.increment()

        log.info("Avbrøt reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun gjenapneReisetilskudd(reisetilskuddSoknad: ReisetilskuddSoknad): ReisetilskuddSoknad {
        var status = reisetilskuddStatus(reisetilskuddSoknad.fom, reisetilskuddSoknad.tom)
        if (status == ReisetilskuddStatus.ÅPEN) {
            if (reisetilskuddSoknad.sporsmal.filter { it.tag == Tag.ANSVARSERKLARING }.any { it.svar.isNotEmpty() }) {
                status = ReisetilskuddStatus.PÅBEGYNT
            }
        }
        val gjenåpnet = reisetilskuddSoknad.tilEnkel().copy(avbrutt = null, status = status)
        enkelReisetilskuddSoknadRepository.save(gjenåpnet)

        val reisetilskudd = reisetilskuddSoknadDao.hentSoknad(reisetilskuddSoknad.id)

        kafkaProducer.send(reisetilskudd)

        metrikk.gjenapnetReisetilskudd.increment()
        log.info("Gjenåpnet reisetilskudd ${reisetilskudd.id}")
        return reisetilskudd
    }

    fun hentReisetilskuddene(fnr: String): List<ReisetilskuddSoknad> {
        return reisetilskuddSoknadDao.finnMedFnr(fnr)
    }

    fun hentReisetilskudd(id: String): ReisetilskuddSoknad? {
        return reisetilskuddSoknadDao.finnSoknad(id)
    }
}
