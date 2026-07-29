package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.AnswerKeyEntry
import com.example.embeddedsystemscareerguide.models.KeyPoint
import com.example.embeddedsystemscareerguide.models.Misconception
import com.example.embeddedsystemscareerguide.models.QuestionAnswer
import com.example.embeddedsystemscareerguide.services.AnswerGrader
import com.example.embeddedsystemscareerguide.services.GeminiReportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report is persisted to Firestore and re-rendered later, so an escaping
 * mistake here is a stored sink, not a one-off. These pin the two lanes:
 * anything from the student or the model is escaped, the authored reference
 * answer is not.
 */
class ReportAssemblyTest {

    private val svc = GeminiReportService()

    private fun entry(
        id: Int = 1,
        answerHtml: String = "<p>Reference <strong>answer</strong>.</p>",
        misconceptions: List<Misconception> = emptyList()
    ) = AnswerKeyEntry(
        id = id,
        topics = listOf("I2C (Inter-Integrated Circuit)"),
        modelAnswerHtml = answerHtml,
        keyPoints = listOf(KeyPoint(3, listOf("alpha")), KeyPoint(1, listOf("beta"))),
        correctFeedbackHtml = "<p>A complete answer covers I2C.</p>",
        misconceptions = misconceptions
    )

    private fun qa(answer: String, id: Int = 1) =
        QuestionAnswer(n = id, q = "Explain <b>I2C</b> & its uses", u = answer, id = id)

    // ---------- escaping lanes ----------

    @Test
    fun `student answer containing script is escaped, not rendered`() {
        val hostile = "<script>alert('xss')</script> and <img src=x onerror=1>"
        val block = svc.renderQuestionBlock(
            qa(hostile),
            AnswerGrader.grade(entry(), hostile),
            entry(),
            null
        )
        // The bar is that no TAG can form. The words survive as inert text -
        // "onerror=1" still appears, but only inside an escaped &lt;img ...&gt;
        // where the parser can never treat it as an attribute.
        assertFalse("raw <script> must not survive", block.contains("<script>"))
        assertFalse("no img tag may form", block.contains("<img"))
        assertTrue("must appear escaped instead", block.contains("&lt;script&gt;"))
        assertTrue(block.contains("&lt;img"))
    }

    @Test
    fun `question text is escaped`() {
        val block = svc.renderQuestionBlock(
            qa("some answer here that is long enough to be graded properly"),
            AnswerGrader.grade(entry(), "alpha ".repeat(30)),
            entry(),
            null
        )
        assertTrue(block.contains("&lt;b&gt;I2C&lt;/b&gt;"))
        assertTrue("ampersand must be escaped", block.contains("&amp;"))
    }

    @Test
    fun `model feedback is escaped - it has read the student's free text`() {
        val poisoned = GeminiReportService.Adjudicated(
            id = 1, score = 60,
            feedbackText = "Consider <script>steal()</script> the clock line"
        )
        val block = svc.renderQuestionBlock(
            qa("alpha ".repeat(30)),
            AnswerGrader.grade(entry(), "alpha ".repeat(30)),
            entry(),
            poisoned
        )
        assertFalse(block.contains("<script>steal()"))
        assertTrue(block.contains("&lt;script&gt;"))
    }

    @Test
    fun `authored reference answer is NOT escaped`() {
        // The opposite lane: this ships in the APK, is already sanitised, and
        // double-escaping it would show students literal &lt;p&gt; tags.
        val block = svc.renderQuestionBlock(
            qa("alpha ".repeat(30)),
            AnswerGrader.grade(entry(), "alpha ".repeat(30)),
            entry(),
            null
        )
        assertTrue("authored markup must render", block.contains("<strong>answer</strong>"))
    }

    @Test
    fun `escaped code in the reference answer survives intact`() {
        // The key stores "#include &lt;stdio.h&gt;" already escaped. It must
        // pass through untouched rather than being re-escaped into &amp;lt;.
        val e = entry(answerHtml = "<pre><code>#include &lt;stdio.h&gt;</code></pre>")
        val block = svc.renderQuestionBlock(
            qa("alpha ".repeat(30)), AnswerGrader.grade(e, "alpha ".repeat(30)), e, null
        )
        assertTrue(block.contains("#include &lt;stdio.h&gt;"))
        assertFalse("must not double-escape", block.contains("&amp;lt;stdio.h"))
    }

    // ---------- score reconciliation ----------

    @Test
    fun `model may lift a score but only to the cap`() {
        val v = AnswerGrader.grade(entry(), "alpha ${"word ".repeat(30)}")   // coverage 75
        val generous = GeminiReportService.Adjudicated(1, 100, "great")
        assertEquals(75 + 25, svc.finalScore(v, generous))
    }

