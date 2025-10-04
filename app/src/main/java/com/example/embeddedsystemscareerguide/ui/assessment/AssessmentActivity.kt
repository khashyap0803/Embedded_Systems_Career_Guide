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

        // Show loading overlay
        binding.loadingOverlay.isVisible = true
        binding.progressText.text = "Generating your personalized report...\nThis may take a few moments."

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
                val userName = user?.displayName ?: "Student"
                val userEmail = user?.email ?: ""
                val userId = user?.uid ?: ""

                // Generate report using Gemini API
                binding.progressText.text = "Analyzing your answers with AI...\nPlease wait..."
                val reportHtml = geminiService.generateReport(userName, userEmail, qaList)

                // Save report to Firebase ONLY (no local storage)
                binding.progressText.text = "Saving your report..."
                saveReportToFirebase(reportHtml, userId, userName, userEmail)

                // Mark assessment as completed SYNCHRONOUSLY with user-specific key
                markAssessmentCompleted(userId)

                // Hide loading and show success
                binding.loadingOverlay.isVisible = false
                Toast.makeText(this@AssessmentActivity, "Report generated successfully!", Toast.LENGTH_LONG).show()

                // Navigate to home with delay to ensure flag is saved
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@AssessmentActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }, 300)

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

    private suspend fun saveReportToFirebase(
        htmlContent: String,
        userId: String,
        userName: String,
        userEmail: String
    ) {
        try {
            val report = AssessmentReport(
                userId = userId,
                userName = userName,
                userEmail = userEmail,
                reportHtml = htmlContent,
                timestamp = System.currentTimeMillis(),
                totalQuestions = questions.size
            )

            firestore.collection("assessment_reports")
                .document(userId)
                .set(report)
                .addOnSuccessListener {
                    // Report saved successfully
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun markAssessmentCompleted(userId: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        // Use user-specific key to prevent cross-user contamination
        prefs.edit().putBoolean("assessment_completed_$userId", true).commit() // Use commit() for synchronous save
    }
}
