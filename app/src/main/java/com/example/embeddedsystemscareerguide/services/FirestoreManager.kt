package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FirestoreManager - Cloud Data Manager for V2.0
 * 
 * IMPORTANT: Uses USERNAME as document ID (not UID) for consistent hierarchy:
 * users/{username}/data/... for all user data
 * 
 * Handles all Firestore operations for:
 * - User profiles
 * - Personalized stages
 * - Stage content
 * - Flashcards
 * - Quiz history
 * - Progress tracking
 * - Chat history
 * - Settings
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class FirestoreManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirestoreManager"
        
        // SharedPreferences keys
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_USERNAME = "current_username"
        
        // Collection names
        const val COLLECTION_USERS = "users"
        const val COLLECTION_DATA = "data"  // Sub-document for user data
        const val COLLECTION_STAGES = "stages"  // Parent collection for stage-related data
        const val COLLECTION_PERSONALIZED_STAGES = "personalized_stages"
        const val COLLECTION_STAGE_CONTENT = "stage_content"
        const val COLLECTION_FLASHCARDS = "flashcards"
        const val COLLECTION_QUIZ_HISTORY = "quiz_history"
        const val COLLECTION_PROGRESS = "progress"
        const val COLLECTION_CHAT_HISTORY = "chat_history"
        const val COLLECTION_ANALYTICS = "analytics"
        const val COLLECTION_PROJECTS = "projects"
        const val COLLECTION_INTERVIEW_PREP = "interview_prep"
        const val COLLECTION_DAILY_TIPS = "daily_tips"
        
        // Singleton instance
        @Volatile
        private var instance: FirestoreManager? = null
        
        fun getInstance(context: Context): FirestoreManager {
            return instance ?: synchronized(this) {
                instance ?: FirestoreManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get current user ID (UID) or throw if not logged in
     */
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
    }

    /**
     * Get current username from SharedPreferences
     * Falls back to UID if username not found (for backward compatibility)
     */
    private fun getCurrentUsername(): String {
        val username = prefs.getString(KEY_USERNAME, null)
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Username not found in SharedPreferences, falling back to UID")
            return getCurrentUserId()
        }
        return username
    }

    /**
     * Get user document reference using USERNAME as document ID
     * Path: users/{username}
     */
    private fun getUserDocRef(username: String = getCurrentUsername()) = 
        firestore.collection(COLLECTION_USERS).document(username)

    /**
     * Get data document reference for user
     * Path: users/{username}/data (main document)
     */
    private fun getDataDocRef(username: String = getCurrentUsername()) = 
        getUserDocRef(username).collection(COLLECTION_DATA).document("main")

    /**
     * Get stages document reference - hierarchy: users/{username}/data/stages/data
     * Path: users/{username}/data/stages/data/{subCollection}
     */
    private fun getStagesDocRef(username: String = getCurrentUsername()) = 
        getDataDocRef(username).collection(COLLECTION_STAGES).document("data")

    // ==================== USER PROFILE ====================

    /**
     * Create or update user profile
     */
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username).set(profile, SetOptions.merge()).await()
            Log.d(TAG, "User profile saved for: ${profile.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile
     */
    suspend fun getUserProfile(): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = getUserDocRef(username).get().await()
            val profile = doc.toObject(UserProfile::class.java)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            Result.failure(e)
        }
    }

    // ==================== PERSONALIZED STAGES ====================

    /**
     * Save AI-generated personalized stages
     */
    suspend fun savePersonalizedStages(stages: List<PersonalizedStage>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val batch = firestore.batch()
            
            val collectionRef = getStagesDocRef(username).collection(COLLECTION_PERSONALIZED_STAGES)
            
            // Clear existing stages first
            val existingDocs = collectionRef.get().await()
            existingDocs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            // Add new stages
            stages.forEach { stage ->
                val docRef = collectionRef.document(stage.id.toString())
                batch.set(docRef, stage)
            }
            
            batch.commit().await()
            Log.d(TAG, "Saved ${stages.size} personalized stages")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving personalized stages", e)
            Result.failure(e)
        }
    }

    /**
     * Get all personalized stages for current user
     */
    suspend fun getPersonalizedStages(): Result<List<PersonalizedStage>> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getStagesDocRef(username)
                .collection(COLLECTION_PERSONALIZED_STAGES)
                .orderBy("id")
                .get()
                .await()
            
            val stages = docs.documents.mapNotNull { doc ->
                doc.toObject(PersonalizedStage::class.java)
            }
            Log.d(TAG, "Loaded ${stages.size} personalized stages")
            Result.success(stages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting personalized stages", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user has personalized stages
     */
    suspend fun hasPersonalizedStages(): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getStagesDocRef(username)
                .collection(COLLECTION_PERSONALIZED_STAGES)
                .limit(1)
                .get()
                .await()
            !docs.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking personalized stages", e)
            false
        }
    }

    /**
     * Get assessment report for current user
     * Path: users/{username}/data/report
     * 
     * @return Map of report data, or null if no report exists
     */
    suspend fun getAssessmentReport(): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection("data")
                .document("report")
                .get()
                .await()
            
            if (doc.exists()) {
                doc.data
            } else {
                Log.d(TAG, "No assessment report found for user: $username")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting assessment report", e)
            null
        }
    }

    /**
     * Check if user has assessment report
     */
    suspend fun hasAssessmentReport(): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = firestore.collection(COLLECTION_USERS)
                .document(username)
                .collection("data")
                .document("report")
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking assessment report", e)
            false
        }
    }

    /**
     * Collect user's performance data for intelligent stage regeneration
     * This is called when user retakes assessment to consider their history
     * 
     * @return UserPerformanceData with completed stages, scores, weak/strong topics
     */
    suspend fun collectUserPerformanceData(): UserPerformanceData = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val userDoc = getUserDocRef(username)
            
            // 1. Get completed stages with stars
            val stages = userDoc.collection(COLLECTION_PERSONALIZED_STAGES)
                .get()
                .await()
            
            val completedStageIds = mutableListOf<String>()
            val stageStars = mutableMapOf<String, Int>()
            
            stages.documents.forEach { doc ->
                val stage = doc.toObject(PersonalizedStage::class.java)
                if (stage?.isCompleted == true) {
                    completedStageIds.add(stage.id.toString())
                    stageStars[stage.id.toString()] = stage.starsEarned
                }
            }
            
            // 2. Get quiz history for each stage
            val quizScores = mutableMapOf<String, Int>()
            val wrongQuestions = mutableListOf<WrongQuestionRecord>()
            
            completedStageIds.forEach { stageId ->
                val quizDocs = userDoc.collection(COLLECTION_QUIZ_HISTORY)
                    .whereEqualTo("stageId", stageId.toIntOrNull() ?: 0)
                    .get()
                    .await()
                
                if (!quizDocs.isEmpty) {
                    // Get the best score
                    val bestScore = quizDocs.documents.maxOfOrNull { doc ->
                        (doc.getLong("score") ?: 0L).toInt()
                    } ?: 0
                    quizScores[stageId] = bestScore
                    
                    // Collect wrong answers from the most recent quiz
                    val latestQuiz = quizDocs.documents.maxByOrNull { doc ->
                        doc.getLong("timestamp") ?: 0L
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val wrongAnswers = latestQuiz?.get("wrongAnswers") as? List<Map<String, String>>
                    wrongAnswers?.forEach { answer ->
                        wrongQuestions.add(WrongQuestionRecord(
                            stageId = stageId,
                            topic = answer["topic"] ?: "",
                            question = answer["question"] ?: "",
                            userAnswer = answer["userAnswer"] ?: "",
                            correctAnswer = answer["correctAnswer"] ?: ""
                        ))
                    }
                }
            }
            
            // 3. Determine weak and strong topics based on performance
            val weakTopics = mutableListOf<String>()
            val strongTopics = mutableListOf<String>()
            
            // Topics with low stars (1 or less) are weak
            stageStars.forEach { (stageId, stars) ->
                val stage = stages.documents.find { it.id == stageId }
                    ?.toObject(PersonalizedStage::class.java)
                
                val topicsForStage = stage?.topics ?: emptyList()
                if (stars <= 1) {
                    weakTopics.addAll(topicsForStage)
                } else if (stars >= 3) {
                    strongTopics.addAll(topicsForStage)
                }
            }
            
            // Also consider topics from wrong questions as weak
            wrongQuestions.forEach { record ->
                if (record.topic.isNotEmpty() && !weakTopics.contains(record.topic)) {
                    weakTopics.add(record.topic)
                }
            }
            
            // 4. Calculate overall progress
            val totalXp = stages.documents.sumOf { doc ->
                val stage = doc.toObject(PersonalizedStage::class.java)
                if (stage?.isCompleted == true) stage.xpReward else 0
            }
            
            val averageScore = if (quizScores.isNotEmpty()) {
                quizScores.values.average().toInt()
            } else 0
            
            Log.d(TAG, "Collected performance: ${completedStageIds.size} completed, " +
                    "avg score: $averageScore, weak: ${weakTopics.size}, strong: ${strongTopics.size}")
            
            UserPerformanceData(
                completedStageIds = completedStageIds,
                stageScores = quizScores,
                stageStars = stageStars,
                weakTopics = weakTopics.distinct(),
                strongTopics = strongTopics.distinct(),
                wrongQuestions = wrongQuestions,
                totalXpEarned = totalXp,
                averageQuizScore = averageScore
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting performance data", e)
            UserPerformanceData()
        }
    }

    /**
     * Delete all personalized stages (for regeneration)
     */
    suspend fun deleteAllPersonalizedStages(): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val stagesRef = getUserDocRef(username).collection(COLLECTION_PERSONALIZED_STAGES)
            
            val docs = stagesRef.get().await()
            val batch = firestore.batch()
            
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            batch.commit().await()
            Log.d(TAG, "Deleted ${docs.size()} personalized stages")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting stages", e)
            false
        }
    }

    // ==================== STAGE CONTENT ====================

    /**
     * Save AI-generated stage content
     */
    suspend fun saveStageContent(stageId: Int, content: StageContent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getStagesDocRef(username)
                .collection(COLLECTION_STAGE_CONTENT)
                .document(stageId.toString())
                .set(content)
                .await()
            Log.d(TAG, "Saved content for stage $stageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving stage content", e)
            Result.failure(e)
        }
    }

    /**
     * Get stage content
     */
    suspend fun getStageContent(stageId: Int): Result<StageContent?> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = getStagesDocRef(username)
                .collection(COLLECTION_STAGE_CONTENT)
                .document(stageId.toString())
                .get()
                .await()
            val content = doc.toObject(StageContent::class.java)
            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stage content", e)
            Result.failure(e)
        }
    }

    // ==================== FLASHCARDS ====================

    /**
     * Save flashcards for a stage
     */
    suspend fun saveFlashcards(stageId: Int, flashcards: List<Flashcard>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val data = mapOf("flashcards" to flashcards, "stageId" to stageId)
            getStagesDocRef(username)
                .collection(COLLECTION_FLASHCARDS)
                .document(stageId.toString())
                .set(data)
                .await()
            Log.d(TAG, "Saved ${flashcards.size} flashcards for stage $stageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving flashcards", e)
            Result.failure(e)
        }
    }

    /**
     * Get flashcards for a stage
     */
    suspend fun getFlashcards(stageId: Int): Result<List<Flashcard>> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = getStagesDocRef(username)
                .collection(COLLECTION_FLASHCARDS)
                .document(stageId.toString())
                .get()
                .await()
            
            @Suppress("UNCHECKED_CAST")
            val flashcardsList = doc.get("flashcards") as? List<Map<String, Any>> ?: emptyList()
            val flashcards = flashcardsList.map { map ->
                Flashcard(
                    id = (map["id"] as? Number)?.toInt() ?: 0,
                    front = map["front"] as? String ?: "",
                    back = map["back"] as? String ?: "",
                    difficulty = map["difficulty"] as? String ?: "medium",
                    category = map["category"] as? String ?: "concept",
                    needsReview = map["needsReview"] as? Boolean ?: false
                )
            }
            Result.success(flashcards)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting flashcards", e)
            Result.failure(e)
        }
    }

    /**
     * Update flashcard review status
     */
    suspend fun updateFlashcardReview(stageId: Int, flashcardId: Int, needsReview: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val flashcardsResult = getFlashcards(stageId)
            if (flashcardsResult.isSuccess) {
                val flashcards = flashcardsResult.getOrNull()?.map { card ->
                    if (card.id == flashcardId) card.copy(needsReview = needsReview) else card
                } ?: emptyList()
                saveFlashcards(stageId, flashcards)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating flashcard review", e)
            Result.failure(e)
        }
    }

    // ==================== QUIZ HISTORY ====================

    /**
     * Save quiz result
     */
    suspend fun saveQuizResult(stageId: Int, result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_QUIZ_HISTORY)
                .document("${stageId}_${System.currentTimeMillis()}")
                .set(result)
                .await()
            Log.d(TAG, "Saved quiz result for stage $stageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving quiz result", e)
            Result.failure(e)
        }
    }

    /**
     * Get quiz history for a stage
     */
    suspend fun getQuizHistory(stageId: Int): Result<List<QuizResult>> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getUserDocRef(username)
                .collection(COLLECTION_QUIZ_HISTORY)
                .whereEqualTo("stageId", stageId)
                .orderBy("timestamp")
                .get()
                .await()
            
            val results = docs.documents.mapNotNull { doc ->
                doc.toObject(QuizResult::class.java)
            }
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting quiz history", e)
            Result.failure(e)
        }
    }

    // ==================== PROGRESS TRACKING ====================

    /**
     * Save overall progress
     */
    suspend fun saveProgress(progress: UserProgress): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_PROGRESS)
                .document("current")
                .set(progress)
                .await()
            Log.d(TAG, "Progress saved: XP=${progress.totalXp}, Completed=${progress.completedStages}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress", e)
            Result.failure(e)
        }
    }

    /**
     * Get current progress
     */
    suspend fun getProgress(): Result<UserProgress?> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val doc = getUserDocRef(username)
                .collection(COLLECTION_PROGRESS)
                .document("current")
                .get()
                .await()
            val progress = doc.toObject(UserProgress::class.java)
            Result.success(progress)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting progress", e)
            Result.failure(e)
        }
    }

    // ==================== CHAT HISTORY ====================

    /**
     * Save chat message
     */
    suspend fun saveChatMessage(message: ChatMessage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_CHAT_HISTORY)
                .document(message.timestamp.toString())
                .set(message)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message", e)
            Result.failure(e)
        }
    }

    /**
     * Get recent chat history
     */
    suspend fun getChatHistory(limit: Int = 50): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getUserDocRef(username)
                .collection(COLLECTION_CHAT_HISTORY)
                .orderBy("timestamp")
                .limitToLast(limit.toLong())
                .get()
                .await()
            
            val messages = docs.documents.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chat history", e)
            Result.failure(e)
        }
    }

    /**
     * Clear chat history
     */
    suspend fun clearChatHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getUserDocRef(username)
                .collection(COLLECTION_CHAT_HISTORY)
                .get()
                .await()
            
            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Log.d(TAG, "Chat history cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing chat history", e)
            Result.failure(e)
        }
    }

    // ==================== ANALYTICS ====================

    /**
     * Save AI-generated analytics report
     */
    suspend fun saveAnalyticsReport(report: AnalyticsReport): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_ANALYTICS)
                .document(report.timestamp.toString())
                .set(report)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving analytics report", e)
            Result.failure(e)
        }
    }

    /**
     * Get latest analytics report
     */
    suspend fun getLatestAnalytics(): Result<AnalyticsReport?> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val docs = getUserDocRef(username)
                .collection(COLLECTION_ANALYTICS)
                .orderBy("timestamp")
                .limitToLast(1)
                .get()
                .await()
            
            val report = docs.documents.firstOrNull()?.toObject(AnalyticsReport::class.java)
            Result.success(report)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting analytics", e)
            Result.failure(e)
        }
    }

    // ==================== PROJECTS ====================

    /**
     * Save project suggestions
     */
    suspend fun saveProjects(projects: List<Project>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val data = mapOf("projects" to projects, "generatedAt" to System.currentTimeMillis())
            getUserDocRef(username)
                .collection(COLLECTION_PROJECTS)
                .document("suggestions")
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving projects", e)
            Result.failure(e)
        }
    }

    /**
     * Update project status
     */
    suspend fun updateProjectStatus(projectId: Int, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_PROJECTS)
                .document("status_$projectId")
                .set(mapOf("status" to status, "updatedAt" to System.currentTimeMillis()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating project status", e)
            Result.failure(e)
        }
    }

    // ==================== DAILY TIPS ====================

    /**
     * Save daily tip
     */
    suspend fun saveDailyTip(tip: DailyTip): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            getUserDocRef(username)
                .collection(COLLECTION_DAILY_TIPS)
                .document(tip.date)
                .set(tip)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily tip", e)
            Result.failure(e)
        }
    }

    /**
     * Get today's tip
     */
    suspend fun getTodaysTip(): Result<DailyTip?> = withContext(Dispatchers.IO) {
        try {
            val username = getCurrentUsername()
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            val doc = getUserDocRef(username)
                .collection(COLLECTION_DAILY_TIPS)
                .document(today)
                .get()
                .await()
            val tip = doc.toObject(DailyTip::class.java)
            Result.success(tip)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily tip", e)
            Result.failure(e)
        }
    }
}

