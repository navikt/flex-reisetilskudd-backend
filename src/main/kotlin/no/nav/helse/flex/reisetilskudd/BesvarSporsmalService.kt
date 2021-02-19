package no.nav.helse.flex.reisetilskudd

import no.nav.helse.flex.client.bucketuploader.BucketUploaderClient
import no.nav.helse.flex.db.*
import no.nav.helse.flex.db.ReisetilskuddSoknadDao
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.ReisetilskuddStatus.PÅBEGYNT
import no.nav.helse.flex.domain.ReisetilskuddStatus.ÅPEN
import no.nav.helse.flex.svarvalidering.validerSvarPaSporsmal
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class BesvarSporsmalService(
    private val reisetilskuddSoknadDao: ReisetilskuddSoknadDao,
    private val bucketUploaderClient: BucketUploaderClient,
    private val enkelReisetilskuddSoknadRepository: EnkelReisetilskuddSoknadRepository,
    private val kafkaProducer: ReisetilskuddKafkaProducer,
) {

    fun oppdaterSporsmal(soknadFraBasenFørOppdatering: ReisetilskuddSoknad, sporsmal: Sporsmal): ReisetilskuddSoknad {
        val sporsmalId = sporsmal.id
        val soknadId = soknadFraBasenFørOppdatering.id

        if (sporsmal.tag == Tag.KVITTERINGER) {
            throw IllegalArgumentException("Kvitteringsspørsmål skal lagre og slette enkeltsvar")
        }
        if (!soknadFraBasenFørOppdatering.sporsmal.flatten().map { it.id }.contains(sporsmalId)) {
            throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
        }

        if (!soknadFraBasenFørOppdatering.sporsmal.map { it.id }.contains(sporsmalId)) {
            throw IllegalArgumentException("$sporsmalId er ikke et hovedspørsmål i søknad $soknadId")
        }

        fun List<Sporsmal>.erUlikUtenomSvar(sammenlign: List<Sporsmal>): Boolean {
            fun List<Sporsmal>.flattenOgFjernSvar(): List<Sporsmal> {
                return this.flatten().map { it.copy(svar = emptyList()) }.sortedBy { it.id }
            }

            return this.flattenOgFjernSvar() != sammenlign.flattenOgFjernSvar()
        }

        val sporsmaletFraBasen = soknadFraBasenFørOppdatering.sporsmal.find { it.id == sporsmal.id }
            ?: throw IllegalArgumentException("Soknad fra basen skal ha spørsmålet")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Spørsmål i databasen er ulikt spørsmål som er besvart")
        }

        sporsmal.validerSvarPaSporsmal()

        if (soknadFraBasenFørOppdatering.status == ÅPEN && sporsmal.tag == Tag.ANSVARSERKLARING) {
            val påbegynt = soknadFraBasenFørOppdatering.tilEnkel().copy(status = PÅBEGYNT)
            enkelReisetilskuddSoknadRepository.save(påbegynt)
            reisetilskuddSoknadDao.lagreSvar(sporsmal)
            return reisetilskuddSoknadDao.hentSoknad(soknadId).also {
                kafkaProducer.send(it)
            }
        }

        reisetilskuddSoknadDao.lagreSvar(sporsmal)
        return reisetilskuddSoknadDao.hentSoknad(soknadId)
    }

    fun lagreNyttSvar(soknadFraBasenFørOppdatering: ReisetilskuddSoknad, sporsmalId: String, svar: Svar): Sporsmal {
        val soknadId = soknadFraBasenFørOppdatering.id

        val sporsmal = (
            soknadFraBasenFørOppdatering.sporsmal.flatten()
                .find { it.id == sporsmalId }
                ?: throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
            )

        val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar.toMutableList().also { it.add(svar) })
        oppdatertSporsmal.validerSvarPaSporsmal()

        reisetilskuddSoknadDao.lagreSvar(oppdatertSporsmal)
        return reisetilskuddSoknadDao.hentSoknad(soknadId).sporsmal.flatten().find { it.id == sporsmalId }!!
    }

    fun slettSvar(soknadFraBasenFørOppdatering: ReisetilskuddSoknad, sporsmalId: String, svarId: String) {
        val soknadId = soknadFraBasenFørOppdatering.id

        if (!soknadFraBasenFørOppdatering.sporsmal.flatten().map { it.id }.contains(sporsmalId)) {
            throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
        }

        val sporsmal = (
            soknadFraBasenFørOppdatering.sporsmal.flatten()
                .find { it.id == sporsmalId }
                ?: throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
            )

        if (sporsmal.svar.map { it.id }.contains(svarId)) {
            reisetilskuddSoknadDao.slettSvar(svarId)
            if (sporsmal.svartype == Svartype.KVITTERING) {
                bucketUploaderClient.slettKvittering(svarId)
            }
        }
    }
}
