package com.lingua.app.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved translation. */
@Entity(
  tableName = "translation_history",
  indices = [Index("createdAt"), Index("isFavorite")],
)
data class TranslationRecord(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val sourceText: String,
  val translatedText: String,
  val sourceLangCode: String,
  val sourceLangName: String,
  val targetLangCode: String,
  val targetLangName: String,
  val providerName: String,
  val model: String,
  val createdAt: Long,
  val isFavorite: Boolean = false,
)
