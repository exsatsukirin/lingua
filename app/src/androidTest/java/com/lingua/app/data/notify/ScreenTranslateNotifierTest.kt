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
    // Stopping a foreground service is asynchronous and can take a moment on a slow emulator;
    // wait for the previous test's service to be really gone before starting a new one.
    ScreenTranslateNotifier.setEnabled(context, enabled = false)
    awaitHidden()
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

    // A foreground service takes its notification down with it; that teardown is asynchronous, and
    // cancelling an FGS notification directly is ignored while the service is still foreground.
    assertTrue("notification survived", awaitHidden())
  }

  /** The service is started asynchronously, so give it a moment to come up. */
  private fun awaitServiceRunning(): Boolean {
    repeat(40) {
      if (com.lingua.app.ui.screentranslate.ScreenTranslateService.running) return true
      Thread.sleep(50)
    }
    return com.lingua.app.ui.screentranslate.ScreenTranslateService.running
  }

  /** Waits for a foreground service to finish stopping and drop its notification. */
  private fun awaitHidden(): Boolean {
    repeat(80) {
      if (!ScreenTranslateNotifier.isShowing(context)) return true
      Thread.sleep(50)
    }
    return ScreenTranslateNotifier.isShowing(context)
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
