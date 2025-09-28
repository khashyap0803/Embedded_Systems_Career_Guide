package com.example.embeddedsystemscareerguide.ui.learningpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

class LearningPathViewModel : ViewModel() {

    private val _currentStage = MutableLiveData<Int>()
    val currentStage: LiveData<Int> = _currentStage

    private val _userXP = MutableLiveData<Int>()
    val userXP: LiveData<Int> = _userXP

    private val _userStreak = MutableLiveData<Int>()
    val userStreak: LiveData<Int> = _userStreak

    private val _userLevel = MutableLiveData<Int>()
    val userLevel: LiveData<Int> = _userLevel

    init {
        // Initialize with default values
        _currentStage.value = 3
        _userXP.value = 1250
        _userStreak.value = 15
        _userLevel.value = 8
    }

    fun completeStage(stageId: Int) {
        // Logic to complete a stage and update progress
        // This would typically involve API calls or database updates
    }

    fun updateUserStats(xp: Int, streak: Int, level: Int) {
        _userXP.value = xp
        _userStreak.value = streak
        _userLevel.value = level
    }

    // Callback will receive stages from Firebase; if none found, fallback will be used
    fun loadLearningStages(onResult: (List<Any>) -> Unit) {
        // TODO: Implement Firebase lookup for stages collection
        onResult(emptyList())
    }
}
