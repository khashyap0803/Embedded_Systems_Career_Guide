package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.ui.practice.PracticeFragment
import com.example.embeddedsystemscareerguide.ui.practice.PracticeFragment.QuickQuiz
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Quick Practice turned a failed read of the learning path into an empty list,
 * so a student who had a path but a bad connection was told to "complete your
 * assessment first".
 */
class QuickQuizTest {

    private val gpio = PersonalizedStage(id = 1, title = "GPIO", isUnlocked = true)

    @Test
    fun `a stored path starts a quiz`() {
        assertEquals(QuickQuiz.Start(gpio), PracticeFragment.quickQuizFor(Result.success(listOf(gpio))))
    }

    @Test
    fun `no path yet asks for the assessment`() {
        assertEquals(QuickQuiz.NeedsAssessment, PracticeFragment.quickQuizFor(Result.success(emptyList())))
    }

    @Test
    fun `a failed read is not a missing path`() {
        val quiz = PracticeFragment.quickQuizFor(Result.failure(IOException("offline")))
        assertTrue("was $quiz", quiz is QuickQuiz.Unavailable)
    }

    @Test
    fun `a cancelled read is not reported at all`() {
        try {
            val quiz = PracticeFragment.quickQuizFor(Result.failure(CancellationException("left Practice")))
            fail("cancellation must propagate, got $quiz")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
