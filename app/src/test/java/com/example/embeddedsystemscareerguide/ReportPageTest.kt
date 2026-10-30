package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.assessment.ReportViewerActivity
import com.example.embeddedsystemscareerguide.ui.assessment.ReportViewerActivity.ReportPage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * The report viewer ended every failed read on "No Report Found - please ensure
 * you have completed the assessment", telling a student who has a report, on a
 * bad connection, to go and take the assessment again.
 */
class ReportPageTest {

    private val offline = Result.failure<String?>(IOException("offline"))
    private val none = Result.success<String?>(null)

    @Test
    fun `a stored report is shown`() {
        assertEquals(ReportPage.Show("<html/>"), ReportViewerActivity.reportPage(Result.success("<html/>")))
    }

    @Test
    fun `a missing or unreadable report checks the legacy copy`() {
        assertEquals(ReportPage.CheckLegacy, ReportViewerActivity.reportPage(none))
        assertEquals(ReportPage.CheckLegacy, ReportViewerActivity.reportPage(offline))
    }

    @Test
    fun `a legacy report is shown`() {
        assertEquals(ReportPage.Show("<old/>"), ReportViewerActivity.reportPage(offline, Result.success("<old/>")))
    }

    @Test
    fun `no report anywhere is the only case that says so`() {
        assertEquals(ReportPage.NotFound, ReportViewerActivity.reportPage(none, none))
    }

    @Test
    fun `a failed read is reported as unavailable, not missing`() {
        assertEquals(ReportPage.Unavailable, ReportViewerActivity.reportPage(offline, none))
        assertEquals(ReportPage.Unavailable, ReportViewerActivity.reportPage(none, offline))
    }
}
