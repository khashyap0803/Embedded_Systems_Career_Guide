package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.GeminiReportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The deadline on the path students actually take.
 *
 * AssessmentActivity falls back to legacy generateReport whenever the answer key
 * is unreviewed, which it is, so that is the live path. Until now it threaded no
 * deadline into any of its ten calls: callGeminiAPIWithRetry took the null
 * default and budgetSpent returned false for every check. The ceiling existed
 * only on generateReportFromKey, which is inert.
 *
 * generateReport itself cannot be exercised here - it hops to Dispatchers.Main
 * for its progress callbacks, and there is no Main dispatcher in a plain JVM
 * test. What these tests pin is the layer underneath it: one deadline, shared
 * across a whole report's worth of calls, behaving identically to no deadline
 * while there is time left and refusing to dial once there is not.
 */
class LivePathBudgetTest {

    private val svc = GeminiReportService()

    private val minute = 60L * 1_000_000_000L
    private val second = 1_000_000_000L

    /** 7 feedback chunks (50 questions at CHUNK_SIZE 8) + 1 body + 2 roadmap halves. */
    private val livePathCalls = 10

    /**
     * Drives a whole report's worth of calls against one deadline, the way
     * generateReport now does, and reports what each call did.
     */
    private suspend fun runReport(
        deadlineNanos: Long?,
        behaviour: (callIndex: Int) -> String
    ): Pair<Int, Int> {
        var attemptsMade = 0
        var callsCompleted = 0
        for (i in 0 until livePathCalls) {
            try {
                svc.callGeminiAPIWithRetry(
                    prompt = "call-$i",
                    maxRetries = 3,
                    deadlineNanos = deadlineNanos
                ) { _, _, _ ->
                    attemptsMade++
                    behaviour(i)
                }
                callsCompleted++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // What generateReport's own handlers do: mark degraded, carry on.
            }
        }
        return attemptsMade to callsCompleted
    }

    // ---- Inside the budget, nothing changes ----------------------------------

    @Test
    fun `a run inside the budget behaves exactly as it did with no deadline`() = runBlocking {
        // The regression this guards against is a fix that cuts off runs which
        // succeed today. Same ten calls, same responses; the only difference is
        // whether a deadline is threaded at all. Every observable must match.
        val withoutDeadline = runReport(deadlineNanos = null) { "body-$it" }
        val withDeadline = runReport(
            deadlineNanos = svc.reportDeadlineNanos(System.nanoTime())
        ) { "body-$it" }

        assertEquals("attempt count must be unchanged", withoutDeadline.first, withDeadline.first)
        assertEquals("completed calls must be unchanged", withoutDeadline.second, withDeadline.second)
        assertEquals("every call should have succeeded first time", livePathCalls, withDeadline.first)
        assertEquals(livePathCalls, withDeadline.second)
    }

    @Test
    fun `a retry inside the budget is still allowed on every call of the run`() = runBlocking {
        // Failing once and succeeding on the retry is normal under load. Threading
        // a deadline must not turn that into a degraded report.
        var attempts = 0
        val deadline = svc.reportDeadlineNanos(System.nanoTime())
        val result = svc.callGeminiAPIWithRetry(
            prompt = "p",
            maxRetries = 3,
            deadlineNanos = deadline
        ) { _, _, _ ->
            attempts++
            if (attempts < 2) throw Exception("API call failed: 502") else "generated"
        }
        assertEquals("generated", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `the default deadline leaves room for a clean run and then some`() {
        // generateReport's default is reportDeadlineNanos(System.nanoTime()).
        // A clean run measured 16m33s on device; the default must clear it with
        // enough margin that queueing behind other students does not cut it off.
        val startedAt = System.nanoTime()
        val deadline = svc.reportDeadlineNanos(startedAt)
        val cleanRunNanos = (16L * 60L + 33L) * second

        assertFalse(
            "the budget must still be live at the end of a clean run",
            svc.budgetSpent(startedAt + cleanRunNanos, deadline)
        )
        assertFalse(
            "and still live at 1.5x a clean run, i.e. three students sharing two slots",
            svc.budgetSpent(startedAt + cleanRunNanos * 3 / 2, deadline)
        )
        assertTrue(
            "but spent well before the ~90 minute unbounded worst case",
            svc.budgetSpent(startedAt + 90L * minute, deadline)
        )
    }

    // ---- Past the budget, the run stops ------------------------------------

    @Test
    fun `one deadline is shared by the whole report rather than renewed per call`() = runBlocking {
        // This is the difference between a budget and a suggestion. Ten calls
        // each handed their own fresh ceiling would be ten times the ceiling; one
        // deadline computed at entry is what makes the bound hold.
        val spent = System.nanoTime() - second
        val (attempts, completed) = runReport(deadlineNanos = spent) { "body" }

        assertEquals("not one request may be dialled past the deadline", 0, attempts)
        assertEquals("and no call may report success", 0, completed)
    }

    @Test
    fun `a report that runs out of time stops dialling instead of finishing the queue`() = runBlocking {
        // Expires partway through. Before the budget was checked ahead of each
        // attempt, every remaining call still burned one full 180s read timeout
        // before giving up - ten calls left meant half an hour spent entirely
        // past a deadline that had already gone.
        var attemptsMade = 0
        val deadline = System.nanoTime() + 2L * second
        var completedBeforeExpiry = 0

        for (i in 0 until livePathCalls) {
            try {
                svc.callGeminiAPIWithRetry(
                    prompt = "call-$i",
                    maxRetries = 3,
                    deadlineNanos = deadline
                ) { _, _, _ ->
                    attemptsMade++
                    Thread.sleep(600)
                    "body-$i"
                }
                completedBeforeExpiry++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // degraded, as generateReport would record it
            }
        }

        assertTrue("some calls should have got away before expiry", completedBeforeExpiry in 1 until livePathCalls)
        assertTrue(
            "the remaining calls must not have dialled; made $attemptsMade of $livePathCalls",
            attemptsMade < livePathCalls
        )
    }

    @Test
    fun `running out of time degrades the report rather than crashing it`() = runBlocking {
        // ReportBudgetExpiredException must stay an ordinary Exception so
        // generateReport's existing handlers catch it, set degraded = true and
        // carry on to assembly. That is what lands the student on a real terminal
        // screen instead of a hang or a silent failure.
        var degraded = false
        var reachedCancellationHandler = false
        try {
            svc.callGeminiAPIWithRetry(
                prompt = "p",
                maxRetries = 3,
                deadlineNanos = System.nanoTime() - second
            ) { _, _, _ -> "unreachable" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            reachedCancellationHandler = true
        } catch (e: Exception) {
            degraded = true
        }
        assertTrue("a spent budget must be catchable as degradation", degraded)
        assertFalse(
            "and must never be mistaken for the student cancelling",
            reachedCancellationHandler
        )
    }

    @Test
    fun `cancellation still outranks the budget on the live path`() = runBlocking {
        // The budget check sits above the try, so it cannot intercept a
        // cancellation; and a student who stops a run at minute 40 is cancelling,
        // not running out of time. Only one of those may produce a saved report.
        val startedAt = System.nanoTime() - 40L * minute
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
