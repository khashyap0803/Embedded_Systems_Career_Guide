package com.example.embeddedsystemscareerguide.models

data class AssessmentReport(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val reportHtml: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val totalQuestions: Int = 50,
    val score: Int = 0
)

data class QuestionAnswer(
    val n: Int,      // Display position in the report, 1-based
    val q: String,   // Question text
    val u: String,   // User answer
    /**
     * The question's real id from the asset, which is what the answer key is
     * keyed by.
     *
     * [n] is a display ordinal and the two only coincide because the ids
     * currently happen to run 1..50 in order - nothing enforces that. Keying
     * the answer key off [n] would silently attach question 7's feedback and
     * reference answer to whatever sits seventh the day someone reorders or
     * renumbers the asset. Defaults to 0 so existing construction sites and
     * deserialisation are unaffected.
     */
    val id: Int = 0
)

