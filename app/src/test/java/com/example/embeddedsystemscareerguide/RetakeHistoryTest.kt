package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.WrongQuestionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A retake promises "topics you struggled with will get more focus". The
 * history behind that read completion and stars off the stage documents, where
 * nothing ever writes them (completion lives in the progress document), from a
 * collection nothing writes to either - so every retake was regenerated as if
 * the student had never studied.
 */
class RetakeHistoryTest {

    // As stored: savePersonalizedStages writes isCompleted = false and it is never updated.
    private val stages = listOf(
        PersonalizedStage(id = 1, title = "GPIO", topics = listOf("GPIO", "Registers"), xpReward = 100),
        PersonalizedStage(id = 2, title = "Timers", topics = listOf("Timers", "PWM"), xpReward = 150),
        PersonalizedStage(id = 3, title = "UART", topics = listOf("UART"), xpReward = 200)
    )

    /** The progress document exactly as UserProgressSyncService writes it, read back by Firestore. */
    private val progressDocument: Map<String, Any?> = UserProgressSyncService.UserProgress(
        totalXP = 250,
        completedStages = listOf("1", "2"),
        stageStars = mapOf("1" to 1, "2" to 3)
    ).toMap().mapValues { (key, value) ->
        // Firestore hands numbers back as Long.
        if (key == "stageStars") (value as Map<*, *>).mapValues { (it.value as Int).toLong() } else value
    }

    @Test
    fun `completion and stars come from the progress document`() {
        val (completed, stars) = FirestoreManager.completionFrom(progressDocument)
        assertEquals(listOf("1", "2"), completed)
        assertEquals(mapOf("1" to 1, "2" to 3), stars)
    }

    @Test
    fun `the retake sees what the student struggled with and what they mastered`() {
        val (completed, stars) = FirestoreManager.completionFrom(progressDocument)
        val history = FirestoreManager.performanceFrom(
            stages, completed, stars,
            quizScores = mapOf("1" to 40, "2" to 100),
            wrongQuestions = listOf(WrongQuestionRecord(stageId = "1", topic = "Pull-ups"))
        )

        assertEquals(listOf("GPIO", "Registers", "Pull-ups"), history.weakTopics)
        assertEquals(listOf("Timers", "PWM"), history.strongTopics)
        assertEquals(250, history.totalXpEarned)
        assertEquals(70, history.averageQuizScore)
        assertEquals(listOf("1", "2"), history.completedStageIds)
    }

    @Test
    fun `a missing progress document is an empty history`() {
        val (completed, stars) = FirestoreManager.completionFrom(emptyMap())
        val history = FirestoreManager.performanceFrom(stages, completed, stars, emptyMap(), emptyList())
        assertEquals(emptyList<String>(), history.weakTopics)
        assertEquals(0, history.totalXpEarned)
    }
}
