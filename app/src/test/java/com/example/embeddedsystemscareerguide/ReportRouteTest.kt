package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity.ReportRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Sign-in sent a returning student whose report lookup failed to the legacy
 * check and from there - back stack cleared - into a fresh assessment, whose
 * result would overwrite their report. The legacy check, meant for students
 * whose report predates the users/{username}/data/report path, only ever ran on
 * that failure, so such a student was never migrated. IntroductionActivity, the
 * relaunch path, left the same student facing only "Start Assessment".
 */
class ReportRouteTest {

    private val offline = Result.failure<Boolean>(IOException("offline"))

    @Test
    fun `a report goes home`() {
        assertEquals(ReportRoute.HOME, LoginActivity.reportRoute(Result.success(true)))
    }

    @Test
    fun `a failed report lookup is not a first-time student`() {
        assertEquals(ReportRoute.HOME, LoginActivity.reportRoute(offline))
    }

    @Test
    fun `no report at the current path checks the legacy one`() {
        assertEquals(ReportRoute.CHECK_LEGACY, LoginActivity.reportRoute(Result.success(false)))
    }

    @Test
    fun `a legacy report is migrated`() {
        assertEquals(
            ReportRoute.MIGRATE_LEGACY,
            LoginActivity.reportRoute(Result.success(false), Result.success(true))
        )
    }

    @Test
    fun `only two successful empty lookups send the student to the assessment`() {
        assertEquals(
            ReportRoute.ASSESSMENT,
            LoginActivity.reportRoute(Result.success(false), Result.success(false))
        )
        assertEquals(ReportRoute.HOME, LoginActivity.reportRoute(Result.success(false), offline))
    }
}
