package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.NetworkModule
import com.example.embeddedsystemscareerguide.services.OllamaService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * The timeout and connect-failure branches of OllamaService's retry ladder
 * skipped the wait that the generic branch had, so a refused connection spent
 * all its attempts within milliseconds.
 */
class OllamaRetryBackoffTest {

    private fun ladder(failure: () -> Exception, sleeps: MutableList<Long>) = runBlocking {
        OllamaService.retryWithBackoff(
            maxRetries = 3,
            initialDelayMs = 1_000L,
            sleep = { sleeps.add(it) }
        ) { throw failure() }
    }

    @Test
    fun `a timeout backs off before retrying`() {
        val sleeps = mutableListOf<Long>()
        val result = ladder({ SocketTimeoutException("read timed out") }, sleeps)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals(NetworkModule.SERVER_DOWN_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `a refused connection backs off before retrying`() {
        val sleeps = mutableListOf<Long>()
        val result = ladder({ ConnectException("refused") }, sleeps)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals(NetworkModule.SERVER_DOWN_MESSAGE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `other failures keep their existing backoff and message`() {
        val sleeps = mutableListOf<Long>()
        val result = ladder({ IllegalStateException("bad json") }, sleeps)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals("bad json", result.exceptionOrNull()?.message)
    }

    @Test
    fun `success after a timeout is returned and no wait follows it`() = runBlocking {
        val sleeps = mutableListOf<Long>()
        var attempts = 0
        val result = OllamaService.retryWithBackoff(sleep = { sleeps.add(it) }) {
            attempts++
            if (attempts == 1) throw SocketTimeoutException("slow")
            "generated"
        }
        assertEquals("generated", result.getOrNull())
        assertEquals(2, attempts)
        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun `cancellation propagates at once and is never retried`() = runBlocking {
        val sleeps = mutableListOf<Long>()
        var attempts = 0
        try {
            OllamaService.retryWithBackoff(sleep = { sleeps.add(it) }) {
                attempts++
                throw kotlinx.coroutines.CancellationException("left the screen")
            }
            fail("cancellation must propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // expected
        }
        assertEquals(1, attempts)
        assertTrue("must not sleep on a cancellation", sleeps.isEmpty())
    }
}