// ==================== DATA CLASSES ====================

/**
 * User profile data
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val hasCompletedAssessment: Boolean = false,
    val assessmentScore: Int = 0
)

/**
 * Personalized learning stage
 */
data class PersonalizedStage(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val topics: List<String> = emptyList(),
    val difficulty: String = "beginner",
    val estimatedMinutes: Int = 60,
    val type: String = "theory",
    val xpReward: Int = 100,
    var isCompleted: Boolean = false,
    var starsEarned: Int = 0,
    var isUnlocked: Boolean = false
)

/**
 * Stage learning content
 */
data class StageContent(
    val stageId: Int = 0,
    val theory: String = "",
    val keyPoints: List<String> = emptyList(),
    val codeExample: CodeExample = CodeExample(),
    val commonMistakes: List<Mistake> = emptyList(),
    val proTips: List<String> = emptyList(),
    val miniChallenge: Challenge = Challenge(),
    val generatedAt: Long = System.currentTimeMillis()
)

data class CodeExample(
    val language: String = "c",
    val code: String = "",
    val explanation: String = ""
)

data class Mistake(
    val mistake: String = "",
    val solution: String = ""
)

data class Challenge(
    val task: String = "",
    val hint: String = ""
)

/**
 * Flashcard data
 */
data class Flashcard(
    val id: Int = 0,
    val front: String = "",
    val back: String = "",
    val difficulty: String = "medium",
    val category: String = "concept",
    val needsReview: Boolean = false
)

