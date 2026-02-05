package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityChallenge3Binding
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.example.embeddedsystemscareerguide.services.Challenge3QuestionInternal
import com.example.embeddedsystemscareerguide.services.GeminiChallengeService
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Challenge 3 Activity - Hard Difficulty - Write Complete Code
 * Supports 3 QUESTIONS with navigation as per spec
 * User writes COMPLETE code from scratch
 * Timer: 30 minutes total for all 3 questions
 * Supports resume functionality with saved questions
 */
class Challenge3Activity : AppCompatActivity() {

    private lateinit var binding: ActivityChallenge3Binding
    private lateinit var eventService: PreReleaseEventService
    private lateinit var geminiService: GeminiChallengeService
    
    private var rollNumber: String = ""
    private var isResume: Boolean = false
    private var timer: CountDownTimer? = null
    private var timeRemainingMs: Long = ChallengeConstants.CHALLENGE_3_TIME_MS
    private var warningCount: Int = 0
    private var lastKnownExtraTime: Long = 0L
    private var extraTimeListener: com.google.firebase.database.ValueEventListener? = null
    
    // ========== MULTI-QUESTION SUPPORT ==========
    private var currentQuestionIndex: Int = 0
    private val questions = mutableListOf<Challenge3QuestionInternal>()
    private val questionAnswers = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallenge3Binding.inflate(layoutInflater)
        setContentView(binding.root)
        
        eventService = PreReleaseEventService.getInstance()
        geminiService = GeminiChallengeService.getInstance(this)
        rollNumber = intent.getStringExtra(RollNumberEntryActivity.EXTRA_ROLL_NUMBER) ?: ""
        isResume = intent.getBooleanExtra(RollNumberEntryActivity.EXTRA_IS_RESUME, false)
        
        if (rollNumber.isEmpty()) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        android.util.Log.i("Challenge3", "Starting Challenge 3 for $rollNumber, isResume=$isResume")
        
