package com.example.embeddedsystemscareerguide.ui.introduction

import com.example.embeddedsystemscareerguide.services.ThemeManager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityIntroductionBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class IntroductionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductionBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTo(this)
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
                    followReportRoute(LoginActivity.reportRoute(Result.success(document.exists())), user.uid)
                }
                .addOnFailureListener { e ->
                    Log.w("IntroductionActivity", "Report lookup failed; not treating this as a first-time student", e)
                    followReportRoute(LoginActivity.reportRoute(Result.failure(e)), user.uid)
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
                followReportRoute(
                    LoginActivity.reportRoute(Result.success(false), Result.success(document.exists())),
                    userId
                )
            }
            .addOnFailureListener { e ->
                Log.w("IntroductionActivity", "Legacy report lookup failed", e)
                followReportRoute(LoginActivity.reportRoute(Result.success(false), Result.failure(e)), userId)
            }
    }

    /**
     * Same decision as sign-in. A failed lookup used to leave a returning student
     * on this screen with "Start Assessment" as the only way forward.
     */
    private fun followReportRoute(route: LoginActivity.ReportRoute, userId: String) {
        when (route) {
            // Report exists (or could not be checked), here or at the legacy path
            LoginActivity.ReportRoute.HOME,
            LoginActivity.ReportRoute.MIGRATE_LEGACY -> navigateToHome()
            LoginActivity.ReportRoute.CHECK_LEGACY -> checkLegacyReport(userId)
            LoginActivity.ReportRoute.ASSESSMENT -> setupUI()
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
