package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.home.HomeFragment
import com.example.embeddedsystemscareerguide.ui.home.HomeFragment.AssessmentCardAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Home's Assessment card started a first-time assessment whenever it could not
 * read the student's report - skipping View Report and the retake warning, and
 * leading to an overwritten report and a path rebuilt without the retake's
 * progress reset. Leaving Home right after tapping it did the same.
 */
class AssessmentCardTest {

    private fun action(lookup: suspend () -> Boolean) = runBlocking { HomeFragment.assessmentCardAction(lookup) }

    @Test
    fun `a student with a report is offered view or retake`() {
        assertEquals(AssessmentCardAction.SHOW_OPTIONS, action { true })
    }

    @Test
    fun `a student without a report starts the assessment`() {
        assertEquals(AssessmentCardAction.START_ASSESSMENT, action { false })
    }

    @Test
    fun `a failed lookup does not start a first-time assessment`() {
        assertEquals(AssessmentCardAction.UNAVAILABLE, action { throw IOException("offline") })
    }

    @Test
    fun `leaving Home after tapping the card starts nothing`() {
        try {
            val result = action { throw CancellationException("left Home") }
            fail("cancellation must propagate, got $result")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
