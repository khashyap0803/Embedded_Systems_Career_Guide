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
    val n: Int,      // Question number
    val q: String,   // Question text
    val u: String    // User answer
)

