package no.nav.helse.flex.db

import no.nav.helse.flex.domain.ReisetilskuddSoknad
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ReisetilskuddSoknadRepository : CrudRepository<ReisetilskuddSoknad, String> {
    fun findReisetilskuddSoknadByFnr(fnr: String): List<ReisetilskuddSoknad>
}
