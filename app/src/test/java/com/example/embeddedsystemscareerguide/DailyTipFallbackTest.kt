package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.DailyTipService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When tip generation failed, DailyTipService returned all seven fallback tips,
 * dated today through six days out, and getTodaysTip stored every one. A tip
 * stored for a day is served without asking the model again, so an outage of
 * any length left the student on generic tips for the next week.
 */
class DailyTipFallbackTest {

    /**
     * The constructor needs a live FirebaseApp, so the instance is allocated
     * without running it. With no FirestoreManager behind it, generateTips fails
     * on its first read and takes the same fallback branch an unreachable model
     * does.
     */
    private val service: DailyTipService = run {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, DailyTipService::class.java) as DailyTipService
    }

    @Test
    fun `a failed generation yields only today's tip`() {
        val tips = runBlocking { service.generateTips() }.tips
        assertEquals(1, tips.size)
        assertEquals(AppDate.todayIso(), tips.single().date)
        assertTrue(tips.single().tip.isNotBlank())
    }
}
