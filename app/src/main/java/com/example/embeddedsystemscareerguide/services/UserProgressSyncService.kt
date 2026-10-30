package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.embeddedsystemscareerguide.AppConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.embeddedsystemscareerguide.AppDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Service for syncing user progress data between local SharedPreferences and Firebase Firestore.
 * Uses username-based paths: users/{username}/data/progress
 */
class UserProgressSyncService(private val context: Context) {

    companion object {
        private const val TAG = "UserProgressSync"

        /**
         * A Firestore write Task only completes once the server acknowledges
         * it, so awaiting one with no connectivity never returns and the
         * caller's spinner hangs indefinitely. Every cloud write here is
         * bounded by this so the UI can always recover and tell the user.
         */
        private const val CLOUD_WRITE_TIMEOUT_MS = 15_000L
        
        // SharedPreferences keys (matching LearningPathFragment)
        private const val PREFS_NAME = "learning_progress"
        private const val KEY_CURRENT_STAGE = "current_stage"
        
        // Firestore paths
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DATA = "data"
        private const val DOCUMENT_PROGRESS = "progress"

        /**
         * The progress a read-modify-write starts from, or null when it must not
         * write at all.
         *
         * resetStageProgressInCloud and updateStreakInCloud built their write from
         * `loadProgressFromCloud() ?: UserProgress()`, and that helper returns null
         * for a FAILED read as well as an absent one. A failed read - offline, a
         * rejected request, or the caller being cancelled - became a brand-new
         * user's zeros, merged over the real XP, streak and completed stages. Every
         * retake runs the reset.
         */
        internal fun writeBaseFor(load: ProgressLoad): UserProgress? = when (load) {
            is ProgressLoad.Loaded -> load.progress
            ProgressLoad.Absent -> UserProgress()
            is ProgressLoad.Failed -> null
        }

        /**
         * The fields a streak update writes - and the only ones.
         *
         * LearningPathFragment used to save the streak by merging its whole cached
         * UserProgress back: totalXP, currentStage, completedStages and stageStars
         * as they stood when the screen loaded. A stage completed after that read -
         * on a second device, or by completeStageInCloud's transaction committing
         * in between - was overwritten by the stale copy, and completedStages is an
         * array, which a merge replaces outright.
         */
        internal fun streakFields(
            streak: Int,
            bestStreak: Int,
            lastVisitDate: String,
            now: Long
        ): Map<String, Any> = mapOf(
            "streak" to streak,
            "bestStreak" to bestStreak,
            "lastVisitDate" to lastVisitDate,
            "lastUpdated" to now
        )

        /**
         * The once-a-day streak rule, unchanged from LearningPathFragment: a first
         * visit keeps the stored streak (at least 1), a second visit the same day
         * changes nothing, the day after the last visit adds 1, and a longer gap
         * starts again at 1. The best streak only ever goes up. Null when there is
         * nothing to write.
         */
        internal fun dailyStreak(progress: UserProgress, today: LocalDate): StreakUpdate? {
            val todayIso = today.toString()
            val lastVisit = progress.lastVisitDate
            val consecutive = AppDate.isYesterday(lastVisit, today)
            val newStreak = when {
                lastVisit.isEmpty() -> progress.streak.coerceAtLeast(1)
                lastVisit == todayIso -> progress.streak
                consecutive -> progress.streak + 1
                else -> 1
            }
            val newBest = maxOf(newStreak, progress.bestStreak)
            if (newStreak == progress.streak && newBest == progress.bestStreak && lastVisit == todayIso) return null
            return StreakUpdate(
                previous = progress.streak,
                streak = newStreak,
                bestStreak = newBest,
                date = todayIso,
                broken = lastVisit.isNotEmpty() && lastVisit != todayIso && !consecutive
            )
        }

        /**
         * How long the daily update waits for Firestore to confirm the streak write.
         * Offline a write never confirms, but it is already in the local cache and is
         * sent on reconnect - and Home and Learning Path wait on this before reading.
         */
        internal const val STREAK_SAVE_WAIT_MS = 3_000L

        // Which student's day has been counted, as "username|yyyy-MM-dd". Process-wide,
        // because each screen makes its own UserProgressSyncService.
        private val dailyVisitLock = Mutex()
        private var dailyVisitCounted: String? = null

        /**
         * Counts today's visit for [key]'s student once: MainActivity asks whenever it
         * starts, and Home and Learning Path ask before they read progress, so
         * whichever comes first does the update and the others wait for it and then
         * find it done. A failed read writes nothing and is tried again next time.
         *
         * @return the update, to the one caller that made it; null otherwise
         */
        internal suspend fun recordDailyVisit(
            key: String,
            today: LocalDate,
            load: suspend () -> ProgressLoad,
            save: suspend (StreakUpdate) -> Boolean,
            saveWaitMs: Long = STREAK_SAVE_WAIT_MS
        ): StreakUpdate? = dailyVisitLock.withLock {
            val counted = "$key|$today"
            if (dailyVisitCounted == counted) return null
            val progress = when (val read = load()) {
                is ProgressLoad.Loaded -> read.progress
                ProgressLoad.Absent -> UserProgress()
                is ProgressLoad.Failed -> return null
            }
            val update = dailyStreak(progress, today)
            // Unconfirmed after saveWaitMs counts as made: see STREAK_SAVE_WAIT_MS.
            if (update != null && withTimeoutOrNull(saveWaitMs) { save(update) } == false) return null
            dailyVisitCounted = counted
            update
        }

        /**
         * [recordDailyVisit], also handing back the progress it read, with today's
         * streak applied - what a read straight after it would return - so a caller
         * about to read progress anyway can skip that second read. The progress is
         * null when nothing was read (the day was already counted) or the read failed:
         * the caller then reads for itself, as before.
         */
        internal suspend fun recordDailyVisitWithProgress(
            key: String,
            today: LocalDate,
            load: suspend () -> ProgressLoad,
            save: suspend (StreakUpdate) -> Boolean,
            saveWaitMs: Long = STREAK_SAVE_WAIT_MS
        ): DailyVisit {
            var read: ProgressLoad? = null
            val update = recordDailyVisit(key, today, { load().also { read = it } }, save, saveWaitMs)
            return DailyVisit(update, progressAfterVisit(read, update))
        }

        /** The progress document as it stands after a visit that read [read] and wrote [update]. */
        internal fun progressAfterVisit(read: ProgressLoad?, update: StreakUpdate?): ProgressLoad? {
            fun UserProgress.withStreak(u: StreakUpdate) =
                copy(streak = u.streak, bestStreak = u.bestStreak, lastVisitDate = u.date)
            return when (read) {
                null, is ProgressLoad.Failed -> null
                is ProgressLoad.Loaded -> ProgressLoad.Loaded(update?.let { read.progress.withStreak(it) } ?: read.progress)
                // The streak write creates the document with only the streak fields.
                ProgressLoad.Absent -> update?.let { ProgressLoad.Loaded(UserProgress().withStreak(it)) } ?: read
            }
        }

        /** The toast for a streak update, as Learning Path used to show it, or null for none. */
        fun streakMessage(update: StreakUpdate?): String? = when {
            update == null -> null
            update.broken && update.previous > 1 ->
                "🔥 Previous streak: ${update.previous} days\nStarting fresh! Let's build a new streak!"
            update.streak > update.previous -> when (update.streak) {
                7 -> "🔥 1 WEEK STREAK! Amazing dedication!"
                14 -> "🔥 2 WEEK STREAK! You're unstoppable!"
                30 -> "🔥 30 DAY STREAK! Legendary commitment!"
                else -> if (update.streak % 10 == 0) "🔥 ${update.streak} DAY STREAK! Keep it up!" else null
            }
            else -> null
        }

        /**
         * The fields a retake's progress reset writes - and the only ones. XP and
         * the streak are left alone, so the reset needs no read first and can go
         * into the same WriteBatch as the new stages (FirestoreManager.retakeWrites).
         */
        internal fun stageResetFields(now: Long): Map<String, Any> = mapOf(
            "currentStage" to 1,
            "completedStages" to emptyList<String>(),
            "stageStars" to emptyMap<String, Int>(),
            "lastUpdated" to now
        )
    }

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val userPrefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    /**
     * Data class representing user progress
     */
    data class UserProgress(
        val totalXP: Int = 0,
        val currentStage: Int = 1,
        val streak: Int = 1,
        val bestStreak: Int = 1,
        val lastVisitDate: String = "",
        val completedStages: List<String> = emptyList(),
        val stageStars: Map<String, Int> = emptyMap(),
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        // No-argument constructor for Firestore
        constructor() : this(0, 1, 1, 1, "", emptyList(), emptyMap(), System.currentTimeMillis())
        
        fun toMap(): Map<String, Any> {
            return mapOf(
                "totalXP" to totalXP,
                "currentStage" to currentStage,
                "streak" to streak,
                "bestStreak" to bestStreak,
                "lastVisitDate" to lastVisitDate,
                "completedStages" to completedStages,
                "stageStars" to stageStars,
                "lastUpdated" to lastUpdated
            )
        }
    }

