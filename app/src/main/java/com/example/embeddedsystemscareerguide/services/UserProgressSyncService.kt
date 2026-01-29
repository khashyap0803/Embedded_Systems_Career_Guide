package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for syncing user progress data between local SharedPreferences and Firebase Firestore.
 * Uses username-based paths: users/{username}/data/progress
 */
class UserProgressSyncService(private val context: Context) {

    companion object {
        private const val TAG = "UserProgressSync"
        
        // SharedPreferences keys (matching LearningPathFragment)
        private const val PREFS_NAME = "learning_progress"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_CURRENT_STAGE = "current_stage"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_LAST_VISIT_DATE = "last_visit_date"
        private const val KEY_COMPLETED_STAGES = "completed_stages"
        private const val KEY_STAGE_STARS = "stage_stars_"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
        
        // Firestore paths
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DATA = "data"
        private const val DOCUMENT_PROGRESS = "progress"
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
     * Read current progress from local SharedPreferences
     */
    fun getLocalProgress(): UserProgress {
        val totalXP = prefs.getInt(KEY_TOTAL_XP, 0)
        val currentStage = prefs.getInt(KEY_CURRENT_STAGE, 1)
        val streak = prefs.getInt(KEY_STREAK, 1)
        val bestStreak = prefs.getInt(KEY_BEST_STREAK, 1)
        val lastVisitDate = prefs.getString(KEY_LAST_VISIT_DATE, "") ?: ""
        val completedStagesSet = prefs.getStringSet(KEY_COMPLETED_STAGES, emptySet()) ?: emptySet()
        
        // Load stage stars
        val stageStars = mutableMapOf<String, Int>()
        for (stageId in completedStagesSet) {
            val stars = prefs.getInt(KEY_STAGE_STARS + stageId, 0)
            if (stars > 0) {
                stageStars[stageId] = stars
            }
        }

        return UserProgress(
            totalXP = totalXP,
            currentStage = currentStage,
            streak = streak,
            bestStreak = bestStreak,
            lastVisitDate = lastVisitDate,
            completedStages = completedStagesSet.toList(),
            stageStars = stageStars,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Save progress to local SharedPreferences
     */
    fun saveLocalProgress(progress: UserProgress) {
        val editor = prefs.edit()
        
        editor.putInt(KEY_TOTAL_XP, progress.totalXP)
        editor.putInt(KEY_CURRENT_STAGE, progress.currentStage)
        editor.putInt(KEY_STREAK, progress.streak)
        editor.putInt(KEY_BEST_STREAK, progress.bestStreak)
        editor.putString(KEY_LAST_VISIT_DATE, progress.lastVisitDate)
        editor.putStringSet(KEY_COMPLETED_STAGES, progress.completedStages.toSet())
        editor.putBoolean(KEY_FIRST_LAUNCH, false)
        
        // Save stage stars
        for ((stageId, stars) in progress.stageStars) {
            editor.putInt(KEY_STAGE_STARS + stageId, stars)
        }
        
        // Also update home page progress
        updateHomeProgress(editor, progress)
        
        editor.apply()
        
        Log.d(TAG, "Saved local progress: XP=${progress.totalXP}, Stages=${progress.completedStages.size}")
    }
    
    /**
     * Update home page progress values for consistency
     */
    private fun updateHomeProgress(editor: SharedPreferences.Editor, progress: UserProgress) {
        val totalStages = 16 // Total stages in the app
        val progressPercentage = ((progress.completedStages.size.toFloat() / totalStages) * 100).toInt()
        
        editor.putInt("home_total_xp", progress.totalXP)
        editor.putInt("home_current_level", progress.currentStage)
        editor.putInt("home_streak", progress.streak)
        editor.putInt("home_progress_percentage", progressPercentage)
        editor.putInt("home_completed_stages", progress.completedStages.size)
        editor.putInt("home_total_stages", totalStages)
    }

    /**
     * Save progress to Firebase Firestore (cloud) using username path
     * Path: users/{username}/data/progress
     * @return true if successful, false otherwise
     */
    suspend fun saveProgressToCloud(): Boolean {
        val username = getCurrentUsername()
        if (username == null) {
            Log.w(TAG, "Cannot save to cloud: Username not found")
            return false
        }

        return try {
            val progress = getLocalProgress()
            
            firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .set(progress.toMap(), SetOptions.merge())
                .await()
            
            Log.d(TAG, "Successfully saved progress to cloud for user: $username")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save progress to cloud", e)
            false
        }
    }

    /**
     * Load progress from Firebase Firestore (cloud) using username path
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load progress from cloud", e)
            null
        }
    }

    /**
     * Merge local and cloud progress, taking the better values
     * Strategy: Take higher XP, more completed stages, higher stars, better streak
     */
    fun mergeProgress(local: UserProgress, cloud: UserProgress): UserProgress {
        // Merge completed stages (union of both)
        val mergedCompletedStages = (local.completedStages + cloud.completedStages).distinct()
        
        // Merge stage stars (take higher stars for each stage)
        val mergedStageStars = mutableMapOf<String, Int>()
        val allStageIds = (local.stageStars.keys + cloud.stageStars.keys)
        for (stageId in allStageIds) {
            val localStars = local.stageStars[stageId] ?: 0
            val cloudStars = cloud.stageStars[stageId] ?: 0
            mergedStageStars[stageId] = maxOf(localStars, cloudStars)
        }
        
        // Calculate current stage based on completed stages
        val mergedCurrentStage = if (mergedCompletedStages.isEmpty()) {
            1
        } else {
            val maxCompleted = mergedCompletedStages.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
            minOf(maxCompleted + 1, 16) // Cap at 16 stages
        }
        
        // Determine which progress is more recent for time-sensitive data
        val useCloudForDate = cloud.lastUpdated > local.lastUpdated
        
        return UserProgress(
            totalXP = maxOf(local.totalXP, cloud.totalXP),
            currentStage = mergedCurrentStage,
            streak = if (useCloudForDate) cloud.streak else local.streak,
            bestStreak = maxOf(local.bestStreak, cloud.bestStreak),
            lastVisitDate = if (useCloudForDate) cloud.lastVisitDate else local.lastVisitDate,
            completedStages = mergedCompletedStages,
            stageStars = mergedStageStars,
            lastUpdated = maxOf(local.lastUpdated, cloud.lastUpdated)
        )
    }

    /**
     * Sync progress between local and cloud
     * 1. Load cloud progress
     * 2. Merge with local progress
     * 3. Save merged progress to both local and cloud
     * @return true if sync was successful
     */
    suspend fun syncProgress(): Boolean {
        val username = getCurrentUsername()
        if (username == null) {
            Log.w(TAG, "Cannot sync: Username not found")
            return false
        }

        return try {
            val localProgress = getLocalProgress()
            val cloudProgress = loadProgressFromCloud()

            val mergedProgress = if (cloudProgress != null) {
                mergeProgress(localProgress, cloudProgress)
            } else {
                // No cloud data, use local
                localProgress
            }

            // Save to local
            saveLocalProgress(mergedProgress)
            
            // Save to cloud
            firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .set(mergedProgress.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "Successfully synced progress for user: $username")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress", e)
            false
        }
    }

    /**
     * Check if user has cloud progress (for determining if data should be restored)
     */
    suspend fun hasCloudProgress(): Boolean {
        val username = getCurrentUsername() ?: return false
        
        return try {
            val document = firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection(COLLECTION_DATA)
                .document(DOCUMENT_PROGRESS)
                .get()
                .await()
            
            document.exists() && document.data?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cloud progress", e)
            false
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

        return try {
            // First load current progress from cloud
            val currentProgress = loadProgressFromCloud() ?: UserProgress()
            
            // Check if stage already completed
            if (currentProgress.completedStages.contains(stageId)) {
                Log.d(TAG, "Stage $stageId already completed, skipping XP award")
                return currentProgress
            }
            
            // Build updated progress
            val updatedCompletedStages = currentProgress.completedStages + stageId
            val updatedStageStars = currentProgress.stageStars.toMutableMap().apply {
                put(stageId, starsEarned)
            }
            val nextStageId = (stageId.toIntOrNull() ?: 0) + 1
            
            val updatedProgress = currentProgress.copy(
                totalXP = currentProgress.totalXP + xpReward,
                currentStage = minOf(nextStageId, 16),
                completedStages = updatedCompletedStages,
                stageStars = updatedStageStars,
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
            saveLocalProgress(updatedProgress)
            
            Log.d(TAG, "Stage $stageId completed in cloud with $starsEarned stars, +$xpReward XP")
            updatedProgress
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete stage in cloud", e)
            null
        }
    }
    
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

        return try {
            // First load current progress from cloud
            val currentProgress = loadProgressFromCloud() ?: return null
            
            val currentStars = currentProgress.stageStars[stageId] ?: 0
            if (newStars <= currentStars) {
                Log.d(TAG, "New stars ($newStars) not better than current ($currentStars), no update needed")
                return currentProgress
            }
            
            // Build updated progress with new stars
            val updatedStageStars = currentProgress.stageStars.toMutableMap().apply {
                put(stageId, newStars)
            }
            
            val updatedProgress = currentProgress.copy(
                stageStars = updatedStageStars,
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
            saveLocalProgress(updatedProgress)
            
            Log.d(TAG, "Stars updated in cloud for stage $stageId: $currentStars -> $newStars")
            updatedProgress
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
            val currentProgress = loadProgressFromCloud() ?: UserProgress()
            
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
            saveLocalProgress(updatedProgress)
            
            Log.d(TAG, "Streak updated in cloud: $newStreak days")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update streak in cloud", e)
            false
        }
    }
}
