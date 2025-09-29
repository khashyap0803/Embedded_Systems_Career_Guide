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
import java.io.File

class IntroductionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductionBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroductionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if report already exists before showing UI
        checkExistingReport()
    }

    private fun checkExistingReport() {
        val user = auth.currentUser
        if (user == null) {
            // User not logged in, show introduction
            setupUI()
            return
        }

        // Show loading state
        binding.buttonStartAssessment.isEnabled = false
        binding.buttonStartAssessment.text = "Checking..."

        // First, check local storage (faster)
        val reportFile = File(filesDir, "assessment_report.html")
        if (reportFile.exists()) {
            // Local report exists, go to home
            navigateToHome()
            return
        }

        // If no local report, check Firebase
        firestore.collection("assessment_reports")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Report exists in Firebase, update local flag and go to home
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("assessment_completed", true).commit()
                    navigateToHome()
                } else {
                    // No report exists anywhere, show introduction for assessment
                    setupUI()
                }
            }
            .addOnFailureListener {
                // On error, check local SharedPreferences as fallback
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val assessmentCompleted = prefs.getBoolean("assessment_completed", false)

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
