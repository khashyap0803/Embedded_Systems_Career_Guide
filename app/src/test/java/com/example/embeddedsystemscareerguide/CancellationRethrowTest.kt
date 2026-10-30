package com.example.embeddedsystemscareerguide

import android.content.SharedPreferences
import com.example.embeddedsystemscareerguide.services.AnalyticsService
import com.example.embeddedsystemscareerguide.services.AnalyticsService.LearningAnalytics
import com.example.embeddedsystemscareerguide.services.AnalyticsService.Recommendation
import com.example.embeddedsystemscareerguide.services.DailyTipService
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.FlashcardService
import com.example.embeddedsystemscareerguide.services.GeminiQuizService
import com.example.embeddedsystemscareerguide.services.Project
import com.example.embeddedsystemscareerguide.services.ProjectSuggestionService
import com.example.embeddedsystemscareerguide.services.QuizResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * catch (e: Exception) also catches CancellationException. Wherever that happened
 * in a suspend function, leaving a screen became one more failure: a Result.failure,
 * a fallback quiz, or an onError callback whose "...was cancelled" text a screen then
 * showed. These must let the cancellation through.
 */
class CancellationRethrowTest {

    /**
     * SharedPreferences whose every read throws CancellationException, so the
     * first thing each FirestoreManager call does - look up the username - is
     * where the student leaves the screen.
     */
    private val leftTheScreen = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)
    ) { _, _, _ -> throw CancellationException("left the screen") } as SharedPreferences

    private fun firestoreManager(): FirestoreManager =
        withoutConstructor(FirestoreManager::class.java).also { set(it, "prefs", leftTheScreen) }

    /** [type] with a FirestoreManager whose calls are cancelled, built without its constructor. */
    private fun <T> withCancelledFirestore(type: Class<T>): T =
        withoutConstructor(type).also { set(it as Any, "firestoreManager", firestoreManager()) }

    @Test
    fun `FirestoreManager reads and writes let a cancellation through`() {
        val fm = firestoreManager()
        val calls: List<Pair<String, suspend () -> Any?>> = listOf(
            "getPersonalizedStages" to { fm.getPersonalizedStages() },
            "hasPersonalizedStages" to { fm.hasPersonalizedStages() },
            "getAssessmentReport" to { fm.getAssessmentReport() },
            "hasAssessmentReport" to { fm.hasAssessmentReport() },
            "getUserProfile" to { fm.getUserProfile() },
            "collectUserPerformanceData" to { fm.collectUserPerformanceData() },
            "getFlashcards" to { fm.getFlashcards(1) },
            "saveQuizResult" to { fm.saveQuizResult(1, QuizResult()) },
            "getQuizHistory" to { fm.getQuizHistory(1) },
            "getChatHistory" to { fm.getChatHistory() },
            "getTodaysTip" to { fm.getTodaysTip() }
        )
        for ((name, call) in calls) {
            expectCancellation(name) { call() }
        }
    }

    @Test
    fun `a cancelled quiz request is not answered with the fallback quiz`() {
        val service = GeminiQuizService()
        expectCancellation("generateQuiz") {
            service.generateQuiz("Timers", numberOfQuestions = 10, sleep = {}) { throw CancellationException("left the quiz") }
        }
    }

    @Test
    fun `cancelled project suggestions report no error`() {
        val errors = mutableListOf<String>()
        expectCancellation("generateProjectSuggestions") {
            withCancelledFirestore(ProjectSuggestionService::class.java)
                .generateProjectSuggestions(object : ProjectSuggestionService.ProjectCallback {
                    override fun onProgress(message: String) {}
                    override fun onSuccess(projects: List<Project>) = fail("no projects from a cancelled read")
                    override fun onError(error: String) { errors += error }
                })
        }
        assertTrue("onError was called with $errors", errors.isEmpty())
    }

    @Test
    fun `cancelled analytics report no error`() {
        val errors = mutableListOf<String>()
        expectCancellation("generateAnalytics") {
            withCancelledFirestore(AnalyticsService::class.java)
                .generateAnalytics(object : AnalyticsService.AnalyticsCallback {
                    override fun onProgress(message: String) {}
                    override fun onSuccess(analytics: LearningAnalytics, recommendations: List<Recommendation>) =
                        fail("no analytics from a cancelled read")
                    override fun onError(error: String) { errors += error }
                })
        }
        assertTrue("onError was called with $errors", errors.isEmpty())
    }

    @Test
    fun `cancelled flashcard and tip loads are not failures`() {
        expectCancellation("getAllCardsNeedingReview") {
            withCancelledFirestore(FlashcardService::class.java).getAllCardsNeedingReview()
        }
        expectCancellation("getTodaysTip") {
            withCancelledFirestore(DailyTipService::class.java).getTodaysTip(save = {})
        }
    }

    private fun expectCancellation(name: String, block: suspend () -> Any?) {
        try {
            val result = runBlocking { block() }
            fail("$name swallowed the cancellation and returned $result")
        } catch (e: CancellationException) {
            // expected
        }
    }

    private fun set(target: Any, field: String, value: Any) {
        target.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
    }

    private fun <T> withoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
