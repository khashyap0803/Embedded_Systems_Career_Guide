package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.services.NetworkModule
import com.example.embeddedsystemscareerguide.services.StudentMessages
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Screens showed exception text as it came: Firebase's "The supplied auth credential
 * is incorrect, malformed or has expired.", "Failed after 3 attempts: ...". Each area
 * now maps failures to a sentence a student can act on.
 */
class StudentMessagesTest {

    private val firebaseText = "The supplied auth credential is incorrect, malformed or has expired."

    private fun auth(code: String) = FirebaseAuthException(code, firebaseText)

    @Test
    fun `a wrong password or unknown account says the details are wrong`() {
        for (code in listOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL")) {
            assertEquals(code, StudentMessages.WRONG_CREDENTIALS, StudentMessages.signIn(auth(code)))
        }
    }

    @Test
    fun `sign-in network and rate-limit failures say what to do`() {
        assertEquals(StudentMessages.NETWORK, StudentMessages.signIn(FirebaseNetworkException("A network error has occurred.")))
        assertEquals(StudentMessages.TOO_MANY_ATTEMPTS, StudentMessages.signIn(FirebaseTooManyRequestsException("We have blocked all requests")))
        assertEquals(StudentMessages.TOO_MANY_ATTEMPTS, StudentMessages.signIn(auth("ERROR_TOO_MANY_REQUESTS")))
        // The username look-up before sign-in is a Firestore read.
        val offline = FirebaseFirestoreException("Failed to get document because the client is offline.", FirebaseFirestoreException.Code.UNAVAILABLE)
        assertEquals(StudentMessages.NETWORK, StudentMessages.signIn(offline))
    }

    @Test
    fun `other sign-in failures get a plain retry message, never the raw text`() {
        assertEquals(StudentMessages.ACCOUNT_DISABLED, StudentMessages.signIn(auth("ERROR_USER_DISABLED")))
        assertEquals(StudentMessages.USE_EMAIL_SIGN_IN, StudentMessages.signIn(auth("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL")))
        val denied = FirebaseFirestoreException("PERMISSION_DENIED: Missing or insufficient permissions.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        for (e in listOf(auth("ERROR_INTERNAL_ERROR"), denied, IllegalStateException("boom"), null)) {
            val message = StudentMessages.signIn(e)
            assertEquals(StudentMessages.SIGN_IN_FAILED, message)
            assertFalse(message.contains("PERMISSION_DENIED") || message.contains("boom"))
        }
    }

    @Test
    fun `registration failures say what to change`() {
        assertEquals(StudentMessages.EMAIL_IN_USE, StudentMessages.register(auth("ERROR_EMAIL_ALREADY_IN_USE")))
        assertEquals(StudentMessages.WEAK_PASSWORD, StudentMessages.register(auth("ERROR_WEAK_PASSWORD")))
        assertEquals(StudentMessages.INVALID_EMAIL, StudentMessages.register(auth("ERROR_INVALID_EMAIL")))
        assertEquals(StudentMessages.USERNAME_TAKEN, StudentMessages.register(StudentMessages.UsernameTakenException()))
        assertEquals(StudentMessages.NETWORK, StudentMessages.register(FirebaseNetworkException("timeout")))
        assertEquals(StudentMessages.REGISTER_FAILED, StudentMessages.register(Exception("Failed to get user ID")))
    }

    @Test
    fun `password reset failures say what to change`() {
        assertEquals(StudentMessages.RESET_CHECK_EMAIL, StudentMessages.passwordReset(auth("ERROR_INVALID_EMAIL")))
        assertEquals(StudentMessages.NETWORK, StudentMessages.passwordReset(FirebaseNetworkException("timeout")))
        assertEquals(StudentMessages.RESET_FAILED, StudentMessages.passwordReset(auth("ERROR_INTERNAL_ERROR")))
    }

    @Test
    fun `an unknown Google picker code no longer shows the code and raw text`() {
        assertEquals(StudentMessages.GOOGLE_SIGN_IN_FAILED, StudentMessages.googleSignIn(16))
        assertEquals("Network error. Please check your connection.", StudentMessages.googleSignIn(7))
    }

    @Test
    fun `AI failures keep the app's own rate-limit and server-down messages`() {
        assertEquals(NetworkModule.RATE_LIMITED_MESSAGE, StudentMessages.aiFailure(Exception(NetworkModule.RATE_LIMITED_MESSAGE)))
        // GeminiChallengeService wraps them: "Failed after 3 attempts: ..."
        assertEquals(
            NetworkModule.SERVER_DOWN_MESSAGE,
            StudentMessages.aiFailure(Exception("Failed after 3 attempts: ${NetworkModule.SERVER_DOWN_MESSAGE}"))
        )
    }

    @Test
    fun `other AI failures say to check the connection or try again, never the raw text`() {
        assertEquals(StudentMessages.AI_CHECK_CONNECTION, StudentMessages.aiFailure(SocketTimeoutException("timeout")))
        assertEquals(StudentMessages.AI_CHECK_CONNECTION, StudentMessages.aiFailure(Exception("wrapped", IOException("reset"))))
        val parse = StudentMessages.aiFailure(Exception("Expected BEGIN_ARRAY but was STRING at line 1 column 1 path $"))
        assertEquals(StudentMessages.AI_TRY_AGAIN, parse)
        assertEquals(StudentMessages.AI_TRY_AGAIN, StudentMessages.aiFailure(null))
    }
}
