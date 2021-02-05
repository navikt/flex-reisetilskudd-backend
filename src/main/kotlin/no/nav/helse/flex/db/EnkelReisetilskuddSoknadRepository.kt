package no.nav.helse.flex.db

import no.nav.helse.flex.domain.EnkelReisetilskuddSoknad
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EnkelReisetilskuddSoknadRepository : CrudRepository<EnkelReisetilskuddSoknad, String> {

    @Query(
        """
            select * FROM reisetilskudd_soknad
            WHERE status = 'ÅPEN'
            AND tom < :now
        """
    )
    fun finnReisetilskuddSomSkalBliSendbar(now: LocalDate): List<EnkelReisetilskuddSoknad>

    @Query(
        """
            select * FROM reisetilskudd_soknad
            WHERE status = 'FREMTIDIG'
            AND fom <= :now
        """
    )
    fun finnReisetilskuddSomSkalÅpnes(now: LocalDate): List<EnkelReisetilskuddSoknad>
}
