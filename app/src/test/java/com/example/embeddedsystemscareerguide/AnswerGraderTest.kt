package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.AnswerKeyEntry
import com.example.embeddedsystemscareerguide.models.KeyPoint
import com.example.embeddedsystemscareerguide.models.Misconception
import com.example.embeddedsystemscareerguide.services.AnswerGrader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grading is the one place where being wrong is worse than being slow: a false
 * CORRECT tells a student their answer was right and denies them the
 * correction. These cover the decision boundary rather than the happy path.
 */
class AnswerGraderTest {

    private fun entry(
        id: Int = 1,
        topics: List<String> = listOf("Volatile & Const Qualifiers"),
        keyPoints: List<KeyPoint> = listOf(
            KeyPoint(3, listOf("optimis", "optimiz")),
            KeyPoint(3, listOf("hardware regist", "memory-mapped")),
            KeyPoint(2, listOf("isr", "interrupt")),
            KeyPoint(2, listOf("re-read", "each time"))
        ),
        misconceptions: List<Misconception> = emptyList()
    ) = AnswerKeyEntry(
        id = id, topics = topics, modelAnswerHtml = "<p>reference</p>",
        keyPoints = keyPoints, correctFeedbackHtml = "<p>ok</p>",
        misconceptions = misconceptions
    )

    private fun words(n: Int) = (1..n).joinToString(" ") { "word$it" }

    @Test
    fun `blank answer is unanswered, not wrong`() {
        val r = AnswerGrader.grade(entry(), "   ")
        assertEquals(AnswerGrader.Verdict.UNANSWERED, r.verdict)
        assertEquals(0, r.score)
    }

    @Test
    fun `a few words is unanswered rather than graded`() {
        assertEquals(
            AnswerGrader.Verdict.UNANSWERED,
            AnswerGrader.grade(entry(), "I don't know").verdict
        )
    }

    @Test
    fun `full coverage with enough substance certifies as correct`() {
        val answer = "The volatile keyword stops the compiler optimising away reads of a " +
            "hardware register. Without it a value updated by an ISR or an interrupt is " +
            "cached, so the code must re-read it each time it is used in the loop body."
        val r = AnswerGrader.grade(entry(), answer)
        assertEquals(AnswerGrader.Verdict.CORRECT, r.verdict)
        assertEquals(100, r.score)
    }

    @Test
    fun `full coverage but too short does NOT certify`() {
        // Every key point present, under the word floor. Keyword coverage alone
        // cannot establish that a one-liner actually explained anything.
        val terse = "optimis hardware regist isr re-read"
        val r = AnswerGrader.grade(entry(), terse)
        assertTrue(r.coverage > 0.99)
        assertEquals(AnswerGrader.Verdict.NEEDS_MODEL, r.verdict)
    }

    @Test
    fun `a misconception blocks certification even at full coverage`() {
        val e = entry(
            misconceptions = listOf(Misconception(listOf("volatile is atomic"), "not atomic"))
        )
        val answer = "Volatile stops the compiler optimising reads of a hardware register " +
            "updated by an interrupt, so it must re-read it each time. Also volatile is " +
            "atomic so it is safe to share between threads without a lock at all times."
        val r = AnswerGrader.grade(e, answer)
        assertTrue(r.coverage > 0.99)
        assertEquals(AnswerGrader.Verdict.NEEDS_MODEL, r.verdict)
        assertEquals(listOf("not atomic"), r.matchedMisconceptions)
    }

    @Test
    fun `partial coverage routes to the model`() {
        val answer = "Volatile has something to do with the compiler optimising things. " +
            "${words(30)}"
        assertEquals(AnswerGrader.Verdict.NEEDS_MODEL, AnswerGrader.grade(entry(), answer).verdict)
    }

    @Test
    fun `long stems match inflected forms`() {
        // "optimis" must cover optimising and optimised - that is why the key
        // stores truncated stems rather than whole words.
        val r = AnswerGrader.grade(
            entry(keyPoints = listOf(KeyPoint(3, listOf("optimis")))),
            "the compiler optimised it away ${words(30)}"
        )
        assertTrue(r.coverage > 0.99)
    }

    @Test
    fun `short stems require a word boundary and do not match inside words`() {
        // The regression this guards: "ss" as a substring hits "process",
        // "assess" and "less", inflating coverage on an answer that never
        // mentioned the SPI slave-select pin.
        val e = entry(keyPoints = listOf(KeyPoint(3, listOf("ss"))))
        val bogus = "the process will assess less data than ${words(30)}"
        assertEquals(0.0, AnswerGrader.grade(e, bogus).coverage, 0.001)

        val real = "the ss line is pulled low by the master ${words(30)}"
        assertTrue(AnswerGrader.grade(e, real).coverage > 0.99)
    }

    @Test
    fun `weighting means a must-have point counts more than a detail`() {
        val e = entry(keyPoints = listOf(KeyPoint(3, listOf("alpha")), KeyPoint(1, listOf("beta"))))
        val onlyHeavy = AnswerGrader.grade(e, "alpha ${words(30)}").coverage
        val onlyLight = AnswerGrader.grade(e, "beta ${words(30)}").coverage
        assertTrue("weight-3 point should dominate", onlyHeavy > onlyLight)
        assertEquals(0.75, onlyHeavy, 0.001)
        assertEquals(0.25, onlyLight, 0.001)
    }

    @Test
    fun `an entry with no key points is never certified`() {
        val r = AnswerGrader.grade(entry(keyPoints = emptyList()), words(40))
        assertEquals(AnswerGrader.Verdict.NEEDS_MODEL, r.verdict)
    }

    @Test
    fun `topic percentages average the questions tagged with each topic`() {
        val results = listOf(
            AnswerGrader.grade(entry(id = 1, topics = listOf("I2C")), words(40)),          // 0
            AnswerGrader.grade(
                entry(
                    id = 2, topics = listOf("I2C", "SPI"),
                    keyPoints = listOf(KeyPoint(1, listOf("alpha")))
                ),
                "alpha ${words(40)}"
            )                                                                               // 100
        )
        val pct = AnswerGrader.topicPercentages(results)
        assertEquals(50, pct["I2C"])   // (0 + 100) / 2
        assertEquals(100, pct["SPI"])  // only the second question
    }

    @Test
    fun `untagged questions contribute to no topic`() {
        // Q2, Q11 and Q37 ship with empty topics because the ES_TOPICS
        // vocabulary has no MCU-vs-MPU, inline-functions or I2S entry. They must
        // not silently land in some other topic's average.
        val pct = AnswerGrader.topicPercentages(
            listOf(AnswerGrader.grade(entry(topics = emptyList()), words(40)))
        )
        assertTrue(pct.isEmpty())
    }

    @Test
    fun `topic percentages of nothing is empty, not a division by zero`() {
        assertTrue(AnswerGrader.topicPercentages(emptyList()).isEmpty())
    }
}