    /**
     * Get the current user's username from SharedPreferences
     */
    private fun getCurrentUsername(): String? {
        return userPrefs.getString("current_username", null)
    }

    /**
     * Get the current user's ID, or null if not logged in
     */
    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Keeps the current stage where Learning Path reads it to highlight that
     * stage. Nothing reads the rest of the progress locally any more - the
     * screens take it from the progress document - so only this is kept.
     */
    fun saveLocalCurrentStage(progress: UserProgress) {
        prefs.edit().putInt(KEY_CURRENT_STAGE, progress.currentStage).apply()
    }

    /**
     * Saves the daily streak without writing any other progress field.
     * See [streakFields].
     */
    suspend fun saveStreak(streak: Int, bestStreak: Int, lastVisitDate: String): Boolean {
        val username = getCurrentUsername()
        if (username == null) {
            Log.w(TAG, "Cannot save streak: Username not found")
            return false
        }

        return try {
            progressDocRef(username)
                .set(streakFields(streak, bestStreak, lastVisitDate, System.currentTimeMillis()), SetOptions.merge())
                .await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save streak to cloud", e)
            false
        }
    }

    /** Today's streak, as [dailyStreak] works it out. */
    data class StreakUpdate(
        val previous: Int,
        val streak: Int,
        val bestStreak: Int,
        val date: String,
        val broken: Boolean
    )

