package com.example.embeddedsystemscareerguide.ui.quiz

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.AppConstants
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityQuizBinding
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.GeminiQuizService
import com.example.embeddedsystemscareerguide.services.QuizResult
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizBinding
    private lateinit var quizService: GeminiQuizService
    
    private var questions: List<GeminiQuizService.QuizQuestion> = emptyList()
    private var currentQuestionIndex = 0
    private var correctAnswers = 0
    private var hasAnswered = false
    
    /** Wall-clock start, so the saved result records real time spent. */
    private var quizStartedAtMs: Long = System.currentTimeMillis()

    private var stageId: Int = 1
    private var stageTitle: String = "Quiz"
    private var stageTopics: List<String> = emptyList()

    companion object {
        const val EXTRA_STAGE_ID = "stage_id"
        const val EXTRA_STAGE_TITLE = "stage_title"
        const val EXTRA_STAGE_TOPICS = "stage_topics"
        const val RESULT_QUIZ_SCORE = "quiz_score"
        const val RESULT_TOTAL_QUESTIONS = "total_questions"

        private const val STATE_QUESTIONS = "state_questions"
        private const val STATE_INDEX = "state_index"
        private const val STATE_CORRECT = "state_correct"
        private const val STATE_ANSWERED = "state_answered"
        private const val STATE_STARTED_AT = "state_started_at"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizService = GeminiQuizService()

        // Get stage info from intent
        stageId = intent.getIntExtra(EXTRA_STAGE_ID, 1)
        stageTitle = intent.getStringExtra(EXTRA_STAGE_TITLE) ?: "Stage Quiz"
        stageTopics = intent.getStringArrayListExtra(EXTRA_STAGE_TOPICS) 
            ?: ArrayList(quizService.getStageTopics(stageId))

        binding.textStageTitle.text = stageTitle

        // Handle back press with modern callback
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentQuestionIndex > 0 && !hasAnswered) {
                    // Confirm exit if quiz in progress
                    MaterialAlertDialogBuilder(this@QuizActivity, R.style.Theme_App_AlertDialog)
                        .setTitle("Leave Quiz?")
                        .setMessage("Your progress will be lost.")
                        .setPositiveButton("Leave") { _, _ -> finish() }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    finish()
                }
            }
        })

        setupClickListeners()

        // Restore an in-progress quiz across a configuration change. Only
        // `uiMode` is declared in the manifest, so a rotation, font-size or
        // locale change, split-screen, or process death recreates this
        // Activity. Without this the questions, position and score were all
        // lost and onCreate fired generateQuiz() again - burning two more LLM
        // calls and restarting the learner at question 1 on a *different* set
        // of questions, with the score they had already earned discarded.
        val restored = savedInstanceState?.let { restoreQuizState(it) } ?: false
        if (!restored) {
            generateQuiz()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (questions.isNotEmpty()) {
            outState.putString(STATE_QUESTIONS, quizService.toJson(questions))
            outState.putInt(STATE_INDEX, currentQuestionIndex)
            outState.putInt(STATE_CORRECT, correctAnswers)
            outState.putBoolean(STATE_ANSWERED, hasAnswered)
            outState.putLong(STATE_STARTED_AT, quizStartedAtMs)
        }
        super.onSaveInstanceState(outState)
    }

    /** @return true when a quiz was restored and regeneration must be skipped. */
    private fun restoreQuizState(state: Bundle): Boolean {
        val json = state.getString(STATE_QUESTIONS) ?: return false
        val saved = quizService.fromJson(json)
        if (saved.isEmpty()) return false

        questions = saved
        currentQuestionIndex = state.getInt(STATE_INDEX, 0).coerceIn(0, saved.lastIndex)
        correctAnswers = state.getInt(STATE_CORRECT, 0)
        hasAnswered = state.getBoolean(STATE_ANSWERED, false)
        quizStartedAtMs = state.getLong(STATE_STARTED_AT, System.currentTimeMillis())

        showLoading(false)
        displayQuestion()
        return true
    }

    private fun setupClickListeners() {
        val optionCards = listOf(
            binding.optionA to 0,
            binding.optionB to 1,
            binding.optionC to 2,
            binding.optionD to 3
        )

        optionCards.forEach { (card, index) ->
            card.setOnClickListener {
                if (!hasAnswered && questions.isNotEmpty()) {
                    selectAnswer(index)
                }
            }
        }

        binding.btnNext.setOnClickListener {
            nextQuestion()
        }
    }

    private fun generateQuiz() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // M1 fix: Use AppConstants for quiz question count
                questions = quizService.generateQuiz(
                    stageTitle = stageTitle,
                    stageTopics = stageTopics,
                    numberOfQuestions = AppConstants.QUESTIONS_PER_QUIZ,
                    difficulty = "medium"
                )
                
                showLoading(false)
                
                if (questions.isNotEmpty()) {
                    displayQuestion()
                } else {
                    Toast.makeText(this@QuizActivity, "Failed to load quiz", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@QuizActivity, "Error loading quiz: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayQuestion() {
        if (currentQuestionIndex >= questions.size) {
            showResults()
            return
        }

        hasAnswered = false
        val question = questions[currentQuestionIndex]

        // Update progress
        binding.textQuestionProgress.text = "Question ${currentQuestionIndex + 1} of ${questions.size}"
        binding.textScore.text = "Score: $correctAnswers/${currentQuestionIndex}"
        binding.progressQuiz.progress = ((currentQuestionIndex + 1) * 100) / questions.size

        // Display question
        binding.textQuestion.text = question.question

        // Display options
        val optionTexts = listOf(
            binding.textOptionA,
            binding.textOptionB,
            binding.textOptionC,
            binding.textOptionD
        )
        val optionCards = listOf(
            binding.optionA,
            binding.optionB,
            binding.optionC,
            binding.optionD
        )

        question.options.forEachIndexed { index, option ->
            if (index < optionTexts.size) {
                optionTexts[index].text = "${('A' + index)}. $option"
                // Reset card appearance
                resetCardStyle(optionCards[index])
            }
        }

        // Hide explanation and next button
        binding.cardExplanation.visibility = View.GONE
        binding.btnNext.visibility = View.GONE
    }

    private fun selectAnswer(selectedIndex: Int) {
        hasAnswered = true
        val question = questions[currentQuestionIndex]
        val isCorrect = selectedIndex == question.correctAnswerIndex

        if (isCorrect) {
            correctAnswers++
        }

        // Update score display
        binding.textScore.text = "Score: $correctAnswers/${currentQuestionIndex + 1}"

        // Show feedback on cards
        val optionCards = listOf(
            binding.optionA,
            binding.optionB,
            binding.optionC,
            binding.optionD
        )

        optionCards.forEachIndexed { index, card ->
            when {
                index == question.correctAnswerIndex -> {
                    // Highlight correct answer
                    card.strokeColor = ContextCompat.getColor(this, R.color.emerald_400)
                    card.strokeWidth = 3
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.emerald_900))
                }
                index == selectedIndex && !isCorrect -> {
                    // Highlight wrong selection
                    card.strokeColor = ContextCompat.getColor(this, R.color.red_400)
                    card.strokeWidth = 3
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.red_900))
                }
            }
        }

        // Show explanation
        binding.cardExplanation.visibility = View.VISIBLE
        binding.textResultIcon.text = if (isCorrect) "✅" else "❌"
        binding.textExplanation.text = question.explanation

        // Show next button
        binding.btnNext.visibility = View.VISIBLE
        binding.btnNext.text = if (currentQuestionIndex == questions.size - 1) "View Results" else "Next Question"
    }

    private fun resetCardStyle(card: MaterialCardView) {
        card.strokeColor = ContextCompat.getColor(this, R.color.slate_600)
        card.strokeWidth = 1
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.glass_secondary))
    }

    private fun nextQuestion() {
        currentQuestionIndex++
        displayQuestion()
    }

    private fun showResults() {
        // L14 fix: Guard against division by zero
        val percentage = if (questions.isNotEmpty()) {
            (correctAnswers * 100) / questions.size
        } else {
            0
        }
        // L5 fix: Use AppConstants for star thresholds
        val stars = when {
            percentage >= AppConstants.STAR_3_THRESHOLD -> 3
            percentage >= AppConstants.STAR_2_THRESHOLD -> 2
            percentage >= AppConstants.STAR_1_THRESHOLD -> 1
            else -> 0
        }

        // Record the attempt. saveQuizResult had no callers at all, so
        // getQuizHistory always came back empty and every downstream use of it
        // was dead: collectUserPerformanceData could not derive weak topics,
        // and the retake dialog's promise that "topics you struggled with will
        // get more focus" was therefore never true.
        lifecycleScope.launch {
            runCatching {
                FirestoreManager.getInstance(this@QuizActivity).saveQuizResult(
                    stageId,
                    QuizResult(
                        stageId = stageId,
                        score = percentage,
                        totalQuestions = questions.size,
                        correctAnswers = correctAnswers,
                        starsEarned = stars,
                        timeSpentSeconds = ((System.currentTimeMillis() - quizStartedAtMs) / 1000).toInt()
                    )
                )
            }.onFailure {
                // Never block the results screen on history bookkeeping.
                Log.w("QuizActivity", "Could not save quiz result", it)
            }
        }

        // Create star display
        val starsFilled = "⭐".repeat(stars)
        val starsEmpty = "☆".repeat(3 - stars)
        val starsDisplay = starsFilled + starsEmpty

        // Create message based on performance
        val emoji = when {
            percentage >= 80 -> "🎉"
            percentage >= 60 -> "👍"
            percentage >= 40 -> "📚"
            else -> "💪"
        }
        val encouragement = when {
            percentage >= 80 -> "Excellent! You've mastered this topic!"
            percentage >= 60 -> "Good job! You're getting there!"
            percentage >= 40 -> "Keep practicing, you're learning!"
            else -> "Don't give up! Review and try again."
        }

        val title = if (stars > 0) "Quiz Complete! $starsDisplay" else "Quiz Complete!"
        val message = """Score: $correctAnswers/${questions.size} ($percentage%)
            |
            |$emoji $encouragement
            |
            |${if (stars < 3) "Tip: Score 80%+ for 3 stars!" else "Perfect score! 🌟"}""".trimMargin()

        MaterialAlertDialogBuilder(this, R.style.Theme_App_AlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Done") { _, _ ->
                returnResult()
            }
            .setNeutralButton("🔄 Try Again") { _, _ ->
                retryQuiz()
            }
            .setCancelable(false)
            .show()
    }

    private fun returnResult() {
        val resultIntent = intent
        resultIntent.putExtra(RESULT_QUIZ_SCORE, correctAnswers)
        resultIntent.putExtra(RESULT_TOTAL_QUESTIONS, questions.size)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun retryQuiz() {
        // Reset quiz state
        currentQuestionIndex = 0
        correctAnswers = 0
        hasAnswered = false
        
        // Regenerate quiz with new AI questions
        generateQuiz()
    }

    private fun showLoading(show: Boolean) {
        binding.layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
        binding.cardHeader.visibility = if (show) View.GONE else View.VISIBLE
        binding.cardQuestion.visibility = if (show) View.GONE else View.VISIBLE
        binding.layoutOptions.visibility = if (show) View.GONE else View.VISIBLE
    }
}
