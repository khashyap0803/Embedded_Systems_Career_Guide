package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiReportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The report's wall-clock budget.
 *
 * Two separate things were wrong and these tests pin both.
 *
 * The budget was 10 minutes while a full report measured 13m11s on device
 * (23:00:24 -> 23:13:35, ten calls). Every call past minute 10 therefore ran
 * with retries switched off, and the calls past minute 10 are the last ones -
 * the roadmap. The ladder turned itself off for the part of the run it was most
 * needed in, and got worse under load, because load is what stretches the run.
 *
 * Separately, the budget did not bound anything. It was consulted only in the
 * failure handlers, so it decided whether to RETRY but never whether to DIAL. A
 * report whose budget had expired still made one full attempt per remaining
 * call, and an attempt is a 180s read timeout. The constant read like a ceiling
 * and was not one.
 */
class ReportBudgetTest {

    private val svc = GeminiReportService()

    private val minute = 60L * 1_000_000_000L
    private val second = 1_000_000_000L

    /** The old constant, kept here so the regression it caused stays visible. */
    private val oldBudgetMinutes = 10L

    /** The 180s readTimeout on NetworkModule.longTimeoutClient. */
    private val readTimeoutSeconds = 180L

    /**
     * A clean run with zero retries, measured on a release build on device:
     * 11:27:01 -> 11:43:34. Ten calls, ~99s each. This is the number the budget
     * is sized against, and it supersedes the earlier 13m11s figure.
     */
    private val measuredRunSeconds = 16L * 60L + 33L

    /** Calls a 50-question report makes on the live path: 7 chunks + 1 body + 2 roadmap halves. */
    private val livePathCalls = 7 + 1 + 2

    // ---- The arithmetic ------------------------------------------------------

    @Test
    fun `the budget is forty-five minutes of nanoseconds`() {
        assertEquals(45L, svc.REPORT_BUDGET_MINUTES)
        assertEquals(45L * minute, svc.reportDeadlineNanos(0L))
        // Offset from the start instant, not from zero.
        assertEquals(5L * minute + 45L * minute, svc.reportDeadlineNanos(5L * minute))
    }

    @Test
    fun `the budget covers the measured clean run with headroom`() {
        val runNanos = measuredRunSeconds * second
        assertTrue(
            "the budget must not expire during a ${measuredRunSeconds}s run",
            svc.reportDeadlineNanos(0L) > runNanos
        )
        // 2.72x the clean run. The margin is not generosity: with
        // OLLAMA_NUM_PARALLEL=2 a third concurrent student makes every call
        // queue, so a clean run stretches roughly with ceil(concurrency / 2).
        // At 1.21x - which is what 20 minutes gave - three students at once is
        // already past the ceiling, and the budget would kill runs that succeed
        // today rather than only the pathological ones.
        val headroomPercent = (svc.reportDeadlineNanos(0L) - runNanos) * 100 / runNanos
        assertTrue("headroom was only $headroomPercent%", headroomPercent >= 150)
    }

    @Test
    fun `the budget would not have cut off a clean run under cohort load`() {
        // Three students sharing a two-slot server: every call queues, the run
        // stretches by about ceil(3 / 2) = 1.5x. This is the case the previous
        // 20-minute value could not absorb.
        val underLoad = measuredRunSeconds * 3 / 2
        assertTrue(
            "a ${underLoad}s loaded run must still fit",
            underLoad * second < svc.reportDeadlineNanos(0L)
        )
        assertTrue(
            "and 20 minutes demonstrably could not absorb it",
            underLoad * second > 20L * minute
        )
    }

    @Test
    fun `the worst case bounded wall-clock is the budget plus one read timeout`() {
        // The deadline stops attempts from STARTING. It cannot abort one already
        // in flight, so an attempt beginning a microsecond inside the deadline
        // still owns its full 180s. That is the whole bound - there is no second
        // term, because no further attempt can start behind it.
        val worstCaseSeconds = svc.REPORT_BUDGET_MINUTES * 60L + readTimeoutSeconds
        assertEquals(2_880L, worstCaseSeconds)
        assertEquals(48L, worstCaseSeconds / 60L)
        // Less than half of what this path allows today: ~90.5 min of generic
        // failures, ~110 once a 429 can park a call on the server's Retry-After.
        assertTrue("must beat the unbounded worst case", worstCaseSeconds < 90L * 60L)
    }

    @Test
    fun `no retry-free run can reach the budget before the read timeout stops it`() {
        // For ten calls to fill 45 minutes the average call would have to run
        // 270s, and a call is abandoned at 180s. So the ceiling cannot cut off a
        // run that would otherwise have succeeded without retrying.
        val requiredPerCallSeconds = svc.REPORT_BUDGET_MINUTES * 60L / livePathCalls
        assertEquals(270L, requiredPerCallSeconds)
        assertTrue(
            "a call that long is already dead at the read timeout",
            requiredPerCallSeconds > readTimeoutSeconds
        )
    }

    @Test
    fun `budgetSpent treats a missing deadline as no budget at all`() {
        // The legacy generateReport path threads no deadline into any of its ten
        // calls. This must not invent one for it.
        assertFalse(svc.budgetSpent(nowNanos = Long.MAX_VALUE, deadlineNanos = null))
    }

    @Test
    fun `budgetSpent is exclusive at the deadline itself`() {
        val deadline = 10L * minute
        assertFalse(svc.budgetSpent(deadline - 1L, deadline))
        assertFalse("the deadline instant is still inside the budget", svc.budgetSpent(deadline, deadline))
        assertTrue(svc.budgetSpent(deadline + 1L, deadline))
    }

