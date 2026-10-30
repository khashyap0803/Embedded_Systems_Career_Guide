package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment.PathFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When the saved learning path could not be read, the student got a toast on an
 * empty screen and no way to retry. The screen now shows its failed state with
 * Retry - worded for a failed load, not for a failed build.
 */
class PathFailureTest {

    /** The module's strings, read the way aapt would see them (tests run from the module directory). */
    private val strings: Map<String, String> = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        .findAll(File("src/main/res/values/strings.xml").readText(Charsets.UTF_8))
        .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `a failed load shows the load wording`() {
        assertEquals(R.string.learning_path_load_failed, PathFailure.LOAD.title)
        assertEquals(R.string.learning_path_load_failed_hint, PathFailure.LOAD.hint)
        assertEquals("We couldn\\'t load your learning path", strings["learning_path_load_failed"])
    }

    @Test
    fun `a failed build keeps the build wording`() {
        assertEquals(R.string.learning_path_build_failed, PathFailure.BUILD.title)
        assertEquals(R.string.learning_path_build_failed_hint, PathFailure.BUILD.hint)
    }

    @Test
    fun `the load hint does not promise a saved report`() {
        // Nothing was read, so the screen can't know a report exists - unlike a failed build.
        val hint = strings.getValue("learning_path_load_failed_hint")
        assertTrue(hint, "report" !in hint)
    }
}