    /** What [recordDailyVisitWithProgress] did: the streak update, and the progress it read. */
    data class DailyVisit(val update: StreakUpdate?, val progress: ProgressLoad?)

    /**
     * Counts today's visit: the daily streak update, once per calendar day per student.
     * See the companion's recordDailyVisit.
     */
    suspend fun recordDailyVisit(): StreakUpdate? = recordDailyVisitWithProgress().update

    /** [recordDailyVisit], also handing back what it read; see the companion's version. */
    suspend fun recordDailyVisitWithProgress(): DailyVisit {
        val username = getCurrentUsername() ?: return DailyVisit(null, null)
        return recordDailyVisitWithProgress(
            key = username,
            today = LocalDate.now(),
            load = { loadProgressResult() },
            save = { saveStreak(it.streak, it.bestStreak, it.date) }
        )
    }

    /**
     * Outcome of a cloud progress read.
     *
     * The distinction matters: [loadProgressFromCloud] collapses "this user has no
     * progress document yet" and "the read failed" into a single null. Callers that
     * then wrote a default UserProgress back to Firestore were destroying real
     * progress whenever the device was briefly offline or a permission check
     * failed. Write paths must use [loadProgressResult] and refuse to write on
     * [Failed]; read-only display paths can keep using the nullable helper.
     */
    sealed class ProgressLoad {
        /** Document exists and was parsed. */
        data class Loaded(val progress: UserProgress) : ProgressLoad()

        /** Read succeeded; this user genuinely has no progress document yet. */
        object Absent : ProgressLoad()

        /** Read failed (offline, permission denied, timeout). Local state is unknown. */
        data class Failed(val cause: Exception?) : ProgressLoad()
    }

