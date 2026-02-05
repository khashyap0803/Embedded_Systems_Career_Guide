package com.example.embeddedsystemscareerguide.models.challenge

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

/**
 * Pre-Release Event Challenge Data Models
 * These models map to the Firebase Realtime Database structure
 */

// ============== CONFIG ==============

@IgnoreExtraProperties
data class ChallengeConfig(
    @get:PropertyName("eventActive") @set:PropertyName("eventActive")
    var eventActive: Boolean = false,
    
    @get:PropertyName("eventStartTime") @set:PropertyName("eventStartTime")
    var eventStartTime: Long = 0,
    
    @get:PropertyName("eventEndTime") @set:PropertyName("eventEndTime")
    var eventEndTime: Long = 0
)

// ============== PARTICIPANT ==============

@IgnoreExtraProperties
data class ParticipantProfile(
    @get:PropertyName("rollNumber") @set:PropertyName("rollNumber")
    var rollNumber: String = "",
    
    @get:PropertyName("registeredAt") @set:PropertyName("registeredAt")
    var registeredAt: Long = 0,
    
    @get:PropertyName("lastActiveAt") @set:PropertyName("lastActiveAt")
    var lastActiveAt: Long = 0
)

@IgnoreExtraProperties
data class ParticipantStatus(
    @get:PropertyName("currentStatus") @set:PropertyName("currentStatus")
    var currentStatus: String = "waiting", // waiting, in_progress, completed, timeout, terminated
    
    @get:PropertyName("terminationReason") @set:PropertyName("terminationReason")
    var terminationReason: String? = null, // cheating, phone_call, exit_choice, network
    
    @get:PropertyName("warningCount") @set:PropertyName("warningCount")
    var warningCount: Int = 0,
    
    @get:PropertyName("canAccessChallenge2") @set:PropertyName("canAccessChallenge2")
    var canAccessChallenge2: Boolean = false,
    
    @get:PropertyName("canAccessChallenge3") @set:PropertyName("canAccessChallenge3")
    var canAccessChallenge3: Boolean = false,
    
    @get:PropertyName("isTerminated") @set:PropertyName("isTerminated")
    var isTerminated: Boolean = false,
    
    // Extra fields for admin dashboard (populated dynamically)
    var rollNumber: String = "",
    var totalScore: Int = 0,
    var challenge1Score: Int = 0,
    var challenge2Score: Int = 0,
    var challenge3Score: Int = 0
) {
    companion object {
        const val STATUS_WAITING = "waiting"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_TIMEOUT = "timeout"
        const val STATUS_TERMINATED = "terminated"
        const val STATUS_RESUMABLE = "resumable"
        
        const val TERMINATION_CHEATING = "cheating"
        const val TERMINATION_PHONE_CALL = "phone_call"
        const val TERMINATION_EXIT_CHOICE = "exit_choice"
        const val TERMINATION_NETWORK = "network"
    }
}

// Admin dashboard details
data class ParticipantDetails(
    val rollNumber: String = "",
    val challenge1Score: Int = 0,
    val challenge2Score: Int = 0,
    val challenge3Score: Int = 0,
    val totalScore: Int = 0,
    val rank: Int = 0
)

// ============== CHALLENGE SUBMISSION ==============

@IgnoreExtraProperties
data class Challenge1Submission(
    @get:PropertyName("selectedMcu") @set:PropertyName("selectedMcu")
    var selectedMcu: String = "",
    
    @get:PropertyName("selectedComponents") @set:PropertyName("selectedComponents")
    var selectedComponents: List<String> = emptyList(),
    
    @get:PropertyName("codeBlocks") @set:PropertyName("codeBlocks")
    var codeBlocks: List<String> = emptyList(),
    
    @get:PropertyName("submittedAt") @set:PropertyName("submittedAt")
    var submittedAt: Long = 0
)

@IgnoreExtraProperties
data class Challenge2Question(
    @get:PropertyName("questionId") @set:PropertyName("questionId")
    var questionId: String = "",
    
    @get:PropertyName("scenario") @set:PropertyName("scenario")
    var scenario: String = "",
    
    @get:PropertyName("partialCode") @set:PropertyName("partialCode")
    var partialCode: String = "",
    
    @get:PropertyName("missingLineCount") @set:PropertyName("missingLineCount")
    var missingLineCount: Int = 0,
    
    @get:PropertyName("userAnswer") @set:PropertyName("userAnswer")
    var userAnswer: String = ""
)

