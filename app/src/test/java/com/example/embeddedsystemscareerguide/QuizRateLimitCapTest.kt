package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiQuizService
import com.example.embeddedsystemscareerguide.services.OllamaService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Under sustained 429s the quiz waited each Retry-After (up to 60s) twice per
 * batch, for two batches - up to four minutes on the loading screen - and then
 * showed the generic fallback questions as if they were the stage quiz.
 */
class QuizRateLimitCapTest {

    private val service = GeminiQuizService()

    /** One batch of five playable questions, as the model returns it. */
    private val batch = (1..5).joinToString(",", "[", "]") {
        """{"question":"Timer question $it?","options":["a","b","c","d"],"correctAnswerIndex":1,"explanation":"b."}"""
    }

    private fun quiz(sleeps: MutableList<Long>, request: () -> String) = runBlocking {
        service.generateQuiz("Timers", numberOfQuestions = 10, sleep = { sleeps.add(it) }) { request() }
    }

    private fun rateLimited(retryAfterSeconds: String) = OllamaService.httpFailureFor(429, "", retryAfterSeconds)

    @Test
    fun `sustained 429s stop at 30 seconds of waiting and give the fallback quiz`() {
        val sleeps = mutableListOf<Long>()
        var requests = 0
        val quiz = quiz(sleeps) { requests++; throw rateLimited("20") }

        assertEquals(listOf(20_000L), sleeps) // a second 20s wait would pass 30s
        assertEquals(2, requests) // and the second batch is never asked for
        assertTrue(quiz.isFallback)
        assertEquals(10, quiz.questions.size)
    }

    @Test
    fun `a Retry-After longer than the cap is not waited at all`() {
        val sleeps = mutableListOf<Long>()
        var requests = 0
        val quiz = quiz(sleeps) { requests++; throw rateLimited("60") }

        assertTrue(sleeps.isEmpty())
        assertEquals(1, requests)
        assertTrue(quiz.isFallback)
    }

    @Test
    fun `the cap is for the whole quiz, not each batch`() {
        val sleeps = mutableListOf<Long>()
        val quiz = quiz(sleeps) { throw rateLimited("8") }

        assertEquals(listOf(8_000L, 8_000L, 8_000L), sleeps)
        assertTrue(sleeps.sum() <= GeminiQuizService.RATE_LIMIT_WAIT_CAP_MS)
        assertTrue(quiz.isFallback)
    }

    @Test
    fun `backoff after other failures is not charged to the cap`() {
        val budget = GeminiQuizService.RateLimitBudget()
        var attempts = 0
        val reply = runBlocking {
            GeminiQuizService.retryQuizCall(budget, sleep = {}) {
                if (++attempts < 3) throw OllamaService.httpFailureFor(502, "Bad Gateway", null)
                "[]"
            }
        }

        assertEquals("[]", reply)
        assertEquals(0L, budget.waitedMs)
    }

    @Test
    fun `a quiz the model made is not the fallback`() {
        val quiz = quiz(mutableListOf()) { batch }

        assertFalse(quiz.isFallback)
        assertEquals(10, quiz.questions.size)
        assertEquals("Timer question 1?", quiz.questions.first().question)
    }

    @Test
    fun `a quiz the model could not make at all is the fallback`() {
        val quiz = quiz(mutableListOf()) { throw OllamaService.httpFailureFor(404, "Not Found", null) }

        assertTrue(quiz.isFallback)
        assertEquals(10, quiz.questions.size)
    }
}
