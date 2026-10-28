package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test

/**
 * A challenge resume abandoned mid-restore caught its own cancellation and fell
 * through to the new-challenge path, which called startChallengeN. That call's
 * suspendCancellableCoroutine block runs even on a cancelled job, so its RTDB
 * write landed anyway and reset the participant's status and start time.
 */
class ChallengeStartCancellationTest {

    /**
     * The service is built with no FirebaseDatabase behind it: the constructor
     * needs a live FirebaseApp, so the instance is allocated without running it.
     * Any attempt to write therefore fails with a NullPointerException on the
     * missing database - which is exactly what a cancelled start must not reach.
     */
    private val service: PreReleaseEventService = run {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, PreReleaseEventService::class.java) as PreReleaseEventService
    }

    private fun assertCancelledStartWritesNothing(start: suspend () -> Boolean) {
        try {
            runBlocking {
                cancel("participant left during the restore")
                start()
            }
            fail("a cancelled start must not complete")
        } catch (e: CancellationException) {
            // expected: it gave up before touching the database
        }
    }

    @Test
    fun `a cancelled start of challenge 1 writes nothing`() =
        assertCancelledStartWritesNothing { service.startChallenge1("21CS001") }

    @Test
    fun `a cancelled start of challenge 2 writes nothing`() =
        assertCancelledStartWritesNothing { service.startChallenge2("21CS001") }

    @Test
    fun `a cancelled start of challenge 3 writes nothing`() =
        assertCancelledStartWritesNothing { service.startChallenge3("21CS001") }
}
