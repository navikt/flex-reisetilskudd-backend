package no.nav.helse.flex.db

import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull

fun <T, ID> CrudRepository<T, ID>.getById(id: ID): T = findByIdOrNull(id) ?: throw IllegalStateException("Entitet med id $id skal finnes")
