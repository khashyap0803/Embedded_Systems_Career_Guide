package com.example.embeddedsystemscareerguide.models

import com.google.firebase.firestore.PropertyName

/**
 * Main learning stage data model that supports both local gamified UI and Firebase storage
 */
data class LearningStage(
    val id: String = "", // Firebase document ID
    @get:PropertyName("topicName") @set:PropertyName("topicName") var topicName: String = "",
    val title: String = "", // Display title for UI
    val subtitle: String = "", // Display subtitle for UI
    val description: String = "",
    val icon: String = "",
    val iconRes: Int = 0, // Local icon resource ID
    val color: String = "",
    val xpReward: Int = 0,
    val topics: List<String> = emptyList(),
    val unlockRequirement: String = "",
    val estimatedDuration: String = "",
    val order: Int = 0,
    val type: String = "foundation", // Stage type for categorization
    var isUnlocked: Boolean = false, // Changed to var for mutability
    var isCompleted: Boolean = false, // Changed to var for mutability
    var starsEarned: Int = 0, // Changed to var for mutability
    var progress: Int = 0 // Changed to var for mutability
)

/**
 * User's overall progress tracking
 */
data class UserProgress(
    val totalXP: Int = 0,
    val currentStage: Int = 1,
    val streakDays: Int = 0,
    val completedStages: List<Int> = emptyList(),
    val stageProgress: Map<Int, StageProgress> = emptyMap()
)

/**
 * Individual stage progress data
 */
data class StageProgress(
    val status: String = "locked", // "locked", "unlocked", "completed"
    val stars: Int = 0,
    val score: Int = 0,
    val stageId: Int = 0,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val starsEarned: Int = 0,
    val progress: Int = 0,
    val completedTopics: List<String> = emptyList(),
    val quizScores: List<Int> = emptyList(),
    val timeSpent: Long = 0L
)

/**
 * Combined class for UI display
 */
data class LearningStageItem(
    val stage: LearningStage,
    val progress: StageProgress
)

/**
 * Stage types enumeration
 */
enum class StageType(val displayName: String, val colorHex: String) {
    FOUNDATION("Foundation", "#10B981"),
    MICROCONTROLLER("Microcontroller", "#3B82F6"),
    PROGRAMMING("Programming", "#8B5CF6"),
    COMMUNICATION("Communication", "#F59E0B"),
    REALTIME("Real-Time", "#EF4444"),
    IOT("IoT", "#06B6D4"),
    ADVANCED("Advanced", "#F97316"),
    INDUSTRY("Industry", "#84CC16")
}

/**
 * Alias for backward compatibility with existing code
 */
typealias GameStage = LearningStage
