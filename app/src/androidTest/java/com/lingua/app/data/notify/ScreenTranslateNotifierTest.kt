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
    ScreenTranslateNotifier.cancel(context)
    Thread.sleep(150)
  }

  @After
  fun cleanUp() {
    ScreenTranslateNotifier.cancel(context)
  }

  @Test
  fun enablingPostsAResidentNotification() {
    ScreenTranslateNotifier.setEnabled(context, true)

    assertTrue("notification was not posted", awaitShowing())
  }

  @Test
  fun turningItOffRemovesTheNotification() {
    ScreenTranslateNotifier.setEnabled(context, true)
    assertTrue(awaitShowing())

    ScreenTranslateNotifier.setEnabled(context, false)

    assertFalse("notification survived", awaitShowing())
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