    @Test
    fun `model may not lower the measured floor`() {
        val v = AnswerGrader.grade(entry(), "alpha ${"word ".repeat(30)}")   // coverage 75
        val harsh = GeminiReportService.Adjudicated(1, 10, "poor")
        assertEquals(75, svc.finalScore(v, harsh))
    }

    @Test
    fun `a misconception withholds certification but does NOT cap the score`() {
        // It used to cap at 50. These triggers are short substrings and several
        // are ordinary topic vocabulary, so they fire on correct answers -
        // Q1's own reference answer tripped three of Q1's own triggers. The
        // heuristic may withhold auto-certification; only the model, which read
        // the answer, may set the number.
        val e = entry(misconceptions = listOf(Misconception(listOf("i2c is full duplex"), "no")))
        val v = AnswerGrader.grade(e, "alpha beta i2c is full duplex ${"word ".repeat(30)}")
        assertTrue(v.matchedMisconceptions.isNotEmpty())
        assertEquals(AnswerGrader.Verdict.NEEDS_MODEL, v.verdict)
        assertEquals(100, svc.finalScore(v, GeminiReportService.Adjudicated(1, 100, "x")))
    }

    @Test
    fun `roadmap keeps allowed tags but strips attributes and everything else`() {
        val hostile = "<h3 onclick=\"steal()\">Week 1</h3><ul><li>Read <strong>RM0090</strong></li></ul>" +
            "<script>bad()</script><iframe src=x></iframe>"
        val out = svc.sanitizeModelHtml(hostile)
        assertTrue("bare allowed tags survive", out.contains("<ul>") && out.contains("<li>"))
        assertTrue(out.contains("<strong>RM0090</strong>"))
        // Same bar as the student-answer case: the word survives as inert text
        // inside an escaped tag, but no ELEMENT carrying it can form.
        assertFalse("no tag with attributes may form", out.contains("<h3 "))
        assertTrue("it is escaped, not executed", out.contains("&lt;h3 onclick"))
        assertFalse(out.contains("<script>"))
        assertFalse(out.contains("<iframe"))
        assertTrue("disallowed markup is escaped, not dropped", out.contains("&lt;script&gt;"))
    }

    @Test
    fun `roadmap sanitiser does not double escape plain text`() {
        val out = svc.sanitizeModelHtml("<p>Use I2C &amp; SPI</p>")
        assertTrue(out.contains("<p>"))
        assertFalse(out.contains("&amp;amp;"))
    }

    @Test
    fun `an unanswered question scores zero regardless of any model opinion`() {
        val v = AnswerGrader.grade(entry(), "")
        assertEquals(0, svc.finalScore(v, GeminiReportService.Adjudicated(1, 90, "generous")))
    }

    @Test
    fun `no grading result yields no score rather than a fabricated zero`() {
        assertNull(svc.finalScore(null, null))
    }

    // ---------- adjudication parsing ----------

    @Test
    fun `parses a well formed array`() {
        val raw = """[{"id":3,"score":70,"feedback":"missing the pull-ups"}]"""
        val out = svc.parseAdjudication(raw, listOf(3))
        assertEquals(1, out.size)
        assertEquals(3, out[0].id)
        assertEquals(70, out[0].score)
    }

    @Test
    fun `tolerates prose and fences around the array`() {
        val raw = "Sure! Here you go:\n```json\n[{\"id\":3,\"score\":70,\"feedback\":\"ok\"}]\n```\nHope that helps"
        assertEquals(1, svc.parseAdjudication(raw, listOf(3)).size)
    }

    @Test
    fun `ignores ids that were never asked about`() {
        // Otherwise the model could overwrite feedback for a question it was
        // not shown, in a different chunk.
        val raw = """[{"id":3,"score":70,"feedback":"ok"},{"id":99,"score":10,"feedback":"bogus"}]"""
        val out = svc.parseAdjudication(raw, listOf(3))
        assertEquals(1, out.size)
        assertEquals(3, out[0].id)
    }

    @Test
    fun `malformed json yields nothing instead of throwing`() {
        assertTrue(svc.parseAdjudication("[{\"id\":3,\"score\":", listOf(3)).isEmpty())
        assertTrue(svc.parseAdjudication("no json at all", listOf(3)).isEmpty())
    }

    @Test
    fun `scores outside 0-100 are clamped`() {
        val raw = """[{"id":1,"score":900,"feedback":"a"},{"id":2,"score":-5,"feedback":"b"}]"""
        val out = svc.parseAdjudication(raw, listOf(1, 2)).sortedBy { it.id }
        assertEquals(100, out[0].score)
        assertEquals(0, out[1].score)
    }

    @Test
    fun `unanswered questions are labelled and not shown an empty answer box`() {
        val block = svc.renderQuestionBlock(qa(""), AnswerGrader.grade(entry(), ""), entry(), null)
        assertTrue(block.contains("Not answered"))
        assertTrue("reference answer still shown so they can learn",
            block.contains("<strong>answer</strong>"))
    }
}
