package com.example.embeddedsystemscareerguide.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Welcome to your Embedded Systems Journey!"
    }
    val text: LiveData<String> = _text

    private val _totalXP = MutableLiveData<Int>().apply {
        value = 1250
    }
    val totalXP: LiveData<Int> = _totalXP

    private val _currentStage = MutableLiveData<Int>().apply {
        value = 5
    }
    val currentStage: LiveData<Int> = _currentStage

    private val _streakDays = MutableLiveData<Int>().apply {
        value = 15
    }
    val streakDays: LiveData<Int> = _streakDays

    private val _completionPercentage = MutableLiveData<Int>().apply {
        value = 33
    }
    val completionPercentage: LiveData<Int> = _completionPercentage

    fun updateUserProgress(xp: Int, stage: Int, streak: Int, completion: Int) {
        _totalXP.value = xp
        _currentStage.value = stage
        _streakDays.value = streak
        _completionPercentage.value = completion
    }
}