    /**
     * Load progress, distinguishing "absent" from "failed".
     *
     * Use this anywhere the result feeds a write.
     */
    /**
     * Shared by the plain read and by the transactional writes, so both agree
     * on how a progress document maps onto [UserProgress].
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseProgress(data: Map<String, Any?>): UserProgress = UserProgress(
        totalXP = (data["totalXP"] as? Number)?.toInt() ?: 0,
        currentStage = (data["currentStage"] as? Number)?.toInt() ?: 1,
        streak = (data["streak"] as? Number)?.toInt() ?: 1,
        bestStreak = (data["bestStreak"] as? Number)?.toInt() ?: 1,
        lastVisitDate = data["lastVisitDate"] as? String ?: "",
        completedStages = (data["completedStages"] as? List<String>) ?: emptyList(),
        stageStars = (data["stageStars"] as? Map<String, Long>)
            ?.mapValues { it.value.toInt() } ?: emptyMap(),
        lastUpdated = (data["lastUpdated"] as? Number)?.toLong()
            ?: System.currentTimeMillis()
    )

    suspend fun loadProgressResult(): ProgressLoad {
        val username = getCurrentUsername()
            ?: return ProgressLoad.Failed(IllegalStateException("No signed-in username"))

        return try {
            val document = firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .get()
                .await()

            val data = document.takeIf { it.exists() }?.data
                ?: return ProgressLoad.Absent

            ProgressLoad.Loaded(parseProgress(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load progress from cloud", e)
            ProgressLoad.Failed(e)
        }
    }

    /**
     * Load progress from Firebase Firestore (cloud) using username path.
     *
     * Returns null for BOTH "no document" and "read failed" — safe for display, but
     * never use the result to build a write. Use [loadProgressResult] for that.
     *
     * @return UserProgress if found, null otherwise
     */
    suspend fun loadProgressFromCloud(): UserProgress? {
        val username = getCurrentUsername()
        if (username == null) {
            Log.w(TAG, "Cannot load from cloud: Username not found")
            return null
        }

        return try {
            val document = firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .get()
                .await()

            if (document.exists()) {
                val data = document.data
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    val progress = UserProgress(
                        totalXP = (data["totalXP"] as? Number)?.toInt() ?: 0,
                        currentStage = (data["currentStage"] as? Number)?.toInt() ?: 1,
                        streak = (data["streak"] as? Number)?.toInt() ?: 1,
                        bestStreak = (data["bestStreak"] as? Number)?.toInt() ?: 1,
                        lastVisitDate = data["lastVisitDate"] as? String ?: "",
                        completedStages = (data["completedStages"] as? List<String>) ?: emptyList(),
                        stageStars = (data["stageStars"] as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap(),
                        lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                    Log.d(TAG, "Successfully loaded progress from cloud for user: $username")
                    progress
                } else {
                    Log.d(TAG, "No cloud progress data found for user: $username")
                    null
                }
            } else {
                Log.d(TAG, "No cloud progress document exists for user: $username")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load progress from cloud", e)
            null
        }
    }

    /**
     * Clear local progress (for testing or logout)
     */
    fun clearLocalProgress() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared local progress")
    }

    // ========== CLOUD-FIRST METHODS ==========
    
    /**
     * Complete a stage directly in cloud (primary source of truth)
     * @return Updated UserProgress if successful, null on failure
     */
    suspend fun completeStageInCloud(stageId: String, starsEarned: Int, xpReward: Int): UserProgress? {
        val username = getCurrentUsername()
        if (username == null) {
            Log.e(TAG, "Cannot complete stage: Username not found")
            return null
        }

        val docRef = progressDocRef(username)

        return try {
            // Read-modify-write inside a transaction.
            //
            // This used to read the document, compute the new totals, then
            // write them back as two separate operations. Two concurrent
            // completions - a second device, or completeStageInCloud racing
            // the streak save that LearningPathFragment launches alongside it -
            // both read the same starting XP and the later write silently threw
            // the other's award away. A transaction re-runs on contention, so
            // the second attempt sees the first one's result.
            //
            // The read inside the transaction also distinguishes "absent" from
            // "failed": a failure throws and is caught below rather than being
            // mistaken for a new user and overwriting real progress with zeros.
            val updatedProgress = withTimeout(CLOUD_WRITE_TIMEOUT_MS) {
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val current = snapshot.data?.let { parseProgress(it) } ?: UserProgress()

                    if (current.completedStages.contains(stageId)) {
                        Log.d(TAG, "Stage $stageId already completed, skipping XP award")
                        return@runTransaction current
                    }

                    val next = current.copy(
                        totalXP = current.totalXP + xpReward,
                        // No cap: the generated path length varies per user, so
                        // clamping to a constant stalled currentStage partway
                        // through a longer curriculum.
                        currentStage = (stageId.toIntOrNull() ?: 0) + 1,
                        completedStages = current.completedStages + stageId,
                        stageStars = current.stageStars.toMutableMap().apply {
                            put(stageId, starsEarned)
                        },
                        lastUpdated = System.currentTimeMillis()
                    )
                    transaction.set(docRef, next.toMap(), SetOptions.merge())
                    next
                }.await()
            }

            // Update local cache
            saveLocalCurrentStage(updatedProgress)

            Log.d(TAG, "Stage $stageId completed in cloud with $starsEarned stars, +$xpReward XP")
            updatedProgress
        } catch (e: TimeoutCancellationException) {
            // A Firestore write Task only completes on server acknowledgement,
            // so with no connectivity this never resolved and the caller's
            // spinner hung forever. Failing loudly lets the UI recover.
            Log.e(TAG, "Timed out completing stage $stageId in cloud", e)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete stage in cloud", e)
            null
        }
    }

