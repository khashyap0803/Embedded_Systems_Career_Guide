package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiQuizService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The quiz parser handed Gson's output straight to QuizActivity. Gson ignores
 * Kotlin nullability and reads a trailing comma as a null element, so ordinary
 * model slips - the prompt itself pleads "NO trailing commas" - became an NPE
 * mid-quiz, stale answer cards, or a question no answer could satisfy.
 */
class QuizParsingTest {

    private val service = GeminiQuizService()

    private fun q(text: String, correct: Int = 2, options: String = """["static","const","volatile","register"]""") =
        """{"question":"$text","options":$options,"correctAnswerIndex":$correct,"explanation":"Because."}"""

    /** What QuizActivity dereferences, checked without going through the fix itself. */
    @Suppress("SENSELESS_COMPARISON")
    private fun assertRenderable(question: GeminiQuizService.QuizQuestion?) {
        assertNotNull("a null question crashes QuizActivity", question)
        question!!
        assertTrue("question text", question.question != null && question.question.isNotBlank())
        assertTrue("four options for four cards", question.options != null && question.options.size == 4)
        assertTrue("answer index must point at an option", question.correctAnswerIndex in 0..3)
    }

    @Test
    fun `a trailing comma does not smuggle a null question into the quiz`() {
        val parsed = service.parseQuizResponse("[${q("Which keyword stops register caching?")},]")
        assertEquals(5, parsed.size)
        parsed.forEach(::assertRenderable)
        assertEquals("Which keyword stops register caching?", parsed.first().question)
    }

    @Test
    fun `questions the screen cannot show or mark are dropped`() {
        val parsed = service.parseQuizResponse(
            "[" + listOf(
                q("Good one"),
                """{"question":"No options","correctAnswerIndex":0,"explanation":"x"}""",
                q("Index past the options", correct = 4),
                q("Negative index", correct = -1),
                q("Three options", options = """["a","b","c"]"""),
                q("Blank option", options = """["a","","c","d"]""")
            ).joinToString(",") + "]"
        )
        parsed.forEach(::assertRenderable)
        assertEquals("Good one", parsed.first().question)
        assertFalse(parsed.any { it.question in setOf("No options", "Index past the options", "Negative index", "Three options", "Blank option") })
    }

    @Test
    fun `nothing playable is a failed batch, not an empty quiz`() {
        try {
            service.parseQuizResponse("[${q("Index past the options", correct = 7)}]")
            fail("expected the batch to fail so generateQuiz falls back")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `the fallback questions are themselves playable`() {
        assertTrue(GeminiQuizService.isPlayable(service.parseQuizResponse("[${q("Good one")}]").last()))
        assertFalse(GeminiQuizService.isPlayable(null))
    }
}
