package com.example.embeddedsystemscareerguide

/**
 * M2-M3 fix: Centralized constants for the application
 * Consolidates magic numbers and SharedPreferences keys for maintainability
 */
object AppConstants {

    // ==================== Learning Path ====================
    
    /** Total number of learning stages in the curriculum */
    const val TOTAL_LEARNING_STAGES = 16
    
    /** M1 fix: Number of questions per quiz (updated to match actual usage) */
    const val QUESTIONS_PER_QUIZ = 10
    
    /** Number of questions requested per API call (to avoid truncation) */
    const val QUESTIONS_PER_API_BATCH = 5
    
    /** Chunk size for processing questions in report generation */
    const val REPORT_CHUNK_SIZE = 15
    
    /** Maximum chat messages to keep in memory */
    const val MAX_CHAT_MESSAGES = 100
    
    // ==================== XP & Levels ====================
    
    /** XP required per level */
    const val XP_PER_LEVEL = 500
    
    /** Default XP reward per stage */
    const val DEFAULT_STAGE_XP = 100
    
    // ==================== Star Ratings ====================
    
    /** Minimum percentage for 1 star */
    const val STAR_1_THRESHOLD = 40
    
    /** Minimum percentage for 2 stars */
    const val STAR_2_THRESHOLD = 60
    
    /** Minimum percentage for 3 stars */
    const val STAR_3_THRESHOLD = 80
    
    // ==================== Animation Durations ====================
    
    /** Short animation duration in milliseconds */
    const val ANIM_DURATION_SHORT = 300L
    
    /** Medium animation duration in milliseconds */
    const val ANIM_DURATION_MEDIUM = 600L
    
    /** Long animation duration in milliseconds */
    const val ANIM_DURATION_LONG = 1000L
    
    // ==================== API Timeouts ====================
    
    /** Connect timeout in seconds */
    const val API_CONNECT_TIMEOUT = 30L
    
    /** Read timeout in seconds */
    const val API_READ_TIMEOUT = 60L
    
    /** Long read timeout for report generation */
    const val API_READ_TIMEOUT_LONG = 300L
    
    /** Write timeout in seconds */
    const val API_WRITE_TIMEOUT = 30L
    
    // ==================== Debounce Delays ====================
    
    /** Username check debounce delay in milliseconds */
    const val USERNAME_CHECK_DEBOUNCE_MS = 500L
}

/**
 * M3 fix: Centralized SharedPreferences Keys
 * All SharedPreferences keys should be referenced from here
 */
object PrefsKeys {
    
    // ==================== Preference File Names ====================
    
    const val PREFS_USER = "user_prefs"
    const val PREFS_APP = "app_prefs"
    const val PREFS_LEARNING = "learning_progress"
    const val PREFS_SECURE = "secure_user_prefs"
    
    // ==================== User Preferences ====================
    
    const val CURRENT_USERNAME = "current_username"
    const val USER_ID = "user_id"
    const val USER_EMAIL = "user_email"
    
    // ==================== Learning Progress ====================
    
    const val TOTAL_XP = "home_total_xp"
    const val CURRENT_LEVEL = "home_current_level"
    const val COMPLETED_STAGES = "home_completed_stages"  // Integer count for HomeFragment display
    const val COMPLETED_STAGES_SET = "completed_stages"   // StringSet for actual stage tracking
    const val CURRENT_STAGE = "current_stage"
    const val STREAK = "home_streak"
    const val LAST_ACTIVE_DATE = "last_visit_date"
    
    // ==================== Stage-Specific Keys (use with stageId) ====================
    
    /** Prefix for stage stars - append stage ID */
    const val STAGE_STARS_PREFIX = "stage_stars_"
    
    /** Prefix for stage completion - append stage ID */
    const val STAGE_COMPLETED_PREFIX = "stage_completed_"
    
    /** Prefix for stage unlock - append stage ID */
    const val STAGE_UNLOCKED_PREFIX = "stage_unlocked_"
    
    // ==================== Assessment ====================
    
    /** Prefix for assessment completion - append user ID */
    const val ASSESSMENT_COMPLETED_PREFIX = "assessment_completed_"
    
    // ==================== Helper Functions ====================
    
    fun stageStarsKey(stageId: Int) = "${STAGE_STARS_PREFIX}$stageId"
    fun stageCompletedKey(stageId: Int) = "${STAGE_COMPLETED_PREFIX}$stageId"
    fun stageUnlockedKey(stageId: Int) = "${STAGE_UNLOCKED_PREFIX}$stageId"
    fun assessmentCompletedKey(userId: String) = "${ASSESSMENT_COMPLETED_PREFIX}$userId"
}
