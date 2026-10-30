package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.ProgressLoad
import com.example.embeddedsystemscareerguide.ui.home.HomeFragment
import com.example.embeddedsystemscareerguide.ui.home.HomeFragment.HomeFigures
import com.example.embeddedsystemscareerguide.ui.home.HomeFragment.HomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Home read progress with loadProgressFromCloud, which returns null both when
 * there is no progress document and when the read failed. Either way Home drew a
 * new student's 0 XP, 1-day streak and 0 stages - over a real student's progress
 * whenever the connection dropped.
 */
class HomeProgressTest {

    private val student = UserProgressSyncService.UserProgress(
        totalXP = 1250, streak = 6, completedStages = listOf("1", "2", "3")
    )
    private val lastShown = HomeFigures(totalXP = 1250, streak = 6, completedStages = 3)
    private val failed = ProgressLoad.Failed(IOException("offline"))

    @Test
    fun `a read progress document is shown`() {
        val view = HomeFragment.homeViewFor(ProgressLoad.Loaded(student), lastShown = null)
        assertSame(student, (view as HomeView.Progress).progress)
    }

    @Test
    fun `no progress document yet shows a new student's defaults`() {
        val view = HomeFragment.homeViewFor(ProgressLoad.Absent, lastShown = null)
        assertEquals(HomeFigures(totalXP = 0, streak = 1, completedStages = 0), HomeFigures.of((view as HomeView.Progress).progress))
    }

    @Test
    fun `a failed read keeps what Home last showed`() {
        assertEquals(HomeView.Unavailable(lastShown), HomeFragment.homeViewFor(failed, lastShown))
    }

    @Test
    fun `a failed read with nothing shown before draws no zeros`() {
        val view = HomeFragment.homeViewFor(failed, lastShown = null)
        assertTrue("was $view", view is HomeView.Unavailable && view.lastShown == null)
    }

    @Test
    fun `the figures Home keeps are the ones it draws`() {
        assertEquals(lastShown, HomeFigures.of(student))
    }
}
