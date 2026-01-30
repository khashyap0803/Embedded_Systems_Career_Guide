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

    /**
     * CLOUD-ONLY: Check Firebase for existing report
     */
    private fun checkExistingReportInFirebase() {
        val user = auth.currentUser
        if (user == null) {
            setupUI()
            return
        }

        binding.buttonStartAssessment.isEnabled = false
        binding.buttonStartAssessment.text = "Checking..."

        // Get username from SharedPreferences (login session only)
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val username = userPrefs.getString("current_username", null)

        if (username != null) {
            // Check path: users/{username}/data/report
            firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Report exists in cloud - navigate to home
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
            checkLegacyReport(user.uid)
        }
    }

    private fun checkLegacyReport(userId: String) {
        firestore.collection("assessment_reports")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Legacy report exists - navigate to home
                    navigateToHome()
                } else {
                    setupUI()
                }
            }
            .addOnFailureListener {
                // On error, show UI to start assessment
                setupUI()
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
