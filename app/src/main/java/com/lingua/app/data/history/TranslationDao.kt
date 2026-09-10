package com.lingua.app.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {

  @Query(
    """
    SELECT * FROM translation_history
    WHERE (:favoritesOnly = 0 OR isFavorite = 1)
      AND (:query = '' OR sourceText LIKE '%' || :query || '%' OR translatedText LIKE '%' || :query || '%')
    ORDER BY createdAt DESC
    LIMIT :limit
    """
  )
  fun observeFiltered(query: String, favoritesOnly: Boolean, limit: Int): Flow<List<TranslationRecord>>

  @Query("SELECT * FROM translation_history WHERE id = :id")
  suspend fun findById(id: Long): TranslationRecord?

  @Insert suspend fun insert(record: TranslationRecord): Long

  @Query("DELETE FROM translation_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM translation_history")
  suspend fun deleteAll()

  @Query("UPDATE translation_history SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("SELECT COUNT(*) FROM translation_history")
  suspend fun count(): Int
}
