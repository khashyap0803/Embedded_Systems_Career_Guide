package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.practice.PracticeFragment
import com.example.embeddedsystemscareerguide.ui.practice.PracticeFragment.QuickQuiz
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Practice, flashcards, daily tips, projects and analytics read completion off the
 * stage documents, where it is written once at generation (stage 1 unlocked,
 * nothing completed) and never updated. Completion lives in the progress
 * document, so every student was treated as being on stage 1.
 */
class CurrentStageTest {

    // As stored: what savePersonalizedStages writes and nothing updates.
    private val stored = listOf(
        PersonalizedStage(id = 1, title = "GPIO", xpReward = 100, isUnlocked = true),
        PersonalizedStage(id = 2, title = "Timers", xpReward = 150),
        PersonalizedStage(id = 3, title = "UART", xpReward = 200),
        PersonalizedStage(id = 4, title = "SPI", xpReward = 200)
    )

    /** The progress document as UserProgressSyncService writes it, read back by Firestore. */
    private fun progressDocument(completed: List<String>, stars: Map<String, Int>): Map<String, Any?> =
        UserProgressSyncService.UserProgress(completedStages = completed, stageStars = stars)
            .toMap().mapValues { (key, value) ->
                // Firestore hands numbers back as Long.
                if (key == "stageStars") (value as Map<*, *>).mapValues { (it.value as Int).toLong() } else value
            }

    private val twoDone = progressDocument(listOf("1", "2"), mapOf("1" to 1, "2" to 3))

    private fun load(progress: suspend () -> Map<String, Any?>) = runBlocking {
        FirestoreManager.stagesWithProgress(loadStages = { Result.success(stored) }, loadProgress = progress)
    }

    @Test
    fun `completion, stars and unlocking come from the progress document`() {
        val stages = load { twoDone }.getOrThrow()

        assertEquals(listOf(true, true, false, false), stages.map { it.isCompleted })
        assertEquals(listOf(1, 3, 0, 0), stages.map { it.starsEarned })
        assertEquals(listOf(true, true, true, false), stages.map { it.isUnlocked })
        assertEquals(250, stages.filter { it.isCompleted }.sumOf { it.xpReward })
    }

    @Test
    fun `a student who completed two stages is on stage 3`() {
        val stages = load { twoDone }

        assertEquals(3, FirestoreManager.currentStageOf(stages.getOrThrow())?.id)
        assertEquals(QuickQuiz.Start(stages.getOrThrow()[2]), PracticeFragment.quickQuizFor(stages))
    }

    @Test
    fun `a student with no completed stages is on stage 1`() {
        // No progress document yet reads as an empty map.
        val stages = load { emptyMap() }

        assertEquals(1, FirestoreManager.currentStageOf(stages.getOrThrow())?.id)
        assertEquals(QuickQuiz.Start(stages.getOrThrow()[0]), PracticeFragment.quickQuizFor(stages))
    }

    @Test
    fun `a student who completed every stage stays on the last one`() {
        val stages = load { progressDocument(listOf("1", "2", "3", "4"), emptyMap()) }.getOrThrow()

        assertEquals(4, FirestoreManager.currentStageOf(stages)?.id)
    }

    @Test
    fun `no path means no current stage`() {
        assertEquals(null, FirestoreManager.currentStageOf(emptyList()))
    }

    @Test
    fun `a failed progress read is not "nothing completed"`() {
        val stages = load { throw IOException("offline") }

        assertTrue(stages.isFailure)
        assertTrue(PracticeFragment.quickQuizFor(stages) is QuickQuiz.Unavailable)
    }

    @Test
    fun `a failed stage read skips the progress read`() {
        var progressRead = false
        val stages = runBlocking {
            FirestoreManager.stagesWithProgress(
                loadStages = { Result.failure(IOException("offline")) },
                loadProgress = { progressRead = true; twoDone }
            )
        }

        assertTrue(stages.isFailure)
        assertFalse(progressRead)
    }

    @Test
    fun `a cancelled progress read propagates`() {
        try {
            val stages = load { throw CancellationException("left Practice") }
            fail("cancellation must propagate, got $stages")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
