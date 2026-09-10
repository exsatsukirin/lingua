package com.lingua.app.data.history

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: TranslationDao) {

  fun observe(query: String, favoritesOnly: Boolean, limit: Int): Flow<List<TranslationRecord>> =
    dao.observeFiltered(query.trim(), favoritesOnly, limit)

  suspend fun insert(record: TranslationRecord): Long = dao.insert(record)

  suspend fun find(id: Long): TranslationRecord? = dao.findById(id)

  suspend fun delete(id: Long) = dao.deleteById(id)

  suspend fun deleteAll() = dao.deleteAll()

  suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

  suspend fun count(): Int = dao.count()
}
