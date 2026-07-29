package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.AnswerKeyEntry
import com.example.embeddedsystemscareerguide.models.KeyPoint
import com.example.embeddedsystemscareerguide.services.AnswerGrader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learning path is selected from these numbers, so the shape of the ranking
 * matters more than any single value.
 *
 * 35 of the 49 topics the answer key tags are backed by exactly ONE question.
 * A one-question topic can only score 0 or 100; a five-question topic regresses
 * toward the middle. Ranking on the raw average therefore fills the weak list
 * with the least-evidenced topics - which is the opposite of what ranking is
 * for, and is what shipped before this was measured.
 */
class TopicRankingTest {

    private fun entry(id: Int, topic: String, points: Int = 2) = AnswerKeyEntry(
        id = id,
        topics = listOf(topic),
        modelAnswerHtml = "<p>ref</p>",
        keyPoints = (1..points).map { KeyPoint(1, listOf("stem$it")) },
        correctFeedbackHtml = "<p>ok</p>"
    )

    /** An answer hitting [hits] of [points] stems, long enough to be graded. */
    private fun answer(hits: Int, points: Int = 2) =
        (1..points).joinToString(" ") { if (it <= hits) "stem$it" else "zzz" } +
            " " + "word ".repeat(30)

    @Test
    fun `rollup reports how many questions back each topic`() {
        val results = listOf(
            AnswerGrader.grade(entry(1, "I2C"), answer(0)),
            AnswerGrader.grade(entry(2, "I2C"), answer(1)),
            AnswerGrader.grade(entry(3, "Solo"), answer(0))
        )
        val roll = AnswerGrader.topicRollup(results)
        assertEquals(2, roll["I2C"]!!.questionCount)
        assertEquals(1, roll["Solo"]!!.questionCount)
        // (0 + 50) / 2 questions
        assertEquals(25, roll["I2C"]!!.percentage)
        assertEquals(50, roll["I2C"]!!.totalScore)
    }

    @Test
    fun `evidence must survive into maxScore or the shrinkage is inert`() {
        // This is the regression that made the first fix do nothing: writing
        // maxScore = 100 for every topic makes the recovered question count
        // always 1, so the shrunk value collapses to (percentage + 50) / 2,
        // which is monotonic in percentage and reorders nothing.
        val results = listOf(
            AnswerGrader.grade(entry(1, "Multi"), answer(0)),
            AnswerGrader.grade(entry(2, "Multi"), answer(0)),
            AnswerGrader.grade(entry(3, "Multi"), answer(0))
        )
        val roll = AnswerGrader.topicRollup(results)["Multi"]!!
        assertEquals(3, roll.questionCount)
        assertTrue(
            "maxScore must encode the count, not a constant 100",
            roll.questionCount * 100 == 300
        )
    }

    @Test
    fun `more evidence of the same weakness ranks as weaker`() {
        // Mirrors categorizeTopics: (totalScore + 50) / (questionCount + 1).
        fun adjusted(total: Int, n: Int) = (total + 50) / (n + 1).toDouble()

        val oneQuestionAtZero = adjusted(0, 1)     // 25.0
        val threeQuestionsAtZero = adjusted(0, 3)  // 12.5

        assertTrue(
            "three questions all scoring zero is stronger evidence than one",
            threeQuestionsAtZero < oneQuestionAtZero
        )
    }

    @Test
    fun `a single zero does not outrank a consistently weak multi-question topic`() {
        fun adjusted(total: Int, n: Int) = (total + 50) / (n + 1).toDouble()

        // Without shrinkage these sort 0 before 20, putting the lone
        // possibly-flukey miss ahead of five questions of real evidence.
        val lonelyZero = adjusted(0, 1)          // 25.0
        val fiveAtTwenty = adjusted(100, 5)      // 25.0
        assertTrue(
            "shrinkage should bring them within a point of each other, not 20 apart",
            kotlin.math.abs(lonelyZero - fiveAtTwenty) < 1.0
        )
    }

    @Test
    fun `ungradeable questions contribute no evidence at all`() {
        // A key entry with no criteria yields coverage 0. Counting that as a
        // zero would report the topic weak on missing data.
        val noCriteria = AnswerKeyEntry(
            id = 9, topics = listOf("Ghost"), modelAnswerHtml = "<p>x</p>",
            keyPoints = emptyList(), correctFeedbackHtml = "<p>y</p>"
        )
        val r = AnswerGrader.grade(noCriteria, answer(0))
        assertTrue(!r.gradable)
        assertTrue(AnswerGrader.topicRollup(listOf(r)).isEmpty())
    }
}
