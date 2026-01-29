package com.example.embeddedsystemscareerguide.ui.assessment

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityAssessmentBinding
import com.example.embeddedsystemscareerguide.models.AssessmentReport
import com.example.embeddedsystemscareerguide.models.Question
import com.example.embeddedsystemscareerguide.models.QuestionAnswer
import com.example.embeddedsystemscareerguide.services.GeminiReportService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class AssessmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssessmentBinding
    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var answers = mutableMapOf<Int, String>()
    private val geminiService = GeminiReportService()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            spokenText?.firstOrNull()?.let { text ->
                val currentText = binding.editTextAnswer.text.toString()
                val newText = if (currentText.isBlank()) text else "$currentText $text"
                binding.editTextAnswer.setText(newText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssessmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle system back to save answer first
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveCurrentAnswer()
                finish()
            }
        })

        loadQuestions()
        setupUI()
        displayCurrentQuestion()
    }

    private fun loadQuestions() {
        try {
            val json = assets.open("initial_assessment_questions.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Question>>() {}.type
            questions = Gson().fromJson(json, type)
        } catch (_: Exception) {
            Toast.makeText(this, "Error loading questions", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener {
            if (currentQuestionIndex > 0) {
                saveCurrentAnswer()
                currentQuestionIndex--
                displayCurrentQuestion()
            }
        }

        binding.buttonNext.setOnClickListener {
            saveCurrentAnswer()
            if (currentQuestionIndex < questions.size - 1) {
                currentQuestionIndex++
                displayCurrentQuestion()
            } else {
                // All questions completed
                submitAssessment()
            }
        }

        binding.buttonMic.setOnClickListener {
            startVoiceInput()
        }
    }

    private fun displayCurrentQuestion() {
        if (questions.isEmpty()) return

        val question = questions[currentQuestionIndex]

        // Update progress
        val progress = ((currentQuestionIndex + 1) * 100) / questions.size
        binding.progressIndicator.progress = progress

        // Update counter
        binding.textQuestionCounter.text = getString(R.string.question_progress, currentQuestionIndex + 1, questions.size)

        // Update question text
        binding.textQuestion.text = question.question

        // Load saved answer if exists
        binding.editTextAnswer.setText(answers[question.id] ?: "")

        // Update back button visibility
        binding.buttonBack.isVisible = currentQuestionIndex > 0

        // Update next button text
        binding.buttonNext.text = if (currentQuestionIndex == questions.size - 1) {
            getString(R.string.submit_button)
        } else {
            getString(R.string.next_button)
        }
    }

    private fun saveCurrentAnswer() {
        if (questions.isNotEmpty()) {
            val questionId = questions[currentQuestionIndex].id
            val answer = binding.editTextAnswer.text.toString().trim()
            answers[questionId] = answer
        }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_to_text))
        }

        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Speech recognition failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitAssessment() {
        saveCurrentAnswer()

        // Prevent multiple simultaneous submissions
        if (binding.loadingOverlay.isVisible) {
            return
        }

        // Show full-screen loading overlay (hides question)
        binding.loadingOverlay.isVisible = true
        binding.loadingState.visibility = android.view.View.VISIBLE
        binding.completionState.visibility = android.view.View.GONE
        binding.progressText.text = "Initializing AI analysis..."
        binding.progressSubstatus.text = "Preparing your answers..."
        binding.phaseCounter.text = "Phase 0 of 6"
        binding.phaseProgressBar.progress = 0
        binding.quoteText.text = GeminiReportService.QUOTES.random()

        // Disable buttons to prevent multiple clicks
        binding.buttonNext.isEnabled = false
        binding.buttonBack.isEnabled = false

        // Process assessment and generate report
        lifecycleScope.launch {
            try {
                // Prepare question-answer pairs
                val qaList = questions.mapIndexed { index, question ->
                    QuestionAnswer(
                        n = index + 1,
                        q = question.question,
                        u = answers[question.id] ?: ""
                    )
                }

                // Get user info
                val user = auth.currentUser
                val userEmail = user?.email ?: ""
                val userId = user?.uid ?: ""
                
                // Get username from SharedPreferences (unique identifier for report)
                val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
                val username = userPrefs.getString("current_username", null)
                // Use username for display in report, fallback to display name
                val userName = username ?: (user?.displayName ?: "Student")

                // Create progress callback
                val progressCallback = object : GeminiReportService.ProgressCallback {
                    override fun onProgress(phase: Int, totalPhases: Int, phaseName: String, quote: String) {
                        // Update UI on main thread
                        binding.phaseCounter.text = "Phase $phase of $totalPhases"
                        binding.phaseProgressBar.progress = ((phase.toFloat() / totalPhases) * 100).toInt()
                        binding.progressText.text = phaseName
                        binding.quoteText.text = quote
                        
                        // Update substatus based on phase
                        binding.progressSubstatus.text = when {
                            phase <= totalPhases - 2 -> "Processing question feedback..."
                            phase == totalPhases - 1 -> "Building your 12-week roadmap..."
                            else -> "Almost done!"
                        }
                    }
                }

                // Generate report using Gemini API with progress callback
                val reportHtml = geminiService.generateReport(userName, userEmail, qaList, progressCallback)

                // Save report to Firebase and WAIT for completion
                binding.progressText.text = "Saving your report to cloud..."
                binding.progressSubstatus.text = "Almost done..."
                binding.phaseProgressBar.progress = 100
                val saveSuccess = saveReportToFirebaseSync(reportHtml, userId, userName, userEmail)

                if (saveSuccess) {
                    // Mark assessment as completed ONLY after Firebase save succeeds
                    markAssessmentCompleted(userId)

                    // Show completion state with Preview/Continue options
                    showCompletionState()
                } else {
                    throw Exception("Failed to save report to cloud")
                }

            } catch (e: Exception) {
                binding.loadingOverlay.isVisible = false
                binding.buttonNext.isEnabled = true
                binding.buttonBack.isEnabled = true
                Toast.makeText(
                    this@AssessmentActivity,
                    "Error generating report: ${e.message}. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }

    /**
     * Show completion state with Preview and Continue buttons
     */
    private fun showCompletionState() {
        // Switch from loading to completion state
        binding.loadingState.visibility = android.view.View.GONE
        binding.completionState.visibility = android.view.View.VISIBLE

        // Setup Preview Report button
        binding.btnPreviewReport.setOnClickListener {
            val intent = Intent(this, ReportViewerActivity::class.java)
            startActivity(intent)
        }

        // Setup Continue to Home button
        binding.btnContinueHome.setOnClickListener {
            navigateToHome()
        }
    }

    /**
     * Navigate to home screen
     */
    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Save report to Firebase SYNCHRONOUSLY using Tasks.await()
     * Saves to: users/{username}/data/report (replaces any existing report)
     */
    private suspend fun saveReportToFirebaseSync(
        htmlContent: String,
        userId: String,
        userName: String,
        userEmail: String
    ): Boolean {
        return try {
            // Get username from SharedPreferences
            val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val username = userPrefs.getString("current_username", null)
            
            if (username == null) {
                android.util.Log.e("AssessmentActivity", "No username found, cannot save report")
                return false
            }

            val reportData = mapOf(
                "userId" to userId,
                "userName" to userName,
                "userEmail" to userEmail,
                "reportHtml" to htmlContent,
                "timestamp" to System.currentTimeMillis(),
                "totalQuestions" to questions.size
            )

            // Save to new path: users/{username}/data/report
            // Using set() will replace any existing report
            firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")
                .set(reportData)
                .await()
            
            android.util.Log.d("AssessmentActivity", "Report saved for user: $username")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun markAssessmentCompleted(userId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        // H8 fix: Use apply() instead of commit() to avoid blocking main thread
        // The flag will be set asynchronously and persisted before process exit
        prefs.edit().putBoolean("assessment_completed_$userId", true).apply()
    }
}
