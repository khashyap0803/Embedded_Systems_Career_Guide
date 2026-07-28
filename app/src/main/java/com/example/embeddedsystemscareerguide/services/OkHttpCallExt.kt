package com.example.embeddedsystemscareerguide.services

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits this call, tearing the request down if the coroutine is cancelled.
 *
 * OkHttp's blocking `execute()` cannot be interrupted, so cancelling the calling
 * coroutine did nothing until the read timeout elapsed - minutes per attempt on
 * the long-timeout client - while the request kept occupying the tunnel and the
 * caller's chain stayed alive. Enqueuing instead, and cancelling the Call when
 * the coroutine is cancelled, ends it immediately.
 *
 * The returned [Response] owns a live body, so callers must close it - normally
 * by calling this directly inside `use { }`.
 */
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            // A cancelled coroutine never reaches the caller's use{}, so an
            // already-completed continuation has to close the body here or the
            // connection leaks for the life of the pool.
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
