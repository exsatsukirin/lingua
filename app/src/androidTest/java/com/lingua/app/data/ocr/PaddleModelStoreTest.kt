package com.lingua.app.data.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled graphs must land in app storage intact, or every recognition would fail at runtime. */
@RunWith(AndroidJUnit4::class)
class PaddleModelStoreTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun installsTheBundledModelsAndVerifiesTheirHashes() {
    val store = PaddleModelStore(context)

    val models = runBlocking { store.models() }

    assertEquals(PaddleModelStore.DETECTION_BYTES, File(models.detectionPath).length())
    assertEquals(PaddleModelStore.RECOGNITION_BYTES, File(models.recognitionPath).length())
    assertEquals(18_708, models.dictionaryLines.size)
    assertTrue(store.isInstalled)
  }

  @Test
  fun reusesAnAlreadyVerifiedCopy() {
    val store = PaddleModelStore(context)

    val first = runBlocking { store.models() }
    val second = PaddleModelStore(context).let { runBlocking { it.models() } }

    assertEquals(first.detectionPath, second.detectionPath)
    assertEquals(first.recognitionPath, second.recognitionPath)
  }
}
