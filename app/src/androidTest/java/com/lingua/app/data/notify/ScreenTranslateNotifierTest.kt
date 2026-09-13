package com.lingua.app.data.notify

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTranslateNotifierTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun startClean() {
    assumeTrue(
      "notifications are disabled for this app",
      NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )
    ScreenTranslateNotifier.setEnabled(context, enabled = false)
    Thread.sleep(150)
  }

  @After
  fun cleanUp() {
    ScreenTranslateNotifier.setEnabled(context, enabled = false)
  }

  @Test
  fun enablingStartsTheShortcutServiceAndPostsItsNotification() {
    ScreenTranslateNotifier.setEnabled(context, true)

    assertTrue("notification was not posted", awaitShowing())
    assertTrue("service is not running", awaitServiceRunning())
  }

  @Test
  fun turningItOffRemovesTheNotification() {
    ScreenTranslateNotifier.setEnabled(context, true)
    assertTrue(awaitShowing())

    ScreenTranslateNotifier.setEnabled(context, false)

    assertFalse("notification survived", awaitShowing())
  }

  /** The service is started asynchronously, so give it a moment to come up. */
  private fun awaitServiceRunning(): Boolean {
    repeat(40) {
      if (com.lingua.app.ui.screentranslate.ScreenTranslateService.running) return true
      Thread.sleep(50)
    }
    return com.lingua.app.ui.screentranslate.ScreenTranslateService.running
  }

  /** The notification manager updates its active list asynchronously. */
  private fun awaitShowing(): Boolean {
    repeat(20) {
      if (ScreenTranslateNotifier.isShowing(context)) return true
      Thread.sleep(50)
    }
    return ScreenTranslateNotifier.isShowing(context)
  }
}
