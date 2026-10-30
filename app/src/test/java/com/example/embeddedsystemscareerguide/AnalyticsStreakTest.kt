package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.AnalyticsService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.ProgressLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Analytics read the streak from users/{username}/progress/current, which nothing
 * writes, so the streak was always 0 and the pace always "slow". It now reads the
 * progress document Home's Study Streak card reads.
 */
class AnalyticsStreakTest {

    @Test
    fun `the streak is the one Home shows`() {
        val load = ProgressLoad.Loaded(UserProgressSyncService.UserProgress(streak = 6, bestStreak = 9))

        assertEquals(AnalyticsService.Streak(current = 6, best = 9), AnalyticsService.streakFrom(load))
    }

    @Test
    fun `no progress document yet has Home's new-student streak`() {
        // HomeFragment shows a streak of 1 when there is no progress document.
        assertEquals(AnalyticsService.Streak(current = 1, best = 1), AnalyticsService.streakFrom(ProgressLoad.Absent))
    }

    @Test
    fun `a failed read is not a streak of zero`() {
        assertNull(AnalyticsService.streakFrom(ProgressLoad.Failed(IOException("offline"))))
    }

    @Test
    fun `the pace follows the streak`() {
        assertEquals("fast", AnalyticsService.paceFor(6))
        assertEquals("steady", AnalyticsService.paceFor(3))
        assertEquals("slow", AnalyticsService.paceFor(2))
    }
}
