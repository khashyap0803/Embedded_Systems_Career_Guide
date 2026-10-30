package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.ProgressLoad
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.StreakUpdate
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.UserProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * On the first load of the day Home read the progress document twice: once inside
 * the daily streak update and again for itself. The update now hands back what it
 * read, with today's streak applied, and Home uses that.
 */
class DailyVisitProgressTest {

    private val march1: LocalDate = LocalDate.of(2026, 3, 1)
    private val student = UserProgress(
        totalXP = 1250, streak = 5, bestStreak = 5, lastVisitDate = "2026-02-28", completedStages = listOf("1", "2")
    )

    /** A progress document that counts its reads and remembers streak writes. */
    private class Document(var load: ProgressLoad) {
        var reads = 0
        val writes = mutableListOf<StreakUpdate>()
        fun visit(key: String, today: LocalDate, saved: Boolean = true) = runBlocking {
            UserProgressSyncService.recordDailyVisitWithProgress(
                key, today,
                load = { reads++; load },
                save = { writes += it; saved }
            )
        }
    }

    @Test
    fun `the first visit of the day hands back what it read, with today's streak`() {
        val doc = Document(ProgressLoad.Loaded(student))

        val visit = doc.visit("handed-back", march1)
        val progress = (visit.progress as ProgressLoad.Loaded).progress

        assertEquals(1, doc.reads)
        assertEquals(6, visit.update?.streak)
        assertEquals(student.copy(streak = 6, bestStreak = 6, lastVisitDate = "2026-03-01"), progress)
    }

    @Test
    fun `a day already counted hands back nothing, so the caller reads for itself`() {
        val doc = Document(ProgressLoad.Loaded(student))
        doc.visit("counted-twice", march1)

        val second = doc.visit("counted-twice", march1)

        assertNull(second.update)
        assertNull(second.progress)
        assertEquals(1, doc.reads)
    }

    @Test
    fun `a failed read hands back nothing, so the caller reads for itself`() {
        val visit = Document(ProgressLoad.Failed(IOException("offline"))).visit("failed-read", march1)

        assertNull(visit.update)
        assertNull(visit.progress)
    }

    @Test
    fun `a refused write hands back the document as it still is`() {
        val visit = Document(ProgressLoad.Loaded(student)).visit("refused-write", march1, saved = false)

        assertNull(visit.update)
        assertSame(student, (visit.progress as ProgressLoad.Loaded).progress)
    }

    @Test
    fun `a day another device already counted hands back the document unchanged`() {
        val counted = student.copy(streak = 6, bestStreak = 6, lastVisitDate = "2026-03-01")
        val doc = Document(ProgressLoad.Loaded(counted))

        val visit = doc.visit("other-device", march1)

        assertNull(visit.update)
        assertSame(counted, (visit.progress as ProgressLoad.Loaded).progress)
        assertEquals(0, doc.writes.size)
    }

    @Test
    fun `a new student's first visit hands back the document the streak write created`() {
        val visit = Document(ProgressLoad.Absent).visit("new-student", march1)
        val progress = (visit.progress as ProgressLoad.Loaded).progress

        assertEquals(1, progress.streak)
        assertEquals("2026-03-01", progress.lastVisitDate)
        assertEquals(0, progress.totalXP)
    }
}