    private fun progressDocRef(username: String) =
        firestore.collection(COLLECTION_USERS)
            .document(username)
            .collection(COLLECTION_DATA)
            .document(DOCUMENT_PROGRESS)
    
    /**
     * Update stars for a stage directly in cloud (for retakes)
     * Only updates if new stars > old stars
     * @return Updated UserProgress if successful, null on failure
     */
    suspend fun updateStarsInCloud(stageId: String, newStars: Int): UserProgress? {
        val username = getCurrentUsername()
        if (username == null) {
            Log.e(TAG, "Cannot update stars: Username not found")
            return null
        }

        val docRef = progressDocRef(username)

        return try {
            // Transactional for the same reason as completeStageInCloud: the
            // read-then-write pair could interleave with a concurrent stage
            // completion and drop whichever award landed first. A read failure
            // throws inside the transaction and is caught below, so a write is
            // never built from a failed read.
            val updatedProgress = withTimeout(CLOUD_WRITE_TIMEOUT_MS) {
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val current = snapshot.data?.let { parseProgress(it) }
                        ?: return@runTransaction null

                    val currentStars = current.stageStars[stageId] ?: 0
                    if (newStars <= currentStars) {
                        Log.d(TAG, "New stars ($newStars) not better than current ($currentStars)")
                        return@runTransaction current
                    }

                    val next = current.copy(
                        stageStars = current.stageStars.toMutableMap().apply {
                            put(stageId, newStars)
                        },
                        lastUpdated = System.currentTimeMillis()
                    )
                    transaction.set(docRef, next.toMap(), SetOptions.merge())
                    next
                }.await()
            } ?: run {
                Log.d(TAG, "No cloud progress yet, nothing to update stars against")
                return null
            }

            // Update local cache
            saveLocalCurrentStage(updatedProgress)

            Log.d(TAG, "Stars updated in cloud for stage $stageId -> $newStars")
            updatedProgress
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timed out updating stars for stage $stageId", e)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stars in cloud", e)
            null
        }
    }
    
    /**
     * Update streak in cloud
     */
    suspend fun updateStreakInCloud(newStreak: Int, bestStreak: Int, lastVisitDate: String): Boolean {
        val username = getCurrentUsername()
        if (username == null) {
            Log.e(TAG, "Cannot update streak: Username not found")
            return false
        }

        return try {
            val currentProgress = writeBaseFor(loadProgressResult()) ?: run {
                Log.w(TAG, "Not updating streak: current progress could not be read")
                return false
            }

            val updatedProgress = currentProgress.copy(
                streak = newStreak,
                bestStreak = maxOf(bestStreak, currentProgress.bestStreak),
                lastVisitDate = lastVisitDate,
                lastUpdated = System.currentTimeMillis()
            )
            
            // Save to cloud
            firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .set(updatedProgress.toMap(), SetOptions.merge())
                .await()
            
            // Update local cache
            saveLocalCurrentStage(updatedProgress)
            
            Log.d(TAG, "Streak updated in cloud: $newStreak days")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update streak in cloud", e)
            false
        }
    }
}

