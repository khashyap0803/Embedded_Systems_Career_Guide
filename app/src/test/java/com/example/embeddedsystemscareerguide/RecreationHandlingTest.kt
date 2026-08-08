package com.example.embeddedsystemscareerguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rotation must not throw away a report run, and a recreation that still happens
 * must say so.
 *
 * A rotation defect cannot be proven in a JVM test - that took a device. What
 * CAN be pinned here is the two things whose regression would silently reopen
 * it: the manifest's configChanges value, and the wording that separates "you
 * stopped this" from "this was interrupted". Both are one-token edits away from
 * being wrong again, and neither would fail any other test.
 */
class RecreationHandlingTest {

    private val moduleDir = File("").absoluteFile
    private val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()
    private val strings = File(moduleDir, "src/main/res/values/strings.xml").readText()

    private fun string(name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(strings)?.groupValues?.get(1)
            ?: error("string $name not found")

    /** The `android:configChanges` declared on AssessmentActivity. */
    private fun assessmentConfigChanges(): String {
        val block = Regex(
            "<activity[^>]*?\\.ui\\.assessment\\.AssessmentActivity[\\s\\S]*?/>"
        ).find(manifest)?.value ?: error("AssessmentActivity not found in manifest")
        return Regex("android:configChanges=\"([^\"]*)\"")
            .find(block)?.groupValues?.get(1)
            ?: error("AssessmentActivity declares no configChanges")
    }

    // ---- The manifest ---------------------------------------------------------

    @Test
    fun `AssessmentActivity absorbs a rotation instead of being recreated by it`() {
        val declared = assessmentConfigChanges().split("|").map { it.trim() }.toSet()

        // A rotation raises all of these on minSdk 26. Missing any one of them
        // hands the recreation back to the framework, which kills lifecycleScope
        // and with it a run measured at ~17 minutes.
        listOf("orientation", "screenSize", "screenLayout", "smallestScreenSize")
            .forEach { assertTrue("configChanges must include $it; was $declared", it in declared) }

        // Was already there for the night-mode theme and must survive.
        assertTrue("uiMode must not be dropped; was $declared", "uiMode" in declared)
    }

    @Test
    fun `the config set stays deliberately narrow`() {
        val declared = assessmentConfigChanges().split("|").map { it.trim() }.toSet()
        assertEquals(
            "adding to this set means the Activity stops being recreated for that " +
                "change too, and every resource it resolved stays stale",
            setOf("uiMode", "orientation", "screenSize", "screenLayout", "smallestScreenSize"),
            declared
        )
        // density and fontScale are deliberately absent: both rescale every
        // dimension in the layout, and suppressing recreation there would leave
        // the screen laid out for the old scale. They are rare enough mid-run to
        // be worth handling by telling the student instead.
        assertFalse("density must not be absorbed", "density" in declared)
        assertFalse("fontScale must not be absorbed", "fontScale" in declared)
    }

    @Test
    fun `the assessment layout has no orientation or width qualified variant`() {
        // This is what makes absorbing the change safe: there is no alternate
        // layout the Activity would fail to pick up. If someone later adds
        // res/layout-land/activity_assessment.xml it would silently never load.
        val res = File(moduleDir, "src/main/res")
        val variants = res.listFiles { f -> f.isDirectory && f.name.startsWith("layout") }
            .orEmpty()
            .filter { File(it, "activity_assessment.xml").exists() }
            .map { it.name }
        assertEquals(
            "activity_assessment.xml must exist only in the unqualified layout dir",
            listOf("layout"), variants
        )
    }

    // ---- The wording ----------------------------------------------------------

    @Test
    fun `an interrupted run is not described to the student as one they stopped`() {
        val cancelled = string("report_cancelled_toast")
        val interrupted = string("report_interrupted_toast")

        assertTrue("the two messages must not be the same text", cancelled != interrupted)

        // The cancel wording tells the student they did this. Reusing it for a
        // recreation blames them for a low-memory kill.
        assertTrue("cancel wording should say generation stopped", cancelled.contains("stopped"))
        assertFalse(
            "the interrupted message must not say the student stopped it",
            interrupted.contains("stopped")
        )
        assertFalse(
            "nor invite them to resubmit 'whenever you're ready', which implies they chose this",
            interrupted.contains("whenever")
        )
        assertTrue(
            "the interrupted message must name what happened",
            interrupted.contains("interrupted")
        )
    }

    @Test
    fun `both messages promise the same two facts - nothing saved, answers kept`() {
        // Whatever the cause, these are the only two things the app can honestly
        // guarantee, and a student needs both.
        listOf("report_cancelled_toast", "report_interrupted_toast").forEach { n ->
            val s = string(n)
            assertTrue(
                "$n must say nothing was saved",
                s.contains("nothing was saved", ignoreCase = true)
            )
            assertTrue("$n must say the answers survived", s.contains("answers are"))
        }
    }

    // ---- The success path must not have drifted -------------------------------

    @Test
    fun `success-path completion strings are untouched`() {
        assertEquals("Report Generated Successfully!", string("report_saved_title"))
        assertEquals(
            "Your personalized career report is ready.\\nYou can preview it now or continue to home.",
            string("report_saved_body")
        )
        assertEquals("📄 Preview Report", string("report_preview_button"))
    }
}
