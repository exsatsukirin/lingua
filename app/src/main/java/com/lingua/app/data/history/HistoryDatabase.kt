package com.lingua.app.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1 ships without migrations: the schema is frozen until a real migration is written, so
 * destructive fallback is deliberately *not* enabled.
 */
@Database(entities = [TranslationRecord::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {

  abstract fun translationDao(): TranslationDao

  companion object {
    private const val NAME = "lingua.db"

    fun build(context: Context): HistoryDatabase =
      Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, NAME).build()
  }
}
