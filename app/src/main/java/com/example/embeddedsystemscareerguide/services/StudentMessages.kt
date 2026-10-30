package com.example.embeddedsystemscareerguide.services

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/**
 * What a student is told when something fails, one function per area.
 *
 * Screens used to show the exception's own text - Firebase's "The supplied auth
 * credential is incorrect, malformed or has expired.", "Failed after 3 attempts:
 * ...", a JSON parser's complaint - which a student can neither read nor act on.
 * Callers keep the raw text for debugging in Log.d.
 */
object StudentMessages {

    const val NETWORK = "Couldn't reach the server. Check your internet connection and try again."
    const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a few minutes, then try again."

    const val WRONG_CREDENTIALS = "That username, email or password isn't right. Check them and try again."
    const val ACCOUNT_DISABLED = "This account has been disabled. Ask your instructor for help."
    const val USE_EMAIL_SIGN_IN =
        "This email is already registered with a password. Sign in with your email and password instead."
    const val SIGN_IN_FAILED = "Couldn't sign you in. Please try again."

    const val EMAIL_IN_USE = "An account with this email already exists. Sign in instead, or use a different email."
    const val WEAK_PASSWORD = "That password is too weak. Use at least 6 characters."
    const val INVALID_EMAIL = "That email address isn't valid. Check it and try again."
    const val USERNAME_TAKEN = "That username was just taken. Please choose a different one."
    const val REGISTER_FAILED = "Couldn't create your account. Please try again."

    const val RESET_CHECK_EMAIL = "Couldn't send a reset link to that email. Check the address and try again."
    const val RESET_FAILED = "Couldn't send the reset email. Please try again."

    const val GOOGLE_SIGN_IN_FAILED =
        "Google sign-in didn't work. Please try again, or sign in with your email and password."

    const val AI_CHECK_CONNECTION = "Check your internet connection and try again."
    const val AI_TRY_AGAIN = "Please try again in a moment."

    /** Registration found the username claimed between the check and the claim. */
    class UsernameTakenException : Exception(USERNAME_TAKEN)

    /** Signing in with email, username or Google, and the Firestore look-ups around it. */
    fun signIn(e: Throwable?): String = common(e) ?: when (authCode(e)) {
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" ->
            WRONG_CREDENTIALS
        "ERROR_USER_DISABLED" -> ACCOUNT_DISABLED
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> USE_EMAIL_SIGN_IN
        else -> SIGN_IN_FAILED
    }

    /** Creating an account, or choosing a username after Google sign-in. */
    fun register(e: Throwable?): String = when {
        e is UsernameTakenException -> USERNAME_TAKEN
        else -> common(e) ?: when (authCode(e)) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> EMAIL_IN_USE
            "ERROR_WEAK_PASSWORD" -> WEAK_PASSWORD
            "ERROR_INVALID_EMAIL" -> INVALID_EMAIL
            else -> REGISTER_FAILED
        }
    }

    /** Sending a password reset email. */
    fun passwordReset(e: Throwable?): String = common(e) ?: when (authCode(e)) {
        "ERROR_INVALID_EMAIL", "ERROR_USER_NOT_FOUND" -> RESET_CHECK_EMAIL
        else -> RESET_FAILED
    }

    /** The Google account picker's ApiException status codes. */
    fun googleSignIn(statusCode: Int): String = when (statusCode) {
        12500 -> "Google Sign-In failed. Please check your internet connection."
        12502 -> "Sign-in is currently in progress"
        // This build's signing certificate is not registered with Firebase.
        // Nothing the student can fix; email login still works.
        10 -> "Google Sign-In isn't available in this version of the app (code 10). " +
            "Please sign in with your email and password."
        7 -> "Network error. Please check your connection."
        else -> GOOGLE_SIGN_IN_FAILED
    }

    /**
     * Why AI-generated content (a quiz, a lesson, a report, challenge questions)
     * failed, as the second sentence after the screen's own "Couldn't load ...".
     * The rate-limit and server-down messages are already written for students -
     * the retry ladders carry them as exception text, sometimes wrapped - so those
     * pass through.
     */
    fun aiFailure(e: Throwable?): String {
        val texts = causes(e).mapNotNull { it.message }
        return when {
            texts.any { NetworkModule.RATE_LIMITED_MESSAGE in it } -> NetworkModule.RATE_LIMITED_MESSAGE
            texts.any { NetworkModule.SERVER_DOWN_MESSAGE in it } -> NetworkModule.SERVER_DOWN_MESSAGE
            isNetwork(e) -> AI_CHECK_CONNECTION
            else -> AI_TRY_AGAIN
        }
    }

    private fun common(e: Throwable?): String? = when {
        causes(e).any { it is FirebaseTooManyRequestsException } || authCode(e) == "ERROR_TOO_MANY_REQUESTS" ->
            TOO_MANY_ATTEMPTS
        isNetwork(e) -> NETWORK
        else -> null
    }

    private fun authCode(e: Throwable?): String? =
        causes(e).filterIsInstance<FirebaseAuthException>().firstOrNull()?.errorCode

    private fun isNetwork(e: Throwable?): Boolean = causes(e).any {
        it is FirebaseNetworkException || it is IOException ||
            (it is FirebaseFirestoreException &&
                (it.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    it.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
    }

    private fun causes(e: Throwable?): List<Throwable> = generateSequence(e) { it.cause }.take(8).toList()
}
