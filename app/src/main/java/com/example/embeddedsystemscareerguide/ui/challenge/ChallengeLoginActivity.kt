package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.databinding.ActivityChallengeLoginBinding
import com.example.embeddedsystemscareerguide.models.challenge.ChallengeConstants
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Challenge Login Activity
 * Handles Firebase Authentication for the Pre-Release Event Challenge
 * Distinguishes between regular users and admins
 */
class ChallengeLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengeLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var eventService: PreReleaseEventService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengeLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        eventService = PreReleaseEventService.getInstance()

        setupUI()
        checkEventStatus()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun checkEventStatus() {
        lifecycleScope.launch {
            val isActive = eventService.isEventActive()
            binding.layoutEventStatus.visibility = View.VISIBLE
            
            if (isActive) {
                binding.statusIndicator.setBackgroundColor(getColor(android.R.color.holo_green_light))
                binding.tvEventStatus.text = "Event Active"
            } else {
                binding.statusIndicator.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                binding.tvEventStatus.text = "Event Not Started"
            }
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Validate inputs
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email required"
            return
        }
        binding.tilEmail.error = null

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password required"
            return
        }
        binding.tilPassword.error = null

        showLoading(true)

        // Try to sign in first
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                showLoading(false)
                val user = result.user
                
                if (user != null) {
                    // Check if admin
                    if (email == ChallengeConstants.ADMIN_EMAIL) {
                        navigateToAdmin()
                    } else {
                        navigateToRollNumberEntry()
                    }
                }
            }
            .addOnFailureListener { signInError ->
                // H-07: Do NOT auto-create users on failed sign-in
                // This was a security vulnerability allowing arbitrary account creation
                showLoading(false)
                showError("Login failed: ${signInError.localizedMessage}")
            }
    }

    private fun navigateToRollNumberEntry() {
        startActivity(Intent(this, RollNumberEntryActivity::class.java))
        finish()
    }

    private fun navigateToAdmin() {
        startActivity(Intent(this, AdminDashboardActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        // Check if already logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: ""
            if (email == ChallengeConstants.ADMIN_EMAIL) {
                navigateToAdmin()
            } else if (email == ChallengeConstants.USER_EMAIL) {
                navigateToRollNumberEntry()
            }
        }
    }
}
