package com.example.embeddedsystemscareerguide.ui.assessment

import com.example.embeddedsystemscareerguide.services.ThemeManager

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // V2: Track if this is a retake (for regeneration with history)
    private var isRetake = false

    /**
     * Held so submission can disable it. The system back press must not be able to
     * finish() the Activity mid-report-generation.
     */
    private lateinit var backCallback: OnBackPressedCallback

    companion object {
        // Keys for surviving configuration changes and process death. The manifest
        // only declares configChanges="uiMode", so a rotation DOES recreate this
        // Activity. Without these, every one of the 50 free-text answers was lost
        // and the user was dropped back on question 1.
        private const val STATE_ANSWERS = "state_answers"
        private const val STATE_QUESTION_INDEX = "state_question_index"
        private const val STATE_IS_RETAKE = "state_is_retake"
    }

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
        ThemeManager.applyTo(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAssessmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // V2: Check if this is a retake assessment
        isRetake = intent.getBooleanExtra("is_retake", false)

        // Restore work in progress after a configuration change or process death.
        // Must happen BEFORE displayCurrentQuestion() so the saved answer is shown.
        if (savedInstanceState != null) {
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val restored = savedInstanceState.getSerializable(STATE_ANSWERS) as? HashMap<Int, String>
            if (restored != null) answers = restored.toMutableMap()
            currentQuestionIndex = savedInstanceState.getInt(STATE_QUESTION_INDEX, 0)
            isRetake = savedInstanceState.getBoolean(STATE_IS_RETAKE, isRetake)
            Log.d("Assessment", "Restored ${answers.size} answers at question $currentQuestionIndex")
        }

        Log.d("Assessment", "Assessment started, isRetake=$isRetake")

        // Handle system back to save answer first. Kept as a field so submission can
        // disable it: previously back remained active during report generation, so a
        // stray back press finished the Activity, cancelled the coroutine, and threw
        // the report away while the UI still showed progress.
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveCurrentAnswer()
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        loadQuestions()
        setupUI()

        // Clamp in case the asset changed between sessions.
        if (currentQuestionIndex >= questions.size) currentQuestionIndex = 0
        displayCurrentQuestion()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Capture what is currently typed, not just what was already committed by
        // Next/Back, so a rotation mid-answer does not drop the in-progress text.
        saveCurrentAnswer()
        outState.putSerializable(STATE_ANSWERS, HashMap(answers))
        outState.putInt(STATE_QUESTION_INDEX, currentQuestionIndex)
        outState.putBoolean(STATE_IS_RETAKE, isRetake)
        super.onSaveInstanceState(outState)
    }

    private fun loadQuestions() {
        try {
            val json = assets.open("initial_assessment_questions.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Question>>() {}.type
            // Gson bypasses Kotlin null-safety: an empty or malformed asset returns
            // null into a declared-non-null List, and the next questions.isEmpty()
            // would throw NPE. Default to an empty list instead.
            questions = Gson().fromJson<List<Question>?>(json, type) ?: emptyList()
            if (questions.isEmpty()) {
                Log.e("AssessmentActivity", "Question asset parsed to an empty list")
                Toast.makeText(this, "Error loading questions", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            // M2 fix: Log asset loading errors for debugging
            Log.e("AssessmentActivity", "Failed to load questions from assets", e)
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
            // EXTRA_LANGUAGE is read as an IETF BCP-47 tag string, not a Locale object -
            // passing the object made getStringExtra() return null downstream, silently
            // ignoring the requested language.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
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
        // Phase count is not fixed: the answer-key path runs one phase per
        // adjudication chunk plus two, so it varies with how many answers
        // actually need the model. The real counter arrives with the first
        // progress callback.
        binding.phaseCounter.text = "Preparing..."
        binding.phaseProgressBar.progress = 0
        binding.quoteText.text = GeminiReportService.QUOTES.random()

        // Disable buttons to prevent multiple clicks
        binding.buttonNext.isEnabled = false
        binding.buttonBack.isEnabled = false

        // Also disable the system back gesture. Report generation runs on a client
        // with a 600 s read timeout; letting back finish() the Activity here silently
        // cancelled the coroutine and discarded the report with no error shown.
        backCallback.isEnabled = false

        // Process assessment and generate report
        lifecycleScope.launch {
            try {
                // Prepare question-answer pairs
                val qaList = questions.mapIndexed { index, question ->
                    QuestionAnswer(
                        n = index + 1,
                        q = question.question,
                        u = answers[question.id] ?: "",
                        id = question.id
                    )
                }

                // Grade every answer against the precomputed key before anything
                // is generated. This costs no network call and is what finally
                // gives the learning path a real per-topic signal.
                //
                // Deliberately runs even while the key is unreviewed: topic
                // scores are not shown to the student, so no model-drafted text
                // reaches them. Serving cached answers and feedback is gated
                // separately, on AssessmentAnswerKey.reviewed. An empty key
                // yields an empty list here and everything downstream falls back
                // to the previous behaviour.
                // Explicitly off the main thread. lifecycleScope.launch uses
                // Dispatchers.Main.immediate, so this body runs synchronously
                // inside the click handler until something suspends - and this
                // step reads two assets, SHA-256s one of them, parses a 214 KB
                // JSON document and runs ~1150 substring matches. On Main that
                // is a frozen frame at the exact moment the loading overlay is
                // supposed to appear, plus a StrictMode disk-read violation.
                val (answerKey, gradedResults) = withContext(Dispatchers.IO) {
                    val key = com.example.embeddedsystemscareerguide.services
                        .AnswerKeyRepository.load(this@AssessmentActivity)
                    val keyed = key?.entries?.associateBy { it.id }.orEmpty()
                    key to questions.mapNotNull { q ->
                        keyed[q.id]?.let { entry ->
                            com.example.embeddedsystemscareerguide.services.AnswerGrader
                                .grade(entry, answers[q.id] ?: "")
                        }
                    }
                }
                val topicPercentages = com.example.embeddedsystemscareerguide.services
                    .AnswerGrader.topicPercentages(gradedResults)
                val topicRollup = com.example.embeddedsystemscareerguide.services
                    .AnswerGrader.topicRollup(gradedResults)

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

                // Two paths, chosen by whether the key is trustworthy.
                //
                // The fast path serves authored reference answers straight to
                // the student, so it requires a key that a human has reviewed.
                // Absent, stale or unreviewed, this falls through to the
                // original full-generation path unchanged - which is also what
                // makes this landable before the review is done.
                // Requires the key to cover EVERY question, not merely one.
                // gradedResults is a mapNotNull, so a key missing 38 of 50
                // entries would still satisfy "isNotEmpty" and then render
                // those 38 with no reference answer, no score, and no
                // degradation flag - a broken report served as the good path.
                val keyCoversAll = gradedResults.size == questions.size
                val report = if (answerKey != null && answerKey.reviewed && keyCoversAll) {
                    Log.d("Assessment", "Using answer-key report path " +
                        "(${gradedResults.count { it.verdict == com.example.embeddedsystemscareerguide.services.AnswerGrader.Verdict.NEEDS_MODEL }} " +
                        "of ${gradedResults.size} answers need the model)")
                    geminiService.generateReportFromKey(
                        userName = userName,
                        userEmail = userEmail,
                        questions = qaList,
                        graded = gradedResults,
                        key = answerKey,
                        topicPercentages = topicPercentages,
                        progressCallback = progressCallback
                    )
                } else {
                    Log.d("Assessment", "Using full generation path " +
                        "(key=${answerKey != null}, reviewed=${answerKey?.reviewed})")
                    geminiService.generateReport(userName, userEmail, qaList, progressCallback)
                }

                // Save report to Firebase and WAIT for completion
                binding.progressText.text = "Saving your report to cloud..."
                binding.progressSubstatus.text = "Almost done..."
                binding.phaseProgressBar.progress = 100
                val saveOutcome = saveReportToFirebaseSync(
                    report.html, userId, userName, userEmail, report.isDegraded
                )

                // One message, matched to what actually happened. Previously the
                // degradation notice fired before the save was even checked, so
                // a failed save produced "uses generic content" immediately
                // followed by "Error generating report".
                val message = when {
                    saveOutcome == SaveOutcome.KEPT_EXISTING ->
                        "Part of this report could not be generated, so your previous " +
                            "report was kept. Try again when the connection is better."
                    report.isDegraded ->
                        "Some of this report could not be generated and uses generic " +
                            "content. Retake the assessment when you're back online " +
                            "for a full personalised report."
                    else -> null
                }
                message?.let {
                    Toast.makeText(this@AssessmentActivity, it, Toast.LENGTH_LONG).show()
                }

                if (saveOutcome != SaveOutcome.FAILED) {
                    // CLOUD-ONLY: Report saved to Firebase is the source of truth
                    // Now generate personalized learning stages based on assessment
                    binding.progressText.text = "Generating your personalized learning path..."
                    binding.progressSubstatus.text = "Creating AI-powered stages..."
                    
                    // Generate personalized stages
                    val stageGenerator = com.example.embeddedsystemscareerguide.services.StageGeneratorService.getInstance(this@AssessmentActivity)
                    
                    // Calculate assessment result from answers.
                    //
                    // totalScore stays on the word-count heuristic on purpose: the
                    // <50 / <75 cutoffs in StageGeneratorService.categorizeTopics
                    // were calibrated against that distribution, so feeding it a
                    // differently-scaled number would silently reclassify people.
                    // What changes is topicScores. When it is non-empty the service
                    // takes its real branch (:227-235) instead of the fallback that
                    // picks one of three hardcoded topic lists - lists whose names
                    // are not even in the ES_TOPICS vocabulary. Until now that map
                    // was always empty, so a learning path was in practice chosen
                    // by how many words the student typed.
                    val score = calculateAssessmentScore()
                    // score/maxScore carry the EVIDENCE, not just the average.
                    //
                    // Writing maxScore = 100 for every topic loses how many
                    // questions backed it, and categorizeTopics recovers the
                    // count as maxScore/100 to shrink single-question topics
                    // toward the mean. With a constant 100 that count is always
                    // 1, the divisor is always 2, and the shrunk value becomes
                    // (percentage + 50) / 2 - a monotonic transform of the
                    // percentage, so the ordering is identical to not shrinking
                    // at all and the correction silently does nothing.
                    val topicScores = topicRollup
                        .mapValues { (_, rollup) ->
                            com.example.embeddedsystemscareerguide.services.StageGeneratorService.TopicScore(
                                score = rollup.totalScore,
                                maxScore = rollup.questionCount * 100,
                                percentage = rollup.percentage
                            )
                        }
                    if (topicScores.isNotEmpty()) {
                        Log.d("Assessment", "Graded ${gradedResults.size} answers into " +
                            "${topicScores.size} topic scores")
                    }
                    val assessmentResult = com.example.embeddedsystemscareerguide.services.StageGeneratorService.AssessmentResult(
                        totalScore = score,
                        maxScore = 100,
                        topicScores = topicScores
                    )
                    
                    val stageCallback = object : com.example.embeddedsystemscareerguide.services.StageGeneratorService.GenerationCallback {
                        override fun onProgress(phase: Int, message: String) {
                            runOnUiThread {
                                binding.progressSubstatus.text = message
                            }
                        }
                        
                        override fun onSuccess(stages: List<com.example.embeddedsystemscareerguide.services.PersonalizedStage>) {
                            runOnUiThread {
                                Log.d("Assessment", "Generated ${stages.size} personalized stages")
                                showCompletionState()
                            }
                        }
                        
                        override fun onError(error: String) {
                            runOnUiThread {
                                // Stage generation failed, but report was saved - still proceed
                                Log.e("Assessment", "Stage generation failed: $error")
                                Toast.makeText(this@AssessmentActivity, 
                                    "Learning path will be generated later", Toast.LENGTH_SHORT).show()
                                showCompletionState()
                            }
                        }
                    }
                    
                    // V2: Use regenerateStages for retakes (considers history),
                    // or generatePersonalizedStages for first-time assessment
                    if (isRetake) {
                        Log.d("Assessment", "Using regenerateStages with history consideration")
                        stageGenerator.regenerateStages(userName, assessmentResult, stageCallback)
                    } else {
                        Log.d("Assessment", "Using generatePersonalizedStages for first-time")
                        stageGenerator.generatePersonalizedStages(userName, assessmentResult, stageCallback)
                    }
                } else {
                    throw Exception("Failed to save report to cloud")
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                // The scope is already gone when this fires (the screen is
                // finishing), so touching the views below would be both
                // pointless and a leak. Let cancellation propagate.
                throw e
            } catch (e: Exception) {
                binding.loadingOverlay.isVisible = false
                // Restore the back gesture alongside the buttons. Without this, a
                // failed submission would leave the user unable to leave the screen.
                backCallback.isEnabled = true
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
        userEmail: String,
        isDegraded: Boolean
    ): SaveOutcome {
        return try {
            // Get username from SharedPreferences
            val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val username = userPrefs.getString("current_username", null)
            
            if (username == null) {
                android.util.Log.e("AssessmentActivity", "No username found, cannot save report")
                return SaveOutcome.FAILED
            }

            val reportRef = firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")

            // Never let a degraded report destroy a good one.
            //
            // set() replaces unconditionally, so a retake during class-wide load
            // - when timeouts are most likely - would overwrite a complete
            // report with one built partly from filler, irreversibly. The
            // student is better served keeping what they had.
            if (isDegraded) {
                val existing = runCatching { reportRef.get().await() }.getOrNull()
                val hadGoodReport = existing != null && existing.exists() &&
                    existing.getBoolean("isDegraded") != true &&
                    !existing.getString("reportHtml").isNullOrBlank()
                if (hadGoodReport) {
                    android.util.Log.w(
                        "AssessmentActivity",
                        "Refusing to overwrite an existing complete report with a degraded one"
                    )
                    return SaveOutcome.KEPT_EXISTING
                }
            }

            val reportData = mapOf(
                "userId" to userId,
                "userName" to userName,
                "userEmail" to userEmail,
                "reportHtml" to htmlContent,
                "timestamp" to System.currentTimeMillis(),
                "totalQuestions" to questions.size,
                // Persisted so a degraded report stays identifiable after the
                // fact. Previously the only signal was a Toast at submit time,
                // which is gone forever a few seconds later.
                "isDegraded" to isDegraded
            )

            // Save to new path: users/{username}/data/report
            reportRef.set(reportData).await()
            
            android.util.Log.d("AssessmentActivity", "Report saved for user: $username")
            SaveOutcome.SAVED
        } catch (e: Exception) {
            e.printStackTrace()
            SaveOutcome.FAILED
        }
    }

    /** Distinguishes a real save failure from a deliberate refusal to overwrite. */
    private enum class SaveOutcome { SAVED, FAILED, KEPT_EXISTING }

    /**
     * Calculate assessment score based on answer quality
     * Uses word count as a heuristic for effort/completeness
     * Returns percentage score (0-100)
     */
    private fun calculateAssessmentScore(): Int {
        if (questions.isEmpty()) return 50  // Default score
        
        var totalScore = 0
        questions.forEach { question ->
            val answer = answers[question.id] ?: ""
            val wordCount = answer.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            
            // Score based on answer length (simple heuristic)
            val questionScore = when {
                wordCount >= 50 -> 100  // Detailed answer
                wordCount >= 30 -> 80   // Good answer
                wordCount >= 15 -> 60   // Adequate answer
                wordCount >= 5 -> 40    // Brief answer
                wordCount > 0 -> 20     // Minimal answer
                else -> 0               // No answer
            }
            totalScore += questionScore
        }
        
        return totalScore / questions.size
    }

    // REMOVED: markAssessmentCompleted - report existence in Firebase is the source of truth
}

