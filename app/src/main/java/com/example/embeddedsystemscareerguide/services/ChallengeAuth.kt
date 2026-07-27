package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.models.challenge.ChallengeConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Admin authorisation for the pre-release event.
 *
 * Replaces the previous client-side check, which compared the typed password
 * against a hardcoded constant and compared the signed-in email against a
 * hardcoded admin address. Both were recoverable from the APK, and neither was
 * enforced anywhere on the server.
 *
 * The claim is minted server-side and travels inside the signed Firebase ID
 * token, so a tampered client cannot forge it. The same claim must also be
 * enforced in firestore.rules / database.rules.json:
 *
 *     allow write: if request.auth.token.admin == true;
 *
 * A client-side check alone only hides UI; the rules are what actually protect
 * the data.
 */
object ChallengeAuth {

    private const val TAG = "ChallengeAuth"

    /**
     * True only if the signed-in user carries the `admin` custom claim.
     *
     * Fails closed: any error, missing user, or absent claim returns false.
     *
     * @param forceRefresh re-fetch the ID token from Firebase instead of using the
     *   cached one. Needed right after a claim is granted, because the cached token
     *   keeps the old claims until it expires (up to an hour).
     */
    suspend fun isAdmin(forceRefresh: Boolean = false): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return try {
            val token = user.getIdToken(forceRefresh).await()
            token.claims[ChallengeConstants.CLAIM_ADMIN] == true
        } catch (e: Exception) {
            // Offline, expired session, or revoked token: deny.
            Log.w(TAG, "Could not resolve admin claim, denying access", e)
            false
        }
    }
}