    // ---- The dead zone is gone -----------------------------------------------

    @Test
    fun `a call in the final third of a run can still retry`() = runBlocking {
        // Minute 13 of the measured 13m11s run: the last call, the roadmap, the
        // one the old budget could never retry. Deadline is what a run that
        // started 13 minutes ago would be holding.
        val startedAt = System.nanoTime() - 13L * minute
        var attempts = 0

        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = svc.reportDeadlineNanos(startedAt)
            ) { _, _, _ ->
                attempts++
                throw Exception("API call failed: 502")
            }
            fail("a call failing three times should still propagate")
        } catch (e: Exception) {
            assertFalse(
                "must not have been refused for budget",
                e is GeminiReportService.ReportBudgetExpiredException
            )
        }

        assertEquals("the ladder must still be live 13 minutes in", 3, attempts)
    }

    @Test
    fun `the same call under the old ten-minute budget was refused outright`() = runBlocking {
        // Identical to the test above except for the budget, which is the point:
        // nothing about the call changed, only how much clock it was given.
        val startedAt = System.nanoTime() - 13L * minute
        val oldDeadline = startedAt + oldBudgetMinutes * minute
        var attempts = 0

        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = oldDeadline
            ) { _, _, _ ->
                attempts++
                throw Exception("API call failed: 502")
            }
            fail("an expired budget must propagate")
        } catch (e: GeminiReportService.ReportBudgetExpiredException) {
            // expected
        }

        // Held against the test above: same code, same call, same failure, and
        // the only difference is how much clock the report was given. At ten
        // minutes a call thirteen minutes in gets no ladder at all - which is
        // precisely where the roadmap runs.
        assertEquals("a 10 minute budget leaves nothing for minute 13", 0, attempts)
    }

    // ---- The budget now bounds the clock, not just the retries ---------------

    @Test
    fun `a call whose budget is already spent never reaches the network`() = runBlocking {
        // This is what makes the worst case defensible. Before, an expired
        // budget still cost one full 180s attempt per remaining call - ten calls
        // left meant thirty minutes spent entirely past the deadline.
        var attempts = 0
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = System.nanoTime() - second
            ) { _, _, _ ->
                attempts++
                "generated"
            }
            fail("an expired budget must not produce a result")
        } catch (e: GeminiReportService.ReportBudgetExpiredException) {
            // expected
        }
        assertEquals("not one request may be dialled past the deadline", 0, attempts)
    }

    @Test
    fun `a budget that expires mid-ladder stops the next attempt and keeps the real cause`() = runBlocking {
        // Expires 1.5s out: attempt 1 fails, the ladder backs off 1s, attempt 2
        // fails, and by the 2s backoff the budget is gone. The exception the
        // caller sees should be the 502 that was actually happening, not a
        // budget notice that hides it.
        val deadline = System.nanoTime() + 1_500_000_000L
        var attempts = 0
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = deadline
            ) { _, _, _ ->
                attempts++
                throw Exception("API call failed: 502")
            }
            fail("the ladder should have given up")
        } catch (e: Exception) {
            assertFalse(
                "the underlying failure must not be replaced by a budget notice",
                e is GeminiReportService.ReportBudgetExpiredException
            )
            assertEquals("API call failed: 502", e.message)
        }
        assertTrue("should have stopped short of all three attempts", attempts < 3)
    }

    @Test
    fun `a spent budget degrades the report rather than failing it`() = runBlocking {
        // ReportBudgetExpiredException must stay an ordinary Exception so the
        // per-chunk handlers in generateReportFromKey mark the report degraded
        // and carry on. A report missing one section is a degraded report, not a
        // dead one.
        var degraded = false
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = System.nanoTime() - second
            ) { _, _, _ -> "generated" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            degraded = true
        }
        assertTrue("a spent budget must be catchable as a degradation", degraded)
    }

    // ---- Cancellation still outranks everything ------------------------------

    @Test
    fun `a cancelled call escapes the degradation handler instead of producing a report`() =
        runBlocking {
            // The exact handler shape every call site in generateReportFromKey
            // wraps its call in. If the CancellationException catch were ever
            // reordered below the generic one, `degraded` would flip true and a
            // filler report would be built and saved for a student who stopped
            // the run themselves - the defect this ordering exists to prevent.
            //
            // Covers the service boundary only: the Activity's own
            // saveReportToFirebaseSync is unreachable from a JVM unit test. What
            // it does establish is that a cancelled run hands the caller nothing
            // to save.
            var degraded = false
            var reportToSave: String? = null

            try {
                try {
                    svc.callGeminiAPIWithRetry(prompt = "p", maxRetries = 3) { _, _, _ ->
                        throw kotlinx.coroutines.CancellationException("Student stopped report generation")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    degraded = true
                    reportToSave = "<html>filler report</html>"
                }
                fail("cancellation must propagate past the degradation handler")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected
            }

            assertFalse("cancellation must never be read as degradation", degraded)
            assertNull("a cancelled run must leave nothing to persist", reportToSave)
        }

    @Test
    fun `cancellation is not rewritten as a budget failure`() = runBlocking {
        // A student who stops a run at minute 19 is cancelling, not running out
        // of time, and the two must not be conflated: only one of them is
        // allowed to produce a saved report.
        val startedAt = System.nanoTime() - 19L * minute
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = svc.reportDeadlineNanos(startedAt)
            ) { _, _, _ ->
                throw kotlinx.coroutines.CancellationException("Student stopped report generation")
            }
            fail("cancellation must propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("Student stopped report generation", e.message)
        }
    }
}
