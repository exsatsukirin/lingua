package com.lingua.app.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Room schema (indices, LIKE search, favourite filter) on a device. */
@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {

  private lateinit var database: HistoryDatabase
  private lateinit var dao: TranslationDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
    dao = database.translationDao()
  }

  @After
  fun tearDown() = database.close()

  private fun record(
    text: String,
    translation: String = "译文",
    favorite: Boolean = false,
    createdAt: Long = System.currentTimeMillis(),
    lang: String = "en",
  ) = TranslationRecord(
    sourceText = text,
    translatedText = translation,
    sourceLangCode = lang,
    sourceLangName = "English",
    targetLangCode = "zh",
    targetLangName = "简体中文",
    providerName = "Mock",
    model = "mock-translate",
    createdAt = createdAt,
    isFavorite = favorite,
  )

  @Test
  fun insertsAndReadsBack() = runBlocking {
    val id = dao.insert(record("hello"))
    assertTrue(id > 0)
    assertEquals("hello", dao.findById(id)?.sourceText)
    assertEquals(1, dao.count())
  }

  @Test
  fun newestFirst() = runBlocking {
    dao.insert(record("older", createdAt = 1_000))
    dao.insert(record("newer", createdAt = 2_000))
    val rows = dao.observeFiltered(query = "", favoritesOnly = false, limit = 10).first()
    assertEquals(listOf("newer", "older"), rows.map { it.sourceText })
  }

  @Test
  fun searchMatchesEitherSide() = runBlocking {
    dao.insert(record("hello world", translation = "你好，世界"))
    dao.insert(record("goodbye", translation = "再见"))
    assertEquals(1, dao.observeFiltered("hello", false, 10).first().size)
    assertEquals(1, dao.observeFiltered("再见", false, 10).first().size)
    assertEquals(0, dao.observeFiltered("missing", false, 10).first().size)
    assertEquals(2, dao.observeFiltered("", false, 10).first().size)
  }

  @Test
  fun favoritesFilterIsApplied() = runBlocking {
    dao.insert(record("plain"))
    val favoriteId = dao.insert(record("starred", favorite = true))
    val favorites = dao.observeFiltered("", favoritesOnly = true, limit = 10).first()
    assertEquals(listOf("starred"), favorites.map { it.sourceText })

    dao.setFavorite(favoriteId, false)
    assertTrue(dao.observeFiltered("", true, 10).first().isEmpty())
  }

  @Test
  fun limitIsRespected() = runBlocking {
    repeat(5) { index -> dao.insert(record("row $index", createdAt = index.toLong())) }
    assertEquals(3, dao.observeFiltered("", false, 3).first().size)
  }

  @Test
  fun deleteRemovesOneRowAndClearRemovesAll() = runBlocking {
    val id = dao.insert(record("keep me"))
    val other = dao.insert(record("delete me"))
    dao.deleteById(other)
    assertEquals(1, dao.count())
    assertNull(dao.findById(other))
    assertEquals("keep me", dao.findById(id)?.sourceText)

    dao.deleteAll()
    assertEquals(0, dao.count())
  }

  @Test
  fun reinsertingADeletedRecordRestoresIt() = runBlocking {
    val id = dao.insert(record("undo me"))
    val stored = dao.findById(id)!!
    dao.deleteById(id)
    val restoredId = dao.insert(stored.copy(id = 0))
    assertEquals("undo me", dao.findById(restoredId)?.sourceText)
    assertEquals(1, dao.count())
  }
}
