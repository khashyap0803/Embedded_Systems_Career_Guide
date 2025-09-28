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
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityAssessmentBinding
import com.example.embeddedsystemscareerguide.models.Question
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class AssessmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssessmentBinding
    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var answers = mutableMapOf<Int, String>()

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

        // TODO: Process answers and generate report using AI
        Toast.makeText(this, "Assessment completed! ${answers.size} answers submitted.", Toast.LENGTH_LONG).show()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
