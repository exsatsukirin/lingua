package com.lingua.app.data.remote

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Bridges OkHttp's callback API into a cancellable coroutine: cancelling the coroutine cancels the
 * in-flight call instead of leaving it to run to completion in the background.
 */
internal suspend fun Call.executeAsync(): Response =
  suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(
      object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          if (continuation.isCancelled) return
          continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
          if (continuation.isCancelled) {
            response.close()
            return
          }
          continuation.resume(response)
        }
      }
    )
  }
