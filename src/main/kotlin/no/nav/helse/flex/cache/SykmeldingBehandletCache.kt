package no.nav.helse.flex.cache

import no.nav.helse.flex.config.CacheConfig.Companion.sykmeldingBehandletCacheNavn
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SykmeldingBehandletCache(
    cacheManager: CacheManager,
) {

    val cache = cacheManager.getCache(sykmeldingBehandletCacheNavn) ?: throw IllegalStateException("Cache skal finnes")

    fun merkSykmeldingSomBehandlet(sykmeldingId: String) {
        cache.put(sykmeldingId, Instant.now())
    }

    fun erSykmeldingBehandlet(sykmeldingId: String): Boolean {
        cache.get(sykmeldingId)?.get()?.let { return true }
        return false
    }
}
