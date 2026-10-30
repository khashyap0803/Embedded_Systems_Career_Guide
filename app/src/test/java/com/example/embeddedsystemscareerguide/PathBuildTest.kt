package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment.PathBuild
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * When the learning path had to be rebuilt from a saved report, every failure -
 * the report read, the generation, an exception - sent the student, clearing the
 * back stack, to retake the 50-question assessment. A failure must leave them on
 * the screen with a Retry button.
 */
class PathBuildTest {

    private val report: Map<String, Any> = mapOf("totalScore" to 62)

    private fun build(
        loadReport: suspend () -> Map<String, Any>?,
        generate: suspend (Map<String, Any>) -> Result<Unit>
    ) = runBlocking { LearningPathFragment.buildPathFromReport(loadReport, generate) }

    @Test
    fun `a generated path is built from the saved report`() {
        var generatedFrom: Map<String, Any>? = null
        val build = build({ report }) { generatedFrom = it; Result.success(Unit) }

        assertEquals(PathBuild.Built, build)
        assertEquals(report, generatedFrom)
    }

    @Test
    fun `a failed generation is a failed build, not a missing report`() {
        val build = build({ report }) { Result.failure(Exception("Failed to generate learning path after 3 attempts")) }

        assertTrue("was $build", build is PathBuild.Failed)
    }

    @Test
    fun `a generation that throws is a failed build`() {
        val build = build({ report }) { throw IOException("server down") }

        assertTrue("was $build", build is PathBuild.Failed)
    }

    @Test
    fun `an unreadable report is a failed build, and nothing is generated`() {
        var generated = false
        val build = build({ null }) { generated = true; Result.success(Unit) }

        assertTrue("was $build", build is PathBuild.Failed)
        assertFalse(generated)
    }

    @Test
    fun `a report read that throws is a failed build`() {
        val build = build({ throw IOException("offline") }) { Result.success(Unit) }

        assertTrue("was $build", build is PathBuild.Failed)
    }

    @Test
    fun `leaving the screen mid-generation ends the build`() {
        try {
            val build = build({ report }) { throw CancellationException("left Learning Path") }
            fail("cancellation must propagate, got $build")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
