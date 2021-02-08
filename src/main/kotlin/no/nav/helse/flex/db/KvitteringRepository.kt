package no.nav.helse.flex.db

import no.nav.helse.flex.domain.Kvittering
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KvitteringRepository : CrudRepository<Kvittering, String>