@IgnoreExtraProperties
data class Challenge3Question(
    @get:PropertyName("questionId") @set:PropertyName("questionId")
    var questionId: String = "",
    
    @get:PropertyName("scenario") @set:PropertyName("scenario")
    var scenario: String = "",
    
    @get:PropertyName("requirements") @set:PropertyName("requirements")
    var requirements: List<String> = emptyList(),
    
    @get:PropertyName("userCode") @set:PropertyName("userCode")
    var userCode: String = ""
)

@IgnoreExtraProperties
data class ChallengeData(
    @get:PropertyName("status") @set:PropertyName("status")
    var status: String = "not_started", // not_started, in_progress, completed, timeout, terminated
    
    @get:PropertyName("startTime") @set:PropertyName("startTime")
    var startTime: Long? = null,
    
    @get:PropertyName("endTime") @set:PropertyName("endTime")
    var endTime: Long? = null,
    
    @get:PropertyName("timeTakenMs") @set:PropertyName("timeTakenMs")
    var timeTakenMs: Long? = null,
    
    @get:PropertyName("extraTimeGrantedMs") @set:PropertyName("extraTimeGrantedMs")
    var extraTimeGrantedMs: Long = 0
)

// ============== EVALUATION ==============

@IgnoreExtraProperties
data class EvaluationCategory(
    @get:PropertyName("score") @set:PropertyName("score")
    var score: Int = 0,
    
    @get:PropertyName("maxScore") @set:PropertyName("maxScore")
    var maxScore: Int = 0,
    
    @get:PropertyName("details") @set:PropertyName("details")
    var details: String = "",
    
    @get:PropertyName("errors") @set:PropertyName("errors")
    var errors: List<String> = emptyList(),
    
    @get:PropertyName("missing") @set:PropertyName("missing")
    var missing: List<String> = emptyList(),
    
    @get:PropertyName("notes") @set:PropertyName("notes")
    var notes: String = ""
)

@IgnoreExtraProperties
data class EvaluationResult(
    @get:PropertyName("attemptCompleteness") @set:PropertyName("attemptCompleteness")
    var attemptCompleteness: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("syntaxCorrectness") @set:PropertyName("syntaxCorrectness")
    var syntaxCorrectness: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("logicAccuracy") @set:PropertyName("logicAccuracy")
    var logicAccuracy: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("criticalElements") @set:PropertyName("criticalElements")
    var criticalElements: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("codeQuality") @set:PropertyName("codeQuality")
    var codeQuality: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("errorCount") @set:PropertyName("errorCount")
    var errorCount: EvaluationCategory = EvaluationCategory(),
    
    @get:PropertyName("totalScore") @set:PropertyName("totalScore")
    var totalScore: Int = 0,
    
    @get:PropertyName("maxScore") @set:PropertyName("maxScore")
    var maxScore: Int = 100,
    
    @get:PropertyName("percentage") @set:PropertyName("percentage")
    var percentage: Double = 0.0,
    
    @get:PropertyName("weightedScore") @set:PropertyName("weightedScore")
    var weightedScore: Int = 0,
    
    @get:PropertyName("feedback") @set:PropertyName("feedback")
    var feedback: String = "",
    
    @get:PropertyName("evaluatedAt") @set:PropertyName("evaluatedAt")
    var evaluatedAt: Long = 0
)

@IgnoreExtraProperties
data class ChallengeScores(
    @get:PropertyName("rawScore") @set:PropertyName("rawScore")
    var rawScore: Int = 0,
    
    @get:PropertyName("weightedScore") @set:PropertyName("weightedScore")
    var weightedScore: Int = 0
)

// ============== UNIVERSAL RANKING ==============

