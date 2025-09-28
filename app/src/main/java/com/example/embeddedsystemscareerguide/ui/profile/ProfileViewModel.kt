package com.example.embeddedsystemscareerguide.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {

    private val _userStats = MutableLiveData<UserStats>()
    val userStats: LiveData<UserStats> = _userStats

    private val _assessmentProgress = MutableLiveData<AssessmentProgress>()
    val assessmentProgress: LiveData<AssessmentProgress> = _assessmentProgress

    private val _learningProgress = MutableLiveData<LearningProgress>()
    val learningProgress: LiveData<LearningProgress> = _learningProgress

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        // TODO: Load user data from Firebase
        // For now, set default values
        _userStats.value = UserStats(
            totalXP = 1250,
            currentLevel = 5,
            dailyStreak = 15,
            completionPercentage = 25
        )

        _assessmentProgress.value = AssessmentProgress(
            initialAssessmentCompleted = true,
            initialAssessmentScore = 75,
            finalAssessmentCompleted = false,
            finalAssessmentScore = 0
        )

        _learningProgress.value = LearningProgress(
            stagesCompleted = 3,
            totalStages = 12,
            currentStage = "Programming Fundamentals"
        )
    }

    data class UserStats(
        val totalXP: Int,
        val currentLevel: Int,
        val dailyStreak: Int,
        val completionPercentage: Int
    )

    data class AssessmentProgress(
        val initialAssessmentCompleted: Boolean,
        val initialAssessmentScore: Int,
        val finalAssessmentCompleted: Boolean,
        val finalAssessmentScore: Int
    )

    data class LearningProgress(
        val stagesCompleted: Int,
        val totalStages: Int,
        val currentStage: String
    )
}
