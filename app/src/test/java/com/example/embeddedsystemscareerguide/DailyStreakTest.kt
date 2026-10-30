package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.ProgressLoad
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.StreakUpdate
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.UserProgress
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * The daily streak was only counted when Learning Path was opened, so after days
 * away Home showed an old streak, and a student who only opened Home or Practice
 * never got one. It is now counted once per calendar day by whichever of
 * MainActivity, Home and Learning Path asks first, with the same rules.
 */
class DailyStreakTest {

    private val march1: LocalDate = LocalDate.of(2026, 3, 1)

    private fun progress(streak: Int, best: Int, lastVisit: String) =
        UserProgress(streak = streak, bestStreak = best, lastVisitDate = lastVisit)

    private fun streakOn(day: LocalDate, progress: UserProgress) = UserProgressSyncService.dailyStreak(progress, day)

    @Test
    fun `a second visit the same day changes nothing`() {
        assertNull(streakOn(march1, progress(streak = 5, best = 7, lastVisit = "2026-03-01")))
    }

    @Test
    fun `the next day adds one, across a month boundary`() {
        val update = streakOn(march1, progress(streak = 5, best = 5, lastVisit = "2026-02-28"))
        assertEquals(StreakUpdate(previous = 5, streak = 6, bestStreak = 6, date = "2026-03-01", broken = false), update)
    }

    @Test
    fun `the next day adds one, across a year boundary`() {
        val update = streakOn(LocalDate.of(2026, 1, 1), progress(streak = 2, best = 4, lastVisit = "2025-12-31"))
        assertEquals(3, update?.streak)
        assertEquals(4, update?.bestStreak)
    }

    @Test
    fun `a gap of two or more days starts again at 1 and keeps the best streak`() {
        for (lastVisit in listOf("2026-02-27", "2026-01-15")) {
            val update = streakOn(march1, progress(streak = 9, best = 9, lastVisit = lastVisit))
            assertEquals(StreakUpdate(previous = 9, streak = 1, bestStreak = 9, date = "2026-03-01", broken = true), update)
        }
    }

    @Test
    fun `the best streak is kept while a new one is shorter`() {
        val update = streakOn(march1, progress(streak = 3, best = 9, lastVisit = "2026-02-28"))
        assertEquals(4, update?.streak)
        assertEquals(9, update?.bestStreak)
    }

    @Test
    fun `a first visit starts at 1 and records the day`() {
        val update = streakOn(march1, UserProgress())
        assertEquals(StreakUpdate(previous = 1, streak = 1, bestStreak = 1, date = "2026-03-01", broken = false), update)
    }

    /** A progress document that remembers the streak writes made to it. */
    private class Document(var progress: UserProgress?) {
        var reads = 0
        val writes = mutableListOf<StreakUpdate>()
        var failReads = false
        suspend fun visit(key: String, today: LocalDate, saved: Boolean = true) =
            UserProgressSyncService.recordDailyVisit(
                key, today,
                load = {
                    reads++
                    if (failReads) ProgressLoad.Failed(IOException("offline"))
                    else progress?.let { ProgressLoad.Loaded(it) } ?: ProgressLoad.Absent
                },
                save = { update ->
                    writes += update
                    if (saved) {
                        progress = (progress ?: UserProgress()).copy(
                            streak = update.streak, bestStreak = update.bestStreak, lastVisitDate = update.date
                        )
                    }
                    saved
                }
            )
    }

    @Test
    fun `the day is counted once, whichever screen asks first`() = runBlocking {
        val doc = Document(progress(streak = 5, best = 5, lastVisit = "2026-02-28"))

        val first = doc.visit("once-a-day", march1)      // MainActivity
        val second = doc.visit("once-a-day", march1)     // then Learning Path, the same day

        assertEquals(6, first?.streak)
        assertNull(second)
        assertEquals(1, doc.writes.size)
        assertEquals(1, doc.reads)
    }

    @Test
    fun `the next calendar day is counted again`() = runBlocking {
        val doc = Document(progress(streak = 5, best = 5, lastVisit = "2026-02-28"))

        doc.visit("next-day", march1)
        val nextDay = doc.visit("next-day", march1.plusDays(1))

        assertEquals(7, nextDay?.streak)
        assertEquals(2, doc.writes.size)
    }

    @Test
    fun `a failed read writes nothing and is tried again`() = runBlocking {
        val doc = Document(progress(streak = 5, best = 5, lastVisit = "2026-02-28")).apply { failReads = true }

        assertNull(doc.visit("failed-read", march1))
        assertTrue(doc.writes.isEmpty())

        doc.failReads = false
        assertEquals(6, doc.visit("failed-read", march1)?.streak)
    }

    @Test
    fun `a refused write is tried again`() = runBlocking {
        val doc = Document(progress(streak = 5, best = 5, lastVisit = "2026-02-28"))

        assertNull(doc.visit("refused-write", march1, saved = false))
        assertEquals(6, doc.visit("refused-write", march1)?.streak)
        assertEquals(2, doc.writes.size)
    }

    @Test
    fun `an unconfirmed write offline does not hold the screens`() = runBlocking {
        // Offline, a Firestore write never confirms. Home and Learning Path wait on this.
        val update = withTimeout(5_000) {
            UserProgressSyncService.recordDailyVisit(
                "offline-write", march1,
                load = { ProgressLoad.Loaded(progress(streak = 5, best = 5, lastVisit = "2026-02-28")) },
                save = { awaitCancellation() },
                saveWaitMs = 20
            )
        }
        assertEquals(6, update?.streak)
    }

    @Test
    fun `milestones and a broken streak are announced as before`() {
        assertEquals("🔥 1 WEEK STREAK! Amazing dedication!",
            UserProgressSyncService.streakMessage(StreakUpdate(6, 7, 7, "2026-03-01", broken = false)))
        assertEquals("🔥 Previous streak: 9 days\nStarting fresh! Let's build a new streak!",
            UserProgressSyncService.streakMessage(StreakUpdate(9, 1, 9, "2026-03-01", broken = true)))
        assertNull(UserProgressSyncService.streakMessage(StreakUpdate(5, 6, 6, "2026-03-01", broken = false)))
    }
}