@IgnoreExtraProperties
data class UniversalRanking(
    @get:PropertyName("challenge1Score") @set:PropertyName("challenge1Score")
    var challenge1Score: Int = 0,
    
    @get:PropertyName("challenge2Score") @set:PropertyName("challenge2Score")
    var challenge2Score: Int = 0,
    
    @get:PropertyName("challenge3Score") @set:PropertyName("challenge3Score")
    var challenge3Score: Int = 0,
    
    @get:PropertyName("totalWeightedScore") @set:PropertyName("totalWeightedScore")
    var totalWeightedScore: Int = 0,
    
    @get:PropertyName("maxPossibleScore") @set:PropertyName("maxPossibleScore")
    var maxPossibleScore: Int = 450, // 100 + 150 + 200
    
    @get:PropertyName("percentage") @set:PropertyName("percentage")
    var percentage: Double = 0.0,
    
    @get:PropertyName("totalTimeTakenMs") @set:PropertyName("totalTimeTakenMs")
    var totalTimeTakenMs: Long = 0,
    
    @get:PropertyName("rank") @set:PropertyName("rank")
    var rank: Int = 0,
    
    @get:PropertyName("lastUpdatedAt") @set:PropertyName("lastUpdatedAt")
    var lastUpdatedAt: Long = 0
)

// ============== RANKING ENTRY (for leaderboard) ==============

@IgnoreExtraProperties
data class RankingEntry(
    @get:PropertyName("rollNumber") @set:PropertyName("rollNumber")
    var rollNumber: String = "",
    
    @get:PropertyName("totalScore") @set:PropertyName("totalScore")
    var totalScore: Int = 0,
    
    @get:PropertyName("ch1") @set:PropertyName("ch1")
    var ch1: Int = 0,
    
    @get:PropertyName("ch2") @set:PropertyName("ch2")
    var ch2: Int = 0,
    
    @get:PropertyName("ch3") @set:PropertyName("ch3")
    var ch3: Int = 0,
    
    @get:PropertyName("totalTimeMs") @set:PropertyName("totalTimeMs")
    var totalTimeMs: Long = 0
)

// ============== GEMINI QUESTION MODELS ==============

data class GeneratedQuestion(
    val questionId: String,
    val scenario: String,
    val components: List<String>,
    val problemStatement: String,
    val partialCode: String = "",  // For Ch.2
    val missingLineCount: Int = 0, // For Ch.2
    val requirements: List<String> = emptyList(), // For Ch.3
    val hints: List<String> = emptyList(),
    val correctAnswer: String = "",
    val referenceCode: String = "", // For Ch.3
    val evaluationCriteria: List<String> = emptyList()
)

// ============== CHALLENGE CONSTANTS ==============

object ChallengeConstants {
    // Time limits in milliseconds
    const val CHALLENGE_1_TIME_MS = 20 * 60 * 1000L  // 20 minutes
    const val CHALLENGE_2_TIME_MS = 20 * 60 * 1000L  // 20 minutes
    const val CHALLENGE_3_TIME_MS = 30 * 60 * 1000L  // 30 minutes
    
    // Score weights
    const val CHALLENGE_1_WEIGHT = 1.0
    const val CHALLENGE_2_WEIGHT = 1.5
    const val CHALLENGE_3_WEIGHT = 2.0
    
    // Max scores
    const val MAX_RAW_SCORE = 100
    const val MAX_WEIGHTED_SCORE_CH1 = 100
    const val MAX_WEIGHTED_SCORE_CH2 = 150
    const val MAX_WEIGHTED_SCORE_CH3 = 200
    const val MAX_TOTAL_SCORE = 450
    
    // Questions per challenge
    const val QUESTIONS_PER_CHALLENGE = 3
    
    // Firebase paths
    const val PATH_PRE_RELEASE_EVENT = "preReleaseEvent"
    const val PATH_CONFIG = "config"
    const val PATH_PARTICIPANTS = "participants"
    const val PATH_RANKINGS = "rankings"
    const val PATH_UNIVERSAL = "universal"
    
    // User credentials
    const val USER_EMAIL = "exam1234@challenge.app"
    const val USER_PASSWORD = "exam1234"
    const val ADMIN_EMAIL = "admin@challenge.app"
    const val ADMIN_PASSWORD = "admin1234"
    
    // Roll number pattern
    val ROLL_NUMBER_REGEX = Regex("^1601[0-9]{8}$")
}
