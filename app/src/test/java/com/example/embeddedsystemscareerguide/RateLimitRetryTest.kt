package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiReportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The gateway's per-uid cap was restored on 2026-08-05 after a crash had been
 * clearing its counter, so 429 is reachable in production for the first time.
 *
 * Two things must hold. A 429 is the one 4xx worth repeating, so it has to reach
 * the retry ladder rather than failing its chunk outright and degrading the
 * report. Every other 4xx must keep failing fast - retrying a malformed request
 * three times is just slower.
 */
class RateLimitRetryTest {

    private val svc = GeminiReportService()

    // ---- Retry-After parsing -------------------------------------------------

    @Test
    fun `Retry-After is read as seconds and returned as milliseconds`() {
        assertEquals(5_000L, svc.parseRetryAfterMs("5"))
        assertEquals(30_000L, svc.parseRetryAfterMs("30"))
    }

    @Test
    fun `Retry-After tolerates surrounding whitespace`() {
        assertEquals(5_000L, svc.parseRetryAfterMs("  5  "))
    }

    @Test
    fun `absent or unusable Retry-After yields null so the caller backs off itself`() {
        assertNull(svc.parseRetryAfterMs(null))
        assertNull(svc.parseRetryAfterMs(""))
        // The HTTP-date form is legal but this gateway does not emit it; guessing
        // at it would be worse than falling back to the exponential delay.
        assertNull(svc.parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(svc.parseRetryAfterMs("soon"))
    }

    @Test
    fun `a zero or negative Retry-After does not become a busy loop`() {
        assertNull(svc.parseRetryAfterMs("0"))
        assertNull(svc.parseRetryAfterMs("-10"))
    }

    @Test
    fun `an absurd Retry-After is clamped rather than parking the student`() {
        assertEquals(60_000L, svc.parseRetryAfterMs("3600"))
        // Long.MAX_VALUE seconds must not overflow into a negative delay.
        val huge = svc.parseRetryAfterMs(Long.MAX_VALUE.toString())
        assertEquals(60_000L, huge)
        assertTrue("clamped delay must be positive", (huge ?: -1L) > 0L)
    }

    @Test
    fun `a tiny Retry-After is lifted to the floor`() {
        assertEquals(1_000L, svc.parseRetryAfterMs("1"))
    }

    // ---- Which delay gets used ----------------------------------------------

    @Test
    fun `the server's Retry-After wins when it gave one`() {
        assertEquals(5_000L, svc.rateLimitWaitMs(retryAfterMs = 5_000L, exponentialMs = 1_000L))
    }

    @Test
    fun `a missing Retry-After falls back to exponential backoff`() {
        assertEquals(1_000L, svc.rateLimitWaitMs(retryAfterMs = null, exponentialMs = 1_000L))
        assertEquals(2_000L, svc.rateLimitWaitMs(retryAfterMs = null, exponentialMs = 2_000L))
        assertEquals(4_000L, svc.rateLimitWaitMs(retryAfterMs = null, exponentialMs = 4_000L))
    }

    // ---- The wall-clock budget still bounds everything -----------------------

    @Test
    fun `a wait that fits the report budget is allowed`() {
        val now = 0L
        val deadline = 10L * 1_000_000_000L // 10s away, in nanos
        assertTrue(svc.rateLimitFitsBudget(waitMs = 1_000L, nowNanos = now, deadlineNanos = deadline))
    }

    @Test
    fun `a wait that would outlast the report budget is refused`() {
        val now = 0L
        val deadline = 10L * 1_000_000_000L
        assertTrue(!svc.rateLimitFitsBudget(waitMs = 60_000L, nowNanos = now, deadlineNanos = deadline))
    }

    @Test
    fun `with no deadline set every wait is allowed`() {
        assertTrue(svc.rateLimitFitsBudget(waitMs = 60_000L, nowNanos = 0L, deadlineNanos = null))
    }

    // ---- Status classification ----------------------------------------------

    @Test
    fun `429 is classified as retryable and carries the parsed delay`() {
        val e = svc.httpFailureFor(429, "5")
        assertTrue(
            "429 must not be a ClientErrorException or the ladder rethrows it",
            e is GeminiReportService.RateLimitedException
        )
        assertEquals(5_000L, (e as GeminiReportService.RateLimitedException).retryAfterMs)
    }

    @Test
    fun `the other 4xx codes still fail fast`() {
        for (code in listOf(400, 401, 403, 404)) {
            val e = svc.httpFailureFor(code, null)
            assertTrue(
                "$code must stay a ClientErrorException",
                e is GeminiReportService.ClientErrorException
            )
        }
    }

    @Test
    fun `5xx stays generic so the existing ladder retries it`() {
        val e = svc.httpFailureFor(502, null)
        assertTrue(e !is GeminiReportService.ClientErrorException)
        assertTrue(e !is GeminiReportService.RateLimitedException)
    }

    // ---- The ladder itself ---------------------------------------------------

    @Test
    fun `a 429 is retried and the eventual success is returned`() = runBlocking {
        var attempts = 0
        val result = svc.callGeminiAPIWithRetry(
            prompt = "p",
            maxRetries = 3
        ) { _, _, _ ->
            attempts++
            if (attempts < 3) {
                throw GeminiReportService.RateLimitedException("429", retryAfterMs = 1_000L)
            }
            "generated"
        }
        assertEquals("generated", result)
        assertEquals("both 429s should have been retried", 3, attempts)
    }

    @Test
    fun `a 4xx that is not 429 is not retried`() = runBlocking {
        var attempts = 0
        try {
            svc.callGeminiAPIWithRetry(prompt = "p", maxRetries = 3) { _, _, _ ->
                attempts++
                throw GeminiReportService.ClientErrorException("API call failed: 400")
            }
            fail("a 400 should have propagated instead of being retried")
        } catch (e: GeminiReportService.ClientErrorException) {
            // expected
        }
        assertEquals("a 400 must cost exactly one attempt", 1, attempts)
    }

    @Test
    fun `429 retries stop at MAX_RETRIES rather than looping`() = runBlocking {
        var attempts = 0
        try {
            svc.callGeminiAPIWithRetry(prompt = "p", maxRetries = 3) { _, _, _ ->
                attempts++
                throw GeminiReportService.RateLimitedException("429", retryAfterMs = 1_000L)
            }
            fail("a persistent 429 should eventually give up")
        } catch (e: GeminiReportService.RateLimitedException) {
            // expected
        }
        assertEquals("must not exceed the retry count", 3, attempts)
    }

    @Test
    fun `a 429 whose wait outlasts the budget gives up without sleeping`() = runBlocking {
        var attempts = 0
        val startedAt = System.nanoTime()
        // Inside the budget, but with only 5s of it left - so the call is allowed
        // to go out and the 60s Retry-After is what cannot fit.
        //
        // This used to use a deadline already in the past. That stopped
        // exercising this rule once the ladder learned to check the budget
        // BEFORE dialling rather than only after a failure: an expired deadline
        // now short-circuits at the loop head and never reaches a 429 at all
        // (see ReportBudgetTest). The rule under test here is ecg-009's, that a
        // wait outlasting the budget is refused rather than slept, and it needs
        // a call that actually happens.
        val deadline = System.nanoTime() + 5_000_000_000L
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = deadline
            ) { _, _, _ ->
                attempts++
                throw GeminiReportService.RateLimitedException("429", retryAfterMs = 60_000L)
            }
            fail("an out-of-budget 429 should propagate")
        } catch (e: GeminiReportService.RateLimitedException) {
            // expected
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        assertEquals("must not retry past the budget", 1, attempts)
        assertTrue(
            "must not have slept the 60s Retry-After; took ${elapsedMs}ms",
            elapsedMs < 5_000L
        )
    }

    @Test
    fun `cancellation is still rethrown and never becomes a retry`() = runBlocking {
        var attempts = 0
        try {
            svc.callGeminiAPIWithRetry(prompt = "p", maxRetries = 3) { _, _, _ ->
                attempts++
                throw kotlinx.coroutines.CancellationException("student left the screen")
            }
            fail("cancellation must propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // expected
        }
        assertEquals("cancellation must not be retried", 1, attempts)
    }
}
