package com.example.embeddedsystemscareerguide.ui.introduction

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityIntroductionBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class IntroductionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductionBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroductionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if report already exists in Firebase (ONLY source of truth)
        checkExistingReportInFirebase()
    }

    private fun checkExistingReportInFirebase() {
        val user = auth.currentUser
        if (user == null) {
            // User not logged in, show introduction
            setupUI()
            return
        }

        // Show loading state
        binding.buttonStartAssessment.isEnabled = false
        binding.buttonStartAssessment.text = "Checking..."

        // Get username from SharedPreferences
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val username = userPrefs.getString("current_username", null)

        if (username != null) {
            // Check new path: users/{username}/data/report
            firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Report exists - update SharedPreferences flag
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        prefs.edit().putBoolean("assessment_completed_${user.uid}", true).apply()
                        navigateToHome()
                    } else {
                        // Check legacy path
                        checkLegacyReport(user.uid)
                    }
                }
                .addOnFailureListener {
                    checkLegacyReport(user.uid)
                }
        } else {
            // No username, check legacy path
            checkLegacyReport(user.uid)
        }
    }

    private fun checkLegacyReport(userId: String) {
        firestore.collection("assessment_reports")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("assessment_completed_$userId", true).apply()
                    navigateToHome()
                } else {
                    setupUI()
                }
            }
            .addOnFailureListener {
                // On error, check SharedPreferences as fallback
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val assessmentCompleted = prefs.getBoolean("assessment_completed_$userId", false)
                if (assessmentCompleted) {
                    navigateToHome()
                } else {
                    setupUI()
                }
            }
    }

    private fun setupUI() {
        binding.buttonStartAssessment.isEnabled = true
        binding.buttonStartAssessment.text = "Start Assessment"
        binding.buttonStartAssessment.setOnClickListener {
            startActivity(Intent(this, AssessmentActivity::class.java))
            finish()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
