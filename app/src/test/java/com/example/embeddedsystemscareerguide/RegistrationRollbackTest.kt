package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.ui.auth.RegisterActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Registration creates the Firebase Auth account first and then claims the
 * username. Only a taken username deleted the account again; any other failed
 * claim left an account with no username or profile, and the student's next
 * attempt to register failed with "email already in use".
 */
class RegistrationRollbackTest {

    private fun register(claim: suspend () -> Unit, deleteAccount: suspend () -> Unit): Exception? =
        try {
            runBlocking { RegisterActivity.claimOrRollBack(claim, deleteAccount) }
            null
        } catch (e: Exception) {
            e
        }

    @Test
    fun `a claim that fails for any reason deletes the new account`() {
        var deletions = 0
        val offline = IOException("offline")
        val error = register(claim = { throw offline }, deleteAccount = { deletions++ })
        assertEquals(1, deletions)
        assertSame("the student still sees why it failed", offline, error)
    }

    @Test
    fun `a successful claim keeps the account`() {
        var deletions = 0
        assertEquals(null, register(claim = {}, deleteAccount = { deletions++ }))
        assertEquals(0, deletions)
    }

    @Test
    fun `a failed cleanup does not hide the original failure`() {
        val denied = IllegalStateException("PERMISSION_DENIED")
        val error = register(claim = { throw denied }, deleteAccount = { throw IOException("offline too") })
        assertSame(denied, error)
    }

    @Test
    fun `a cancelled claim leaves the account alone`() {
        var deletions = 0
        try {
            runBlocking {
                RegisterActivity.claimOrRollBack(
                    claim = { throw CancellationException("left the screen") },
                    deleteAccount = { deletions++ }
                )
            }
            fail("cancellation must propagate")
        } catch (e: CancellationException) {
            // expected: the transaction may still commit
        }
        assertEquals(0, deletions)
    }
}
