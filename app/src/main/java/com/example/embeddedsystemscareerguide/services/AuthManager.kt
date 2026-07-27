package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.google.firebase.auth.FirebaseAuth

/**
 * Single place to sign a user out.
 *
 * Previously logout was implemented three times (MainActivity, ProfileFragment,
 * SettingsFragment) as near-identical copies, each of which cleared the identity
 * key (`user_prefs.current_username`) AFTER `UserProgressSyncService.clearLocalProgress()`
 * inside one try block, and swallowed any exception by simply navigating to Login
 * anyway. If `clearLocalProgress()` (or even the `UserProgressSyncService`
 * constructor, via `requireContext()` on a Fragment mid-teardown) threw, the
 * `user_prefs` clear was skipped entirely - so `current_username` survived sign-out.
 * The next account signed into on that device would then read and write the
 * previous user's Firestore documents under `users/{thatUsername}/...`.
 *
 * [logout] clears the identity key FIRST and unconditionally, before anything that
 * could plausibly throw, so an error in a later step can never suppress it.
 */
object AuthManager {

    private const val TAG = "AuthManager"

    /**
     * Sign the current user out.
     *
     * Always returns normally (never throws) - callers should navigate to
     * LoginActivity and finish their host regardless of the return value, since a
     * user pressing "Logout" expects to land on the login screen either way. The
     * boolean only reports whether every step actually succeeded, for logging.
     */
    fun logout(context: Context): Boolean {
        var clean = true

        // 1. The identity key. Cleared first, on its own, so nothing downstream can
        //    prevent it from happening.
        try {
            context.getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (e: Exception) {
            clean = false
            Log.e(TAG, "Failed to clear user_prefs during logout - identity key may persist", e)
        }

        // 2. Local progress cache (best effort; the cloud copy is the source of truth).
        try {
            UserProgressSyncService(context).clearLocalProgress()
        } catch (e: Exception) {
            clean = false
            Log.e(TAG, "Failed to clear local progress during logout", e)
        }

        // 3. Firebase session.
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            clean = false
            Log.e(TAG, "FirebaseAuth.signOut() threw during logout", e)
        }

        return clean
    }
}