/**
 * Quiz result data
 */
data class QuizResult(
    val stageId: Int = 0,
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val correctAnswers: Int = 0,
    val starsEarned: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val timeSpentSeconds: Int = 0
)

/**
 * User progress data
 */
data class UserProgress(
    val totalXp: Int = 0,
    val completedStages: Int = 0,
    val totalStages: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: String = "",
    val stageProgress: Map<Int, StageProgress> = emptyMap(),
    val totalQuizzesTaken: Int = 0,
    val averageQuizScore: Float = 0f
)

data class StageProgress(
    val isCompleted: Boolean = false,
    val starsEarned: Int = 0,
    val contentRead: Boolean = false,
    val flashcardsReviewed: Boolean = false,
    val quizPassed: Boolean = false
)

/**
 * Chat message data
 */
data class ChatMessage(
    val role: String = "", // "user" or "assistant"
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Analytics report data
 */
data class AnalyticsReport(
    val overallAssessment: String = "",
    val strengthsAnalysis: String = "",
    val improvementAreas: String = "",
    val recommendations: List<String> = emptyList(),
    val motivationalMessage: String = "",
    val predictedCompletionDays: Int = 0,
    val weeklyGoal: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Project data
 */
data class Project(
    val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val difficulty: String = "beginner",
    val estimatedHours: Int = 0,
    val skills: List<String> = emptyList(),
    val components: List<String> = emptyList(),
    val learningOutcomes: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val status: String = "not_started" // not_started, in_progress, completed
)

/**
 * Daily tip data
 */
data class DailyTip(
    val date: String = "",
    val tip: String = "",
    val category: String = "",
    val actionItem: String = ""
)

/**
 * User's performance data for stage regeneration
 * Collected when user retakes assessment to inform new stage generation
 */
data class UserPerformanceData(
    val completedStageIds: List<String> = emptyList(),
    val stageScores: Map<String, Int> = emptyMap(), // stageId -> best score percentage
    val stageStars: Map<String, Int> = emptyMap(),  // stageId -> stars earned
    val weakTopics: List<String> = emptyList(),     // Topics with low scores
    val strongTopics: List<String> = emptyList(),   // Topics with high scores
    val wrongQuestions: List<WrongQuestionRecord> = emptyList(),
    val totalXpEarned: Int = 0,
    val averageQuizScore: Int = 0
)

/**
 * Record of a wrong answer for analysis during regeneration
 */
data class WrongQuestionRecord(
    val stageId: String = "",
    val topic: String = "",
    val question: String = "",
    val userAnswer: String = "",
    val correctAnswer: String = ""
)



