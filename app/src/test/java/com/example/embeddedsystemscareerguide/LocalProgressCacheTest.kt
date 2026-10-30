package com.example.embeddedsystemscareerguide

import android.content.SharedPreferences
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.UserProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * After each cloud write the progress was also copied into local prefs: XP, streak,
 * best streak, visit date, completed stages, stars, a first-launch flag and four
 * home_* values. Since the screens read progress from the progress document, the only
 * one of those anything reads is current_stage, which Learning Path uses to highlight
 * the current stage.
 */
class LocalProgressCacheTest {

    @Test
    fun `only the current stage is kept locally`() {
        val written = mutableMapOf<String, Any?>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            if (method.name.startsWith("put")) written[args[0] as String] = args[1]
            if (method.returnType == SharedPreferences.Editor::class.java) proxy else null
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, _ ->
            if (method.name == "edit") editor else error("unexpected ${method.name}")
        } as SharedPreferences

        val service = withoutConstructor(UserProgressSyncService::class.java)
        service.javaClass.getDeclaredField("prefs").apply { isAccessible = true }.set(service, prefs)

        service.saveLocalCurrentStage(
            UserProgress(
                totalXP = 1250, currentStage = 4, streak = 5, bestStreak = 7, lastVisitDate = "2026-03-01",
                completedStages = listOf("1", "2", "3"), stageStars = mapOf("1" to 3, "2" to 2, "3" to 1)
            )
        )

        assertEquals(mapOf<String, Any?>("current_stage" to 4), written)
    }

    private fun <T> withoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
