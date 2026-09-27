package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiChallengeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The challenge ladder read Retry-After as milliseconds and then ignored it,
 * retrying a 429 on its own 1s/2s/4s backoff. A gateway asking for 30s got hit
 * again after ~1s and all five attempts were spent inside the window.
 */
class ChallengeRetryAfterTest {

    @Test
    fun `Retry-After is read as seconds, not milliseconds`() {
        assertEquals(30_000L, GeminiChallengeService.parseRetryAfterMs("30"))
        assertEquals(5_000L, GeminiChallengeService.parseRetryAfterMs(" 5 "))
    }

    @Test
    fun `unusable Retry-After yields null so the ladder backs off itself`() {
        assertNull(GeminiChallengeService.parseRetryAfterMs(null))
        assertNull(GeminiChallengeService.parseRetryAfterMs(""))
        assertNull(GeminiChallengeService.parseRetryAfterMs("0"))
        assertNull(GeminiChallengeService.parseRetryAfterMs("-3"))
        assertNull(GeminiChallengeService.parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `Retry-After is clamped without overflowing`() {
        assertEquals(60_000L, GeminiChallengeService.parseRetryAfterMs("3600"))
        val huge = GeminiChallengeService.parseRetryAfterMs(Long.MAX_VALUE.toString())
        assertEquals(60_000L, huge)
        assertTrue((huge ?: -1L) > 0L)
    }

    @Test
    fun `the server's wait wins, otherwise the backoff is used`() {
        assertEquals(30_000L, GeminiChallengeService.retryWaitMs(30_000L, 1_200L))
        assertEquals(1_200L, GeminiChallengeService.retryWaitMs(null, 1_200L))
    }
}
