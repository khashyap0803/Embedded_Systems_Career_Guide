package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.ProgressLoad
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.UserProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * resetStageProgressInCloud - run on every retake - and updateStreakInCloud
 * started their write from `loadProgressFromCloud() ?: UserProgress()`. That
 * helper returns null for a failed read too, so one failed read wrote a new
 * user's zeros over the student's XP, streak and completed stages.
 */
class ProgressWriteBaseTest {

    private val real = UserProgress(
        totalXP = 2_400,
        currentStage = 9,
        streak = 12,
        bestStreak = 30,
        lastVisitDate = "2026-10-27",
        completedStages = (1..8).map { it.toString() },
        stageStars = mapOf("1" to 3, "2" to 2),
        lastUpdated = 1L
    )

    @Test
    fun `a failed read writes nothing`() {
        assertNull(UserProgressSyncService.writeBaseFor(ProgressLoad.Failed(IOException("offline"))))
    }

    @Test
    fun `the stored progress is what the write builds on`() {
        val base = UserProgressSyncService.writeBaseFor(ProgressLoad.Loaded(real))
        assertEquals(real, base)

        // What resetStageProgressInCloud then writes: stage state cleared, the rest kept.
        val reset = base!!.copy(currentStage = 1, completedStages = emptyList(), stageStars = emptyMap())
        assertEquals(2_400, reset.totalXP)
        assertEquals(12, reset.streak)
        assertEquals(30, reset.bestStreak)
    }

    @Test
    fun `only a genuinely absent document starts from a new user's defaults`() {
        val base = UserProgressSyncService.writeBaseFor(ProgressLoad.Absent)
        assertEquals(0, base?.totalXP)
        assertEquals(emptyList<String>(), base?.completedStages)
    }
}
