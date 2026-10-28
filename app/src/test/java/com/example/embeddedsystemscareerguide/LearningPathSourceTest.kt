package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.StageGeneratorService
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment
import com.example.embeddedsystemscareerguide.ui.learningpath.LearningPathFragment.PathSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The learning path screen sent a student to retake the 50-question assessment,
 * clearing the back stack, whenever it could not tell what they had: a failed
 * read was taken to mean "no path" and "no report", and leaving the screen
 * mid-load - including mid-regeneration, which takes minutes - was caught as one
 * more error and answered the same way.
 */
class LearningPathSourceTest {

    private val stage = PersonalizedStage(id = 1, title = "GPIO")

    private fun resolve(
        stages: suspend () -> Result<List<PersonalizedStage>>,
        report: suspend () -> Result<Boolean> = { fail("report must not be read"); Result.success(false) }
    ) = runBlocking { LearningPathFragment.resolvePathSource(stages, report) }

    @Test
    fun `a stored path is shown`() {
        assertEquals(PathSource.Stages(listOf(stage)), resolve({ Result.success(listOf(stage)) }))
    }

    @Test
    fun `no path but a report rebuilds the path`() {
        assertEquals(
            PathSource.RegenerateFromReport,
            resolve({ Result.success(emptyList()) }, { Result.success(true) })
        )
    }

    @Test
    fun `no path and no report is the only case that sends the student to the assessment`() {
        assertEquals(
            PathSource.TakeAssessment,
            resolve({ Result.success(emptyList()) }, { Result.success(false) })
        )
    }

    @Test
    fun `an unreadable path is not a missing one`() {
        val source = resolve({ Result.failure(IOException("offline")) })
        assertTrue("was $source", source is PathSource.Unavailable)
    }

    @Test
    fun `an unreadable report is not a missing one`() {
        val source = resolve({ Result.success(emptyList()) }, { Result.failure(IOException("offline")) })
        assertTrue("was $source", source is PathSource.Unavailable)
    }

    @Test
    fun `leaving the screen mid-load ends the load`() {
        // FirestoreManager catches the cancellation into a failed Result...
        expectCancellation { resolve({ Result.failure(CancellationException("left")) }) }
        expectCancellation {
            resolve({ Result.success(emptyList()) }, { Result.failure(CancellationException("left")) })
        }
        // ...or its withContext boundary rethrows it.
        expectCancellation { resolve({ throw CancellationException("left") }) }
    }

    @Test
    fun `leaving mid-regeneration is not reported to the screen as a failed run`() {
        var errors = 0
        val callback = object : StageGeneratorService.GenerationCallback {
            override fun onProgress(phase: Int, message: String) = throw CancellationException("left")
            override fun onSuccess(stages: List<PersonalizedStage>) = fail("no stages were generated")
            override fun onError(error: String) { errors++ }
        }
        val service = withoutConstructor(StageGeneratorService::class.java)
        val result = StageGeneratorService.AssessmentResult()

        expectCancellation { runBlocking { service.generatePersonalizedStages("s", result, callback) } }
        expectCancellation { runBlocking { service.regenerateStages("s", result, callback) } }
        assertEquals("onError sends LearningPath's student to the assessment", 0, errors)
    }

    private fun expectCancellation(block: () -> Unit) {
        try {
            block()
            fail("cancellation must propagate")
        } catch (e: CancellationException) {
            // expected
        }
    }

    /**
     * StageGeneratorService's constructor needs a live FirebaseApp. The path under
     * test - the run's catch ladder, reached from its first progress callback -
     * reads none of the fields the constructor sets, so allocate the instance
     * without running it.
     */
    private fun <T> withoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
