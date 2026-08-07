package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiReportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shell call now asks for the report BODY and the document around it is
 * built client-side, because the model was spending ~4,300 characters of every
 * completion retyping a stylesheet that never changes.
 *
 * The prompt says "fragment", but a model asked for HTML will sometimes hand
 * back a whole document anyway, and a document nested inside our own container
 * renders as a mess. These pin the undo.
 */
class ReportBodyStrippingTest {

    private val svc = GeminiReportService()

    @Test
    fun `a plain fragment is passed through untouched`() {
        val body = "<div class=\"user-info\"><p>Student</p></div><h2>Summary</h2><p>Good.</p>"
        assertEquals(body, svc.stripDocumentScaffolding(body))
    }

    @Test
    fun `a full document is reduced to its body content`() {
        val doc = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>x</title><style>body { color: red; }</style></head>
            <body>
            <h2>Summary</h2><p>Kept.</p>
            </body>
            </html>
        """.trimIndent()
        val out = svc.stripDocumentScaffolding(doc)
        assertTrue("body content must survive", out.contains("<h2>Summary</h2>"))
        assertTrue(out.contains("Kept."))
        assertFalse("doctype must not survive", out.contains("DOCTYPE"))
        assertFalse("the model's stylesheet must not survive", out.contains("<style"))
        assertFalse(out.contains("<head"))
        assertFalse(out.contains("<body"))
        assertFalse(out.contains("</html>"))
    }

    @Test
    fun `head and style are dropped even without a body tag`() {
        val doc = "<!DOCTYPE html><head><style>p { color: red; }</style></head><h2>S</h2><p>Kept.</p>"
        val out = svc.stripDocumentScaffolding(doc)
        assertTrue(out.contains("<h2>S</h2>"))
        assertTrue(out.contains("Kept."))
        assertFalse(out.contains("<style"))
        assertFalse(out.contains("DOCTYPE"))
    }

    @Test
    fun `a fenced code block is unwrapped`() {
        val fenced = "```html\n<h2>Summary</h2><p>Kept.</p>\n```"
        val out = svc.stripDocumentScaffolding(fenced)
        assertTrue(out.contains("<h2>Summary</h2>"))
        assertFalse("fences would render as literal text", out.contains("```"))
    }

    @Test
    fun `a duplicated container and title are dropped`() {
        val doc = "<div class=\"container\"><h1>Your Personalized Embedded Systems Report</h1>" +
            "<h2>Summary</h2><p>Kept.</p></div>"
        val out = svc.stripDocumentScaffolding(doc)
        assertFalse("the wrapper supplies the container", out.contains("class=\"container\""))
        assertFalse("the wrapper supplies the title", out.contains("<h1"))
        assertTrue(out.contains("<h2>Summary</h2>"))
        assertTrue(out.contains("Kept."))
    }

    @Test
    fun `the feedback placeholder survives stripping`() {
        val body = "<h2>A</h2><h2>Feedback</h2><!-- QUESTION_FEEDBACK_INSERT_POINT --><h2>B</h2>"
        val out = svc.stripDocumentScaffolding(body)
        assertTrue(
            "losing the placeholder loses fifty questions of feedback",
            out.contains("<!-- QUESTION_FEEDBACK_INSERT_POINT -->")
        )
    }

    @Test
    fun `the placeholder survives inside a full document too`() {
        val doc = "<!DOCTYPE html><html><body><h2>A</h2>" +
            "<!-- QUESTION_FEEDBACK_INSERT_POINT --><h2>B</h2></body></html>"
        val out = svc.stripDocumentScaffolding(doc)
        assertTrue(out.contains("<!-- QUESTION_FEEDBACK_INSERT_POINT -->"))
    }

    @Test
    fun `blank input stays blank so the caller can degrade`() {
        assertEquals("", svc.stripDocumentScaffolding(""))
        assertEquals("", svc.stripDocumentScaffolding("   \n  "))
    }
}
