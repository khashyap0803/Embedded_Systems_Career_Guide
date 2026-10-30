package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.AnalyticsService
import com.example.embeddedsystemscareerguide.services.AnalyticsService.LearningAnalytics
import com.example.embeddedsystemscareerguide.services.AnalyticsService.Recommendation
import com.example.embeddedsystemscareerguide.services.DailyTip
import com.example.embeddedsystemscareerguide.services.DailyTipService
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.FlashcardService
import com.example.embeddedsystemscareerguide.services.PathRead
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.Project
import com.example.embeddedsystemscareerguide.services.ProjectSuggestionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Every caller of getStagesWithProgress turned a failed read into an empty list,
 * and an empty list means "no path yet". A network or permission error told the
 * student to take the assessment, showed beginner projects, and stored a generic
 * daily tip for the whole day.
 */
class FailedPathReadTest {

    private val gpio = PersonalizedStage(id = 1, title = "GPIO")

    @Test
    fun `an empty read is no path`() {
        assertEquals(PathRead.NoPath, FirestoreManager.pathReadOf(Result.success(emptyList())))
    }

    @Test
    fun `a stored path is the path`() {
        assertEquals(PathRead.Path(listOf(gpio)), FirestoreManager.pathReadOf(Result.success(listOf(gpio))))
    }

    @Test
    fun `a failed read is not a missing path`() {
        val read = FirestoreManager.pathReadOf(Result.failure(IOException("offline")))
        assertTrue("was $read", read is PathRead.Failed)
    }

    @Test
    fun `a cancelled read propagates`() {
        try {
            val read = FirestoreManager.pathReadOf(Result.failure(CancellationException("left")))
            fail("cancellation must propagate, got $read")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun `daily tips on a failed read are generic and not stored`() {
        val service = withFailingReads(DailyTipService::class.java)
        val saved = mutableListOf<DailyTip>()

        val tip = runBlocking { service.getTodaysTip(save = { saved += it }) }.getOrThrow()

        assertEquals(AppDate.todayIso(), tip.date)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `projects on a failed read say so instead of suggesting beginner projects`() {
        val service = withFailingReads(ProjectSuggestionService::class.java)
        var shown: String? = null
        runBlocking {
            service.generateProjectSuggestions(object : ProjectSuggestionService.ProjectCallback {
                override fun onProgress(message: String) {}
                override fun onSuccess(projects: List<Project>) = fail("beginner projects for a failed read")
                override fun onError(error: String) { shown = error }
            })
        }

        assertEquals(FirestoreManager.PROGRESS_LOAD_FAILED_MESSAGE, shown)
    }

    @Test
    fun `cards needing review on a failed read are a failure, not none`() {
        val service = withFailingReads(FlashcardService::class.java)
        assertTrue(runBlocking { service.getAllCardsNeedingReview() }.isFailure)
    }

    @Test
    fun `analytics on a failed read says so instead of no learning path`() {
        val service = withFailingReads(AnalyticsService::class.java)
        var shown: String? = null
        runBlocking {
            service.generateAnalytics(object : AnalyticsService.AnalyticsCallback {
                override fun onProgress(message: String) {}
                override fun onSuccess(analytics: LearningAnalytics, recommendations: List<Recommendation>) =
                    fail("no analytics from a failed read")
                override fun onError(error: String) { shown = error }
            })
        }

        assertEquals(FirestoreManager.PROGRESS_LOAD_FAILED_MESSAGE, shown)
        assertTrue(runBlocking { service.getQuickStats() }.isFailure)
    }

    /**
     * [type] with a FirestoreManager whose reads fail. Neither constructor can run
     * without a live FirebaseApp, so both are allocated without one. The
     * FirestoreManager then has no SharedPreferences, so every read throws inside
     * its own try and comes back as a failed Result - the same path a network or
     * permission error takes.
     */
    private fun <T> withFailingReads(type: Class<T>): T {
        val service = withoutConstructor(type)
        type.getDeclaredField("firestoreManager").apply { isAccessible = true }
            .set(service, withoutConstructor(FirestoreManager::class.java))
        return service
    }

    private fun <T> withoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
