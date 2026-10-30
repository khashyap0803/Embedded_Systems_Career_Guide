package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiQuizService
import com.example.embeddedsystemscareerguide.services.NetworkModule
import com.example.embeddedsystemscareerguide.services.OllamaService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The quiz kept its own retry ladder. It retried a 429 on a fixed 1s/2s, so all
 * three attempts landed inside the window the gateway had just asked for and the
 * student got the generic fallback quiz; and it retried every 4xx except the
 * 401/403 it could find in the message text.
 */
class QuizRetryTest {

    private fun ladder(failure: () -> Exception, sleeps: MutableList<Long>): Pair<Exception?, Int> {
        var attempts = 0
        val error = try {
            runBlocking {
                GeminiQuizService.retryQuizCall(sleep = { sleeps.add(it) }) { attempts++; throw failure() }
            }
            fail("every attempt failed, so the ladder must throw")
            null
        } catch (e: Exception) {
            e
        }
        return error to attempts
    }

    @Test
    fun `a 429 waits out Retry-After, read as seconds`() {
        val sleeps = mutableListOf<Long>()
        val (error, attempts) = ladder({ OllamaService.httpFailureFor(429, "", "10") }, sleeps)
        assertEquals(3, attempts)
        assertEquals(listOf(10_000L, 10_000L), sleeps)
        assertEquals(NetworkModule.RATE_LIMITED_MESSAGE, error?.message)
    }

    @Test
    fun `a 404 for a missing model is not retried`() {
        val sleeps = mutableListOf<Long>()
        val (_, attempts) = ladder({ OllamaService.httpFailureFor(404, "Not Found", null) }, sleeps)
        assertEquals(1, attempts)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `a server error keeps the 1s then 2s backoff`() {
        val sleeps = mutableListOf<Long>()
        val (_, attempts) = ladder({ OllamaService.httpFailureFor(502, "Bad Gateway", null) }, sleeps)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun `a reply after a 429 is returned`() = runBlocking {
        var attempts = 0
        val reply = GeminiQuizService.retryQuizCall(sleep = {}) {
            if (++attempts == 1) throw OllamaService.httpFailureFor(429, "", "5")
            "[]"
        }
        assertEquals("[]", reply)
    }
}
