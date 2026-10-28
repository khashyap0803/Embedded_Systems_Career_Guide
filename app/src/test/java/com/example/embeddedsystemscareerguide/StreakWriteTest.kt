package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Learning Path screen saved the daily streak by merging its whole cached
 * progress document back, so a stage completed after the screen loaded - on a
 * second device, or by a transaction committing in between - was overwritten
 * with the stale XP and completed-stage list.
 */
class StreakWriteTest {

    private val written = UserProgressSyncService.streakFields(
        streak = 5,
        bestStreak = 9,
        lastVisitDate = "2026-10-28",
        now = 1_000L
    )

    @Test
    fun `a streak update writes only the streak`() {
        assertEquals(setOf("streak", "bestStreak", "lastVisitDate", "lastUpdated"), written.keys)
    }

    @Test
    fun `a streak update cannot undo progress made elsewhere`() {
        listOf("totalXP", "currentStage", "completedStages", "stageStars").forEach { field ->
            assertEquals("$field must not be written by a streak update", false, field in written)
        }
    }

    @Test
    fun `the streak values are written as given`() {
        assertEquals(5, written["streak"])
        assertEquals(9, written["bestStreak"])
        assertEquals("2026-10-28", written["lastVisitDate"])
        assertEquals(1_000L, written["lastUpdated"])
    }
}