        setupUI()
        loadQuestions()
    }
    
    private fun setupUI() {
        binding.tvRollNumber.text = "Roll: $rollNumber"
        
        // Disable copy-paste on the code editor to prevent cheating
        binding.etCompleteCode.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        binding.etCompleteCode.customInsertionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        
        // Navigation buttons
        binding.btnPrevQuestion.setOnClickListener { navigateToQuestion(currentQuestionIndex - 1) }
        binding.btnNextQuestion.setOnClickListener { navigateToQuestion(currentQuestionIndex + 1) }
        binding.btnSubmit.setOnClickListener { confirmFinalSubmit() }
        binding.btnSaveProgress.setOnClickListener { 
            saveCurrentAnswer()
            Toast.makeText(this, "Progress saved!", Toast.LENGTH_SHORT).show()
        }
        
        // Progress dots
        binding.dot1.setOnClickListener { navigateToQuestion(0) }
        binding.dot2.setOnClickListener { navigateToQuestion(1) }
        binding.dot3.setOnClickListener { navigateToQuestion(2) }
        
        // Line counter for code editor
        binding.etCompleteCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineCount()
            }
        })
    }
    
    private fun updateLineCount() {
        val lines = binding.etCompleteCode.text.toString().lines().size
        binding.tvLineCount.text = "$lines lines"
    }
    
    private fun loadQuestions() {
        lifecycleScope.launch {
            if (isResume) {
                // RESUME MODE: Load saved questions AND state from Firebase
                showLoading(true, "Restoring your saved progress...")
                android.util.Log.i("Challenge3", "Resume mode: Loading saved questions and state for $rollNumber")
                
                val savedJson = eventService.loadSavedQuestions(rollNumber, 3)
                if (savedJson != null && savedJson.isNotEmpty()) {
                    try {
                        val loaded = parseQuestionsFromJson(savedJson)
                        if (loaded.isNotEmpty()) {
                            questions.addAll(loaded)
                            questions.forEach { _ -> questionAnswers.add("") }
                            
                            // Load saved state (answers, timer, question index)
                            val savedState = eventService.loadChallengeState(rollNumber, 3)
                            var resumeTimeMs = ChallengeConstants.CHALLENGE_3_TIME_MS
                            var resumeQuestionIndex = 0
                            
                            if (savedState != null) {
                                val (stateJson, savedTimeMs, savedQuestionIdx) = savedState
                                resumeTimeMs = if (savedTimeMs > 0) savedTimeMs else ChallengeConstants.CHALLENGE_3_TIME_MS
                                resumeQuestionIndex = savedQuestionIdx
                                
                                // Parse and restore all answers
                                if (stateJson != null) {
                                    parseAndRestoreState(stateJson, savedQuestionIdx)
                                }
                                
                                android.util.Log.i("Challenge3", "Restored state: timeRemaining=${resumeTimeMs}ms, questionIndex=$resumeQuestionIndex")
                            }
                            
                            // Set timer to saved value
                            timeRemainingMs = resumeTimeMs
                            currentQuestionIndex = resumeQuestionIndex
                            
                            showLoading(false)
                            displayQuestion(currentQuestionIndex)
                            
                            // Restore UI - show saved answer in text field
                            if (currentQuestionIndex < questionAnswers.size) {
                                binding.etCompleteCode.setText(questionAnswers[currentQuestionIndex])
                                updateLineCount()
                            }
                            
                            startTimer()
                            android.util.Log.i("Challenge3", "Successfully resumed with ${questions.size} questions at index $currentQuestionIndex")
                            return@launch
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Challenge3", "Failed to parse saved questions/state", e)
                    }
                }
                
                // If loading failed, fall back to generating new questions
                android.util.Log.w("Challenge3", "No saved questions found, generating new ones")
                isResume = false
            }
            
            // NEW CHALLENGE MODE: Generate new questions via Gemini
            showLoading(true, "Generating 3 complete code problems via Gemini AI...")
            
            eventService.startChallenge3(rollNumber)
            
            // Generate questions via Gemini API (cloud-only, no local fallback)
            val geminiResult = geminiService.generateChallenge3Questions()
            
            if (geminiResult.isSuccess && geminiResult.getOrNull()?.size == 3) {
                geminiResult.getOrNull()?.forEach { q ->
                    questions.add(q)
                }
                
                // Initialize answers for each question
                questions.forEach { _ -> questionAnswers.add("") }
                
                // SAVE QUESTIONS TO FIREBASE for future resume
                val questionsJson = serializeQuestionsToJson(questions)
                eventService.saveGeneratedQuestions(rollNumber, 3, questionsJson)
                android.util.Log.i("Challenge3", "Saved ${questions.size} questions to Firebase for resume")
                
                showLoading(false)
                displayQuestion(0)
                startTimer()
            } else {
                // Show error dialog and allow retry
                showLoading(false)
                showGeminiErrorDialog(geminiResult.exceptionOrNull()?.message ?: "Failed to generate questions")
            }
        }
    }
    
    /**
     * Serialize questions to JSON for Firebase storage
     */
    private fun serializeQuestionsToJson(questionsList: List<Challenge3QuestionInternal>): String {
        val jsonArray = JSONArray()
        questionsList.forEach { q ->
            jsonArray.put(JSONObject().apply {
                put("id", q.id)
                put("scenario", q.scenario)
                put("description", q.description)
                put("requirements", JSONArray(q.requirements))
                put("expectedElements", JSONArray(q.expectedElements))
                put("hints", JSONArray(q.hints))
            })
        }
        return jsonArray.toString()
    }
    
    /**
     * Parse questions from saved JSON
     */
    private fun parseQuestionsFromJson(json: String): List<Challenge3QuestionInternal> {
        val result = mutableListOf<Challenge3QuestionInternal>()
        val jsonArray = JSONArray(json)
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            
            val requirementsArray = obj.optJSONArray("requirements") ?: JSONArray()
            val requirements = mutableListOf<String>()
            for (j in 0 until requirementsArray.length()) {
                requirements.add(requirementsArray.getString(j))
            }
            
            val expectedElementsArray = obj.optJSONArray("expectedElements") ?: JSONArray()
            val expectedElements = mutableListOf<String>()
            for (j in 0 until expectedElementsArray.length()) {
                expectedElements.add(expectedElementsArray.getString(j))
            }
            
            val hintsArray = obj.optJSONArray("hints") ?: JSONArray()
            val hints = mutableListOf<String>()
            for (j in 0 until hintsArray.length()) {
                hints.add(hintsArray.getString(j))
            }
            
            result.add(Challenge3QuestionInternal(
                id = obj.getInt("id"),
                scenario = obj.optString("scenario", ""),
                description = obj.optString("description", ""),
                requirements = requirements,
                expectedElements = expectedElements,
                hints = hints
            ))
        }
        
        return result
    }
    
    private fun showGeminiErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Connection Error")
            .setMessage("Failed to generate questions from Gemini AI.\n\nError: $errorMessage\n\nPlease check your internet connection and try again.")
            .setPositiveButton("🔄 Retry") { _, _ ->
                questions.clear()
                questionAnswers.clear()
                loadQuestions()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }


    
    private fun displayQuestion(index: Int) {
        if (index < 0 || index >= questions.size) return
        
        // Save current answer before switching
        saveCurrentAnswer()
        
        currentQuestionIndex = index
        val question = questions[index]
        
        // Update UI
        binding.tvQuestionNumber.text = "Question ${index + 1} of ${questions.size}"
        binding.tvScenario.text = question.scenario
        binding.tvDescription.text = question.description
        binding.tvRequirements.text = question.requirements.mapIndexed { i, req -> 
            "✓ ${req}" 
        }.joinToString("\n")
        binding.tvHints.text = "💡 Hints: ${question.hints.joinToString(", ")}"
        
        // Restore saved answer
        binding.etCompleteCode.setText(questionAnswers.getOrElse(index) { "" })
        updateLineCount()
        
        // Update navigation buttons
        binding.btnPrevQuestion.isEnabled = index > 0
        binding.btnNextQuestion.visibility = if (index < questions.size - 1) View.VISIBLE else View.GONE
        binding.btnSubmit.visibility = if (index == questions.size - 1) View.VISIBLE else View.GONE
        
        updateProgressDots()
        updateProgress()
    }
    
    private fun updateProgressDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { i, dot ->
            when {
                i == currentQuestionIndex -> dot.setBackgroundResource(R.drawable.dot_active)
                questionAnswers.getOrNull(i)?.length ?: 0 > 50 -> dot.setBackgroundResource(R.drawable.dot_completed)
                else -> dot.setBackgroundResource(R.drawable.dot_inactive)
            }
        }
    }
    
    private fun saveCurrentAnswer() {
        if (currentQuestionIndex < questionAnswers.size) {
            questionAnswers[currentQuestionIndex] = binding.etCompleteCode.text.toString()
        }
    }
    
    private fun navigateToQuestion(index: Int) {
        if (index in 0 until questions.size) {
            displayQuestion(index)
        }
    }
    
    private fun startTimer() {
        // Listen for extra time added by admin
        listenForExtraTime()
        
        timer = object : CountDownTimer(timeRemainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                updateTimerDisplay()
                
                when {
                    millisUntilFinished < 60000 -> {
                        binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
                    }
                    millisUntilFinished < 300000 -> {
                        binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_orange_light))
                    }
                }
            }
            
            override fun onFinish() {
                timeoutAndSubmit()
            }
        }.start()
    }
    
    private fun listenForExtraTime() {
        val ref = eventService.getParticipantRef(rollNumber).child("challenge3/extraTimeGrantedMs")
        extraTimeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val newExtraTime = snapshot.getValue(Long::class.java) ?: 0L
                if (newExtraTime > lastKnownExtraTime) {
                    val additionalTime = newExtraTime - lastKnownExtraTime
                    lastKnownExtraTime = newExtraTime
                    
                    // Add extra time to remaining time
                    timeRemainingMs += additionalTime
                    
                    // Restart timer with new time
                    timer?.cancel()
                    timer = object : CountDownTimer(timeRemainingMs, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            timeRemainingMs = millisUntilFinished
                            updateTimerDisplay()
                            
                            when {
                                millisUntilFinished < 60000 -> {
                                    binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
                                }
                                millisUntilFinished < 300000 -> {
                                    binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_orange_light))
                                }
                            }
                        }
                        
                        override fun onFinish() {
                            timeoutAndSubmit()
                        }
                    }.start()
                    
                    Toast.makeText(this@Challenge3Activity, "⏰ Admin added ${additionalTime / 60000} minutes!", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Ignore errors for extra time listener
            }
        }
        ref.addValueEventListener(extraTimeListener!!)
    }
    
    private fun updateTimerDisplay() {
        val minutes = (timeRemainingMs / 1000) / 60
        val seconds = (timeRemainingMs / 1000) % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun updateProgress() {
        val answered = questionAnswers.count { it.length > 50 } // Consider >50 chars as "answered"
        binding.tvProgress.text = "$answered/${questions.size} questions completed"
        binding.progressBarCompletion.progress = (answered * 100 / questions.size)
    }
    
    private fun confirmFinalSubmit() {
        saveCurrentAnswer()
        
        val answeredCount = questionAnswers.count { it.length > 50 }
        
        val message = if (answeredCount < questions.size) {
            "Warning: Only $answeredCount of ${questions.size} questions have substantial code.\n\nSubmit anyway?"
        } else {
            "All ${questions.size} questions have code!\n\nReady for final submission?"
        }
        
        AlertDialog.Builder(this)
            .setTitle("📝 Final Submission")
            .setMessage(message)
            .setPositiveButton("Submit All") { _, _ -> showReviewScreen() }
            .setNegativeButton("Review Again", null)
            .show()
    }
    
    private fun showReviewScreen() {
        val reviewText = buildString {
            questions.forEachIndexed { i, question ->
                val answer = questionAnswers.getOrNull(i) ?: ""
                val lines = answer.lines().size
                appendLine("━━━ Question ${i + 1}: ${question.scenario} ━━━")
                appendLine("Lines of code: $lines")
                appendLine("Status: ${if (answer.length > 50) "✅ Submitted" else "⚠️ Minimal"}")
                appendLine()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔍 Review Your Code")
            .setMessage(reviewText)
            .setPositiveButton("✓ Confirm Submit") { _, _ -> submitChallenge() }
            .setNegativeButton("← Go Back", null)
            .show()
    }
    
    private fun submitChallenge() {
        showLoading(true, "Evaluating all 3 complete code solutions...")
        timer?.cancel()
        
        lifecycleScope.launch {
            // Build questions with answers
            val submittedQuestions = questions.mapIndexed { i, q ->
                q.copy(userCode = questionAnswers.getOrElse(i) { "" })
            }
            
            // Calculate time bonus
            val timeUsedMs = ChallengeConstants.CHALLENGE_3_TIME_MS - timeRemainingMs
            val timeBonus = calculateTimeBonus(timeUsedMs)
            
            // Evaluate
            val evaluation = evaluateSubmission(submittedQuestions, timeBonus)
            
            // Submit to Firebase
            val success = eventService.submitChallenge3(rollNumber, submittedQuestions, evaluation)
            
            if (success) {
                navigateToRankingDashboard()
            } else {
                showLoading(false)
                Toast.makeText(this@Challenge3Activity, "Submission failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun calculateTimeBonus(timeUsedMs: Long): Int {
        // TimeBonus = (30 - timeUsed) / 30 × 100 for Challenge 3
        val timeUsedMinutes = timeUsedMs / 60000.0
        val bonus = ((30.0 - timeUsedMinutes) / 30.0 * 100.0).toInt()
        return maxOf(0, bonus)
    }
    
    private fun evaluateSubmission(submittedQuestions: List<Challenge3QuestionInternal>, timeBonus: Int): EvaluationResult {
        var totalScore = 0
        var hasAnyAttempt = false
        
        submittedQuestions.forEach { question ->
            val code = question.userCode ?: ""
            
            if (code.isNotEmpty()) {
                hasAnyAttempt = true
                var questionScore = 0
                
                // Check for expected elements
                question.expectedElements.forEach { element ->
                    if (code.contains(element, ignoreCase = true)) {
                        questionScore += 10
                    }
                }
                
                // Check for essential structure
                if (code.contains("void setup()") || code.contains("void setup ()")) questionScore += 15
                if (code.contains("void loop()") || code.contains("void loop ()")) questionScore += 15
                if (code.contains("#include")) questionScore += 10
                if (code.contains("#define") || code.contains("const int")) questionScore += 10
                
                // Line count bonus (more complete code)
                val lines = code.lines().size
                questionScore += minOf(lines / 2, 20)
                
                totalScore += minOf(questionScore, 100) / questions.size
            }
        }
        
        // If no attempt was made, return 0 score
        if (!hasAnyAttempt) {
            return EvaluationResult(
                attemptCompleteness = EvaluationCategory(0, 20, "No attempt made"),
                syntaxCorrectness = EvaluationCategory(0, 20, "No code submitted"),
                logicAccuracy = EvaluationCategory(0, 25, "No solution provided"),
                criticalElements = EvaluationCategory(0, 15, "No code written"),
                codeQuality = EvaluationCategory(0, 10, "No code provided"),
                errorCount = EvaluationCategory(0, 10, "N/A"),
                totalScore = 0,
                maxScore = 100,
                percentage = 0.0,
                weightedScore = 0,
                feedback = "No attempt was made. Please write the complete code solution.",
                evaluatedAt = System.currentTimeMillis()
            )
        }
        
        // Apply time bonus (20% weight) only if user attempted something
        val finalScore = (totalScore * 0.8 + timeBonus * 0.2).toInt()
        
        return EvaluationResult(
            attemptCompleteness = EvaluationCategory(18, 20, ""),
            syntaxCorrectness = EvaluationCategory(16, 20, ""),
            logicAccuracy = EvaluationCategory(22, 25, ""),
            criticalElements = EvaluationCategory(12, 15, ""),
            codeQuality = EvaluationCategory(8, 10, ""),
            errorCount = EvaluationCategory(7, 10, ""),
            totalScore = finalScore,
            maxScore = 100,
            percentage = finalScore.toDouble(),
            weightedScore = (finalScore * ChallengeConstants.CHALLENGE_3_WEIGHT).toInt(),
            feedback = "Evaluated ${questions.size} complete code solutions. Time bonus: $timeBonus points.",
            evaluatedAt = System.currentTimeMillis()
        )
    }
    
    private fun timeoutAndSubmit() {
        Toast.makeText(this, "Time's up! Auto-submitting...", Toast.LENGTH_LONG).show()
        saveCurrentAnswer()
        
        lifecycleScope.launch {
            eventService.timeoutChallenge(rollNumber, 3)
            submitChallenge()
        }
    }
    
    private fun navigateToRankingDashboard() {
        val intent = Intent(this, RankingDashboardActivity::class.java)
        intent.putExtra(RollNumberEntryActivity.EXTRA_ROLL_NUMBER, rollNumber)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(show: Boolean, message: String = "Loading...") {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvLoadingMessage.text = message
    }
    
    override fun onStop() {
        super.onStop()
        if (!isFinishing) handleAppBackground()
    }
    
    private var isTerminated = false
    
    private fun handleAppBackground() {
        warningCount++
        lifecycleScope.launch {
            // Save current answer first
            saveCurrentAnswer()
            
            // Serialize complete state (all answers + timer + current index)
            val stateJson = serializeCurrentState()
            eventService.saveChallengeState(
                rollNumber = rollNumber,
                challengeNumber = 3,
                stateJson = stateJson,
                timeRemainingMs = timeRemainingMs,
                currentProblemIndex = currentQuestionIndex
            )
            android.util.Log.i("Challenge3", "Saved state on exit (warningCount=$warningCount, timeRemaining=${timeRemainingMs}ms)")
            
            eventService.addWarning(rollNumber)
            if (warningCount >= 2) {
                eventService.terminateParticipant(rollNumber, ParticipantStatus.TERMINATION_EXIT_CHOICE)
                isTerminated = true
                android.util.Log.i("Challenge3", "User terminated after 2nd exit, state preserved for resume")
            }
        }
    }
    
    /**
     * Serialize current state (all answers) for resume
     */
    private fun serializeCurrentState(): String {
        val stateObj = JSONObject().apply {
            val answersArray = JSONArray()
            questionAnswers.forEachIndexed { idx, answer ->
                answersArray.put(JSONObject().apply {
                    put("questionIndex", idx)
                    put("answer", answer)
                })
            }
            put("answers", answersArray)
            put("currentQuestionIndex", currentQuestionIndex)
        }
        return stateObj.toString()
    }
    
    /**
     * Parse and restore state from saved JSON
     */
    private fun parseAndRestoreState(stateJson: String, savedQuestionIndex: Int) {
        try {
            val stateObj = JSONObject(stateJson)
            
            val answersArray = stateObj.optJSONArray("answers")
            if (answersArray != null) {
                for (i in 0 until answersArray.length()) {
                    val answerObj = answersArray.getJSONObject(i)
                    val questionIndex = answerObj.getInt("questionIndex")
                    val answer = answerObj.optString("answer", "")
                    
                    if (questionIndex < questionAnswers.size) {
                        questionAnswers[questionIndex] = answer
                    }
                }
            }
            
            currentQuestionIndex = savedQuestionIndex
            android.util.Log.i("Challenge3", "Restored state: questionIndex=$savedQuestionIndex, answers=${questionAnswers.size}")
            
        } catch (e: Exception) {
            android.util.Log.e("Challenge3", "Failed to parse state JSON", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (isTerminated) {
            showTerminationDialog()
        } else if (warningCount == 1) {
            showWarningDialog()
        }
    }
    
    private fun showTerminationDialog() {
        timer?.cancel()
        AlertDialog.Builder(this)
            .setTitle("❌ Session Terminated")
            .setMessage("Your session has been terminated due to multiple exits from the challenge.\n\nYou cannot continue this challenge.")
            .setPositiveButton("Exit to Login") { _, _ ->
                navigateToLogin()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.auth.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun showWarningDialog() {
        binding.ivWarning.visibility = View.VISIBLE
        AlertDialog.Builder(this)
            .setTitle("⚠️ Warning!")
            .setMessage("You left the challenge! This is your FINAL warning.")
            .setPositiveButton("I Understand") { _, _ -> }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        // Clean up extra time listener
        extraTimeListener?.let { listener ->
            eventService.getParticipantRef(rollNumber).child("challenge3/extraTimeGrantedMs")
                .removeEventListener(listener)
        }
    }
    
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit Challenge?")
            .setMessage("If you exit, you will receive a warning and may be terminated.")
            .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Stay", null)
            .show()
    }
    
    // Block keyboard paste shortcuts (Ctrl+V) for tablets/ChromeOS
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V) {
            return true // Block paste
        }
        return super.dispatchKeyEvent(event)
    }
}
