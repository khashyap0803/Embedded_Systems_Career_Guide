package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.databinding.ActivityRollNumberEntryBinding
import com.example.embeddedsystemscareerguide.models.challenge.ChallengeConstants
import com.example.embeddedsystemscareerguide.models.challenge.ParticipantStatus
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Roll Number Entry Activity
 * Collects and validates the participant's roll number before starting the challenge
 */
class RollNumberEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRollNumberEntryBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var eventService: PreReleaseEventService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRollNumberEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        eventService = PreReleaseEventService.getInstance()

        setupUI()
    }

    private fun setupUI() {
        binding.btnContinue.setOnClickListener {
            validateAndProceed()
        }

        binding.tvLogout.setOnClickListener {
            logout()
        }
    }

    private fun validateAndProceed() {
        val rollNumber = binding.etRollNumber.text.toString().trim()

        // Validate roll number format
        if (rollNumber.isEmpty()) {
            binding.tilRollNumber.error = "Roll number required"
            return
        }

        if (!eventService.isValidRollNumber(rollNumber)) {
            binding.tilRollNumber.error = "Invalid format. Must be 1601XXXXXXXX"
            return
        }

        binding.tilRollNumber.error = null
        showLoading(true)

        // Register participant and check status
        lifecycleScope.launch {
            val registered = eventService.registerParticipant(rollNumber)
            
            if (registered) {
                val status = eventService.getParticipantStatus(rollNumber)
                
                when {
                    // Terminated users cannot continue
                    status?.isTerminated == true -> {
                        showLoading(false)
                        showError("You have been terminated from this challenge and cannot continue.")
                    }
                    
                    // Resumable status - admin has resumed a terminated/timeout user
                    status?.currentStatus == ParticipantStatus.STATUS_RESUMABLE -> {
                        // Find the last incomplete challenge to resume
                        val ch1Complete = eventService.isChallengeCompleted(rollNumber, 1)
                        val ch2Complete = eventService.isChallengeCompleted(rollNumber, 2)
                        val ch3Complete = eventService.isChallengeCompleted(rollNumber, 3)
                        
                        when {
                            !ch1Complete -> navigateToChallenge(1, rollNumber, isResume = true)
                            status.canAccessChallenge2 && !ch2Complete -> navigateToChallenge(2, rollNumber, isResume = true)
                            status.canAccessChallenge3 && !ch3Complete -> navigateToChallenge(3, rollNumber, isResume = true)
                            else -> navigateToRankingDashboard(rollNumber)
                        }
                    }
                    
                    // Check if user can access Challenge 3
                    status?.canAccessChallenge3 == true -> {
                        val ch3Complete = eventService.isChallengeCompleted(rollNumber, 3)
                        if (ch3Complete) {
                            navigateToRankingDashboard(rollNumber)
                        } else {
                            navigateToChallenge(3, rollNumber)
                        }
                    }
                    
                    // Check if user can access Challenge 2
                    status?.canAccessChallenge2 == true -> {
                        val ch2Complete = eventService.isChallengeCompleted(rollNumber, 2)
                        if (ch2Complete) {
                            // Ch2 done - check if Ch3 is now unlocked
                            if (status.canAccessChallenge3) {
                                val ch3Complete = eventService.isChallengeCompleted(rollNumber, 3)
                                if (!ch3Complete) {
                                    navigateToChallenge(3, rollNumber)
                                    return@launch
                                }
                            }
                            navigateToRankingDashboard(rollNumber)
                        } else {
                            navigateToChallenge(2, rollNumber)
                        }
                    }
                    
                    // Check if Challenge 1 is completed (either by status or by having a score)
                    status?.currentStatus == "completed" || (status?.challenge1Score ?: 0) > 0 -> {
                        // Challenge 1 done, but Challenge 2 not unlocked yet - go to ranking dashboard
                        navigateToRankingDashboard(rollNumber)
                    }
                    
                    // In progress - check actual completion
                    status?.currentStatus == "in_progress" -> {
                        val ch1Complete = eventService.isChallengeCompleted(rollNumber, 1)
                        if (ch1Complete) {
                            navigateToRankingDashboard(rollNumber)
                        } else {
                            navigateToChallenge(1, rollNumber)
                        }
                    }
                    
                    // Default - start fresh with Challenge 1
                    else -> {
                        navigateToChallenge(1, rollNumber)
                    }
                }
            } else {
                showLoading(false)
                showError("Failed to register. Please try again.")
            }
        }
    }

    private fun navigateToChallenge(challengeNumber: Int, rollNumber: String, isResume: Boolean = false) {
        val intent = when (challengeNumber) {
            1 -> Intent(this, Challenge1Activity::class.java)
            2 -> Intent(this, Challenge2Activity::class.java)
            3 -> Intent(this, Challenge3Activity::class.java)
            else -> Intent(this, Challenge1Activity::class.java)
        }
        intent.putExtra(EXTRA_ROLL_NUMBER, rollNumber)
        intent.putExtra(EXTRA_IS_RESUME, isResume)
        startActivity(intent)
        finish()
    }

    private fun navigateToRankingDashboard(rollNumber: String) {
        val intent = Intent(this, RankingDashboardActivity::class.java)
        intent.putExtra(EXTRA_ROLL_NUMBER, rollNumber)
        startActivity(intent)
        finish()
    }

    private fun logout() {
        auth.signOut()
        // Navigate back to main LoginActivity
        val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.auth.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnContinue.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_ROLL_NUMBER = "extra_roll_number"
        const val EXTRA_IS_RESUME = "extra_is_resume"
    }
}
