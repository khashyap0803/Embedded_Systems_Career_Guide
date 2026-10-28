package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.NetworkModule
import com.example.embeddedsystemscareerguide.services.OllamaService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OllamaService - behind stage generation, lesson content, daily tips,
 * flashcards, interview prep and project ideas - folded every non-2xx into one
 * generic "API error". A 429 was retried on the ladder's own 1s/2s, so all three
 * attempts landed inside the window the gateway had just asked for, and a 401,
 * 403 or 404 was retried as if waiting could change the answer.
 */
class OllamaRateLimitTest {

    private fun ladder(failure: () -> Exception, sleeps: MutableList<Long>): Pair<Result<String>, Int> {
        var attempts = 0
        val result = runBlocking {
            OllamaService.retryWithBackoff(
                maxRetries = 3,
                initialDelayMs = 1_000L,
                sleep = { sleeps.add(it) }
            ) { attempts++; throw failure() }
        }
        return result to attempts
    }

    @Test
    fun `a 429 waits out the server's Retry-After`() {
        val sleeps = mutableListOf<Long>()
        val (result, attempts) = ladder({ OllamaService.httpFailureFor(429, "", "30") }, sleeps)
        assertEquals(3, attempts)
        assertEquals(listOf(30_000L, 30_000L), sleeps)
        assertEquals(NetworkModule.RATE_LIMITED_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `a 429 without a usable Retry-After keeps the ladder's backoff`() {
        val sleeps = mutableListOf<Long>()
        ladder({ OllamaService.httpFailureFor(429, "", null) }, sleeps)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun `other client errors are not retried`() {
        listOf(401, 403, 404).forEach { code ->
            val sleeps = mutableListOf<Long>()
            val (result, attempts) = ladder({ OllamaService.httpFailureFor(code, "", null) }, sleeps)
            assertEquals("HTTP $code", 1, attempts)
            assertTrue("HTTP $code must not sleep", sleeps.isEmpty())
            assertEquals("API error: $code - ", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `server errors keep retrying with the old message`() {
        val sleeps = mutableListOf<Long>()
        val (result, attempts) = ladder({ OllamaService.httpFailureFor(502, "Bad Gateway", null) }, sleeps)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals("API error: 502 - Bad Gateway", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Retry-After is read as seconds`() {
        val e = OllamaService.httpFailureFor(429, "", "5") as OllamaService.RateLimitedException
        assertEquals(5_000L, e.retryAfterMs)
        assertNull((OllamaService.httpFailureFor(429, "", "soon") as OllamaService.RateLimitedException).retryAfterMs)
    }
}
