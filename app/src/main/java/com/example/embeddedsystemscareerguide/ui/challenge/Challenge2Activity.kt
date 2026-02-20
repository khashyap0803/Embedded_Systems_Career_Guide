package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityChallenge2Binding
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.example.embeddedsystemscareerguide.services.Challenge2QuestionInternal
import com.example.embeddedsystemscareerguide.services.GeminiChallengeService
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Challenge 2 Activity - Medium Difficulty - Code Completion
 * Supports 3 QUESTIONS with navigation as per spec
 * User fills in missing code lines (2-4 lines)
 * Timer: 20 minutes total for all 3 questions
 * Supports resume functionality with saved questions
 */
class Challenge2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityChallenge2Binding
    private lateinit var eventService: PreReleaseEventService
    private lateinit var geminiService: GeminiChallengeService
    
    private var rollNumber: String = ""
    private var isResume: Boolean = false
    private var timer: CountDownTimer? = null
    private var timeRemainingMs: Long = ChallengeConstants.CHALLENGE_2_TIME_MS
    private var warningCount: Int = 0
    private var timerStartRealtime: Long = 0L  // C-07: wall-clock tracking
    private var challengeStartRealtime: Long = 0L  // BUG#1-FIX: track total elapsed for time bonus
    private var isSubmitting: Boolean = false   // H-01: prevent false positive warnings during submit
    private var lastKnownExtraTime: Long = 0L
    private var timerRunning: Boolean = false  // BUG#7-FIX: prevent duplicate timer starts
    private var extraTimeListener: com.google.firebase.database.ValueEventListener? = null
    
    // ========== MULTI-QUESTION SUPPORT ==========
    private var currentQuestionIndex: Int = 0
    private val questions = mutableListOf<Challenge2QuestionInternal>()
    private val questionAnswers = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallenge2Binding.inflate(layoutInflater)
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
        
        android.util.Log.i("Challenge2", "Starting Challenge 2 for $rollNumber, isResume=$isResume")
        
        setupUI()
        setupBackPressHandler()
        loadQuestions()
    }
    
    private fun setupUI() {
        binding.tvRollNumber.text = "Roll: $rollNumber"
        
        // Disable copy-paste on the code input
        binding.etMissingCode.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        binding.etMissingCode.customInsertionActionModeCallback = object : android.view.ActionMode.Callback {
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
            Toast.makeText(this, "💾 Answer saved!", Toast.LENGTH_SHORT).show()
        }
        
        // Progress dots
        binding.dot1.setOnClickListener { navigateToQuestion(0) }
        binding.dot2.setOnClickListener { navigateToQuestion(1) }
        binding.dot3.setOnClickListener { navigateToQuestion(2) }
    }
    
    private fun loadQuestions() {
        lifecycleScope.launch {
            if (isResume) {
                // RESUME MODE: Load saved questions AND state from Firebase
                showLoading(true, "Restoring your saved progress...")
                android.util.Log.i("Challenge2", "Resume mode: Loading saved questions and state for $rollNumber")
                
                val savedJson = eventService.loadSavedQuestions(rollNumber, 2)
                if (savedJson != null && savedJson.isNotEmpty()) {
                    try {
                        val loaded = parseQuestionsFromJson(savedJson)
                        if (loaded.isNotEmpty()) {
                            questions.addAll(loaded)
                            questions.forEach { _ -> questionAnswers.add("") }
                            
                            // Load saved state (answers, timer, question index)
                            val savedState = eventService.loadChallengeState(rollNumber, 2)
                            var resumeTimeMs = ChallengeConstants.CHALLENGE_2_TIME_MS
                            var resumeQuestionIndex = 0
                            
                            // Check if user has EXTRA TIME GRANTED (for timed-out users)
                            // If yes, use ONLY the extra time, not the original timer
                            val extraTimeInfo = eventService.getExtraTimeInfo(rollNumber)
                            val hasExtraTime = extraTimeInfo != null && extraTimeInfo.first == 2 && extraTimeInfo.second > 0
                            
                            if (hasExtraTime) {
                                // User timed out and admin granted extra time - use ONLY the extra time
                                resumeTimeMs = extraTimeInfo!!.second
                                // CRITICAL: Set lastKnownExtraTime so listenForExtraTime won't double-add this time
                                lastKnownExtraTime = extraTimeInfo.second
                                android.util.Log.i("Challenge2", "Using EXTRA TIME ONLY: ${resumeTimeMs}ms (${resumeTimeMs / 60000} minutes), lastKnownExtraTime set")
                            } else if (savedState != null) {
                                // Normal resume (terminated user) - use saved remaining time
                                val (stateJson, savedTimeMs, savedQuestionIdx) = savedState
                                resumeTimeMs = if (savedTimeMs > 0) savedTimeMs else ChallengeConstants.CHALLENGE_2_TIME_MS
                                resumeQuestionIndex = savedQuestionIdx
                                android.util.Log.i("Challenge2", "Using saved time: ${resumeTimeMs}ms")
                            }
                            
                            // Restore answers from saved state regardless of timer source
                            if (savedState != null) {
                                val (stateJson, _, savedQuestionIdx) = savedState
                                resumeQuestionIndex = savedQuestionIdx
                                
                                // Parse and restore all answers
                                if (stateJson != null) {
                                    parseAndRestoreState(stateJson, savedQuestionIdx)
                                }
                                
                                android.util.Log.i("Challenge2", "Restored state: timeRemaining=${resumeTimeMs}ms, questionIndex=$resumeQuestionIndex")
                            }
                            
                            // Set timer to saved value
                            timeRemainingMs = resumeTimeMs
                            currentQuestionIndex = resumeQuestionIndex
                            
                            showLoading(false)
                            displayQuestion(currentQuestionIndex)
                            
                            // Restore UI - show saved answer in text field
                            if (currentQuestionIndex < questionAnswers.size) {
                                binding.etMissingCode.setText(questionAnswers[currentQuestionIndex])
                            }
                            
                            startTimer()
                            android.util.Log.i("Challenge2", "Successfully resumed with ${questions.size} questions at index $currentQuestionIndex")
                            return@launch
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Challenge2", "Failed to parse saved questions/state", e)
                    }
                }
                
                // If loading failed, fall back to generating new questions
                android.util.Log.w("Challenge2", "No saved questions found, generating new ones")
                isResume = false
            }
            
            // NEW CHALLENGE MODE: Generate new questions via Gemini
            showLoading(true, "Generating 3 code completion questions...")
            
            eventService.startChallenge2(rollNumber)
            
            // Generate questions via API (cloud-only, no local fallback)
            val geminiResult = geminiService.generateChallenge2Questions()
            
            if (geminiResult.isSuccess && geminiResult.getOrNull()?.size == 3) {
                geminiResult.getOrNull()?.forEach { q ->
                    questions.add(q)
                }
                
                // Initialize answers for each question
                questions.forEach { _ -> questionAnswers.add("") }
                
                // SAVE QUESTIONS TO FIREBASE for future resume
                val questionsJson = serializeQuestionsToJson(questions)
                eventService.saveGeneratedQuestions(rollNumber, 2, questionsJson)
                android.util.Log.i("Challenge2", "Saved ${questions.size} questions to Firebase for resume")
                
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
    private fun serializeQuestionsToJson(questionsList: List<Challenge2QuestionInternal>): String {
        val jsonArray = JSONArray()
        questionsList.forEach { q ->
            jsonArray.put(JSONObject().apply {
                put("id", q.id)
                put("scenario", q.scenario)
                put("description", q.description)
                put("preCode", q.preCode)
                put("postCode", q.postCode)
                put("missingLineCount", q.missingLineCount)
                // BUG#6-FIX: Keep correctAnswer for fair evaluation on resume
                // Firebase security rules already restrict read access
                put("correctAnswer", q.correctAnswer)
                put("hints", JSONArray(q.hints))
            })
        }
        return jsonArray.toString()
    }
    
    /**
     * Parse questions from saved JSON
     */
    private fun parseQuestionsFromJson(json: String): List<Challenge2QuestionInternal> {
        val result = mutableListOf<Challenge2QuestionInternal>()
        val jsonArray = JSONArray(json)
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            
            val hintsArray = obj.getJSONArray("hints")
            val hints = mutableListOf<String>()
            for (j in 0 until hintsArray.length()) {
                hints.add(hintsArray.getString(j))
            }
            
            result.add(Challenge2QuestionInternal(
                id = obj.getInt("id"),
                scenario = obj.optString("scenario", ""),
                description = obj.optString("description", ""),
                preCode = obj.optString("preCode", ""),
                postCode = obj.optString("postCode", ""),
                missingLineCount = obj.optInt("missingLineCount", 4),
                correctAnswer = obj.optString("correctAnswer", ""),
                hints = hints
            ))
        }
        
        return result
    }
    
    private fun showGeminiErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Connection Error")
            .setMessage("Failed to generate questions.\n\nError: $errorMessage\n\nPlease check your internet connection and try again.")
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
        
        // Only save current answer when navigating AWAY from current question
        // (not on initial display, which would overwrite restored answers with empty text)
        if (index != currentQuestionIndex && currentQuestionIndex < questionAnswers.size) {
            saveCurrentAnswer()
        }
        
        currentQuestionIndex = index
        val question = questions[index]
        
        // Update UI
        binding.tvQuestionNumber.text = "Question ${index + 1} of ${questions.size}"
        binding.tvScenario.text = question.scenario
        binding.tvDescription.text = question.description
        binding.tvPreCode.text = question.preCode
        binding.tvPostCode.text = question.postCode
        binding.tvMissingLines.text = "Fill in ${question.missingLineCount} lines of code:"
        binding.tvHints.text = "💡 Hints: ${question.hints.joinToString(", ")}"
        
        // Restore saved answer
        binding.etMissingCode.setText(questionAnswers.getOrElse(index) { "" })
        
        // Update navigation buttons
        binding.btnPrevQuestion.isEnabled = index > 0
        binding.btnNextQuestion.visibility = if (index < questions.size - 1) View.VISIBLE else View.GONE
        binding.btnSubmit.visibility = if (index == questions.size - 1) View.VISIBLE else View.GONE
        
        // Update progress dots
        updateProgressDots()
        updateProgress()
    }
    
    private fun updateProgressDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { i, dot ->
            when {
                i == currentQuestionIndex -> dot.setBackgroundResource(R.drawable.dot_active)
                questionAnswers.getOrNull(i)?.isNotEmpty() == true -> dot.setBackgroundResource(R.drawable.dot_completed)
                else -> dot.setBackgroundResource(R.drawable.dot_inactive)
            }
        }
    }
    
    private fun saveCurrentAnswer() {
        if (currentQuestionIndex < questionAnswers.size) {
            questionAnswers[currentQuestionIndex] = binding.etMissingCode.text.toString()
        }
        // M-10: Removed toast spam — this is called automatically on every navigation
    }
    
    private fun navigateToQuestion(index: Int) {
        if (index in 0 until questions.size) {
            displayQuestion(index)
        }
    }
    
    private fun startTimer() {
        // Listen for extra time added by admin (only on first start)
        if (!timerRunning) listenForExtraTime()
        
        // C-07: Record wall-clock start time
        timerStartRealtime = android.os.SystemClock.elapsedRealtime()
        // BUG#1-FIX: Record challenge start only once (for accurate time bonus)
        if (challengeStartRealtime == 0L) challengeStartRealtime = timerStartRealtime
        
        timer?.cancel()  // BUG#7-FIX: Cancel any existing timer before starting new one
        timerRunning = true
        
        timer = object : CountDownTimer(timeRemainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                updateTimerDisplay()
                updateTimerColor()  // BUG#3/9-FIX: Use helper
            }
            
            override fun onFinish() {
                timerRunning = false
                timeoutAndSubmit()
            }
        }.start()
    }
    
    // BUG#3/9-FIX: Extracted timer color logic into helper to eliminate duplication
    private fun updateTimerColor() {
        when {
            timeRemainingMs < 60000 -> {
                binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
            }
            timeRemainingMs < 300000 -> {
                binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_orange_light))
            }
            else -> {
                binding.timerCard.setCardBackgroundColor(getColor(R.color.indigo_600))
            }
        }
    }
    
    private fun listenForExtraTime() {
        val ref = eventService.getParticipantRef(rollNumber).child("challenge2/extraTimeGrantedMs")
        extraTimeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val newExtraTime = snapshot.getValue(Long::class.java) ?: 0L
                if (newExtraTime > lastKnownExtraTime) {
                    val additionalTime = newExtraTime - lastKnownExtraTime
                    lastKnownExtraTime = newExtraTime
                    
                    // Add extra time to remaining time
                    timeRemainingMs += additionalTime
                    
                    // Restart timer with new time — reuse startTimer() logic (BUG#3-FIX)
                    startTimer()
                    
                    Toast.makeText(this@Challenge2Activity, "⏰ Admin added ${additionalTime / 60000} minutes!", Toast.LENGTH_LONG).show()
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
        if (questions.isEmpty()) return  // BUG#10-FIX: Guard against division by zero
        val answered = questionAnswers.count { it.isNotEmpty() }
        binding.tvProgress.text = "$answered/${questions.size} questions answered"
        binding.progressBarCompletion.progress = (answered * 100 / questions.size)
    }
    
    private fun confirmFinalSubmit() {
        saveCurrentAnswer()
        
        val answeredCount = questionAnswers.count { it.isNotEmpty() }
        
        val message = if (answeredCount < questions.size) {
            "Warning: Only $answeredCount of ${questions.size} questions answered.\n\nSubmit anyway?"
        } else {
            "All ${questions.size} questions answered!\n\nReady to submit?"
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
                appendLine("━━━ Question ${i + 1}: ${question.scenario} ━━━")
                appendLine("Answer: ${if (answer.isNotEmpty()) "✅ Provided" else "⚠️ Empty"}")
                if (answer.isNotEmpty()) {
                    appendLine(answer.take(100) + if (answer.length > 100) "..." else "")
                }
                appendLine()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔍 Review Your Answers")
            .setMessage(reviewText)
            .setPositiveButton("✓ Confirm Submit") { _, _ -> submitChallenge() }
            .setNegativeButton("← Go Back", null)
            .show()
    }
    
    private fun submitChallenge(isTimeout: Boolean = false) {
        showLoading(true, "Evaluating your submissions...")
        timer?.cancel()
        isSubmitting = true  // H-01: prevent anti-cheat during submission
        
        lifecycleScope.launch {
            // Build questions with answers
            val submittedQuestions = questions.mapIndexed { i, q ->
                q.copy(userAnswer = questionAnswers.getOrElse(i) { "" })
            }
            
            // BUG#1-FIX: Use actual elapsed wall-clock time, not constant-based calculation
            val actualElapsedMs = if (challengeStartRealtime > 0L)
                android.os.SystemClock.elapsedRealtime() - challengeStartRealtime
            else
                maxOf(0L, ChallengeConstants.CHALLENGE_2_TIME_MS - timeRemainingMs)
            val timeUsedMs = maxOf(0L, actualElapsedMs)
            val timeBonus = calculateTimeBonus(timeUsedMs)
            
            // Evaluate with retry (no local fallback)
            val evaluation = try {
                val evalResult = geminiService.evaluateChallenge2(submittedQuestions)
                
                if (evalResult.isSuccess) {
                    android.util.Log.i("Challenge2", "Evaluation successful")
                    val result = evalResult.getOrThrow()
                    // Apply time bonus: 80% evaluation + 20% time bonus
                    val adjustedScore = (result.totalScore * 0.8 + timeBonus * 0.2).toInt().coerceIn(0, 100)
                    result.copy(
                        totalScore = adjustedScore,
                        percentage = adjustedScore.toDouble(),
                        weightedScore = (adjustedScore * ChallengeConstants.CHALLENGE_2_WEIGHT).toInt()
                    )
                } else {
                    android.util.Log.w("Challenge2", "Evaluation failed", evalResult.exceptionOrNull())
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("Challenge2", "Evaluation exception", e)
                null
            }
            
            if (evaluation == null) {
                showLoading(false)
                isSubmitting = false
                Toast.makeText(this@Challenge2Activity, "⚠️ Evaluation failed. Please check your internet and try again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            // Submit to Firebase
            val success = eventService.submitChallenge2(rollNumber, submittedQuestions, evaluation, isTimeout)
            
            if (success) {
                navigateToRankingDashboard()
            } else {
                showLoading(false)
                isSubmitting = false
                Toast.makeText(this@Challenge2Activity, "Submission failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun calculateTimeBonus(timeUsedMs: Long): Int {
        val timeUsedMinutes = timeUsedMs / 60000.0
        val bonus = ((20.0 - timeUsedMinutes) / 20.0 * 100.0).toInt()
        return maxOf(0, bonus)
    }
    
    private fun timeoutAndSubmit() {
        Toast.makeText(this, "Time's up! Auto-submitting...", Toast.LENGTH_LONG).show()
        saveCurrentAnswer()
        
        lifecycleScope.launch {
            // CRITICAL: Save state to Firebase BEFORE timeout so user can resume with their answers
            val stateJson = serializeCurrentState()
            eventService.saveChallengeState(
                rollNumber = rollNumber,
                challengeNumber = 2,
                stateJson = stateJson,
                timeRemainingMs = 0L, // Timed out, no time remaining
                currentProblemIndex = currentQuestionIndex
            )
            android.util.Log.i("Challenge2", "Saved challenge state to Firebase before timeout")
            
            eventService.timeoutChallenge(rollNumber, 2)
            submitChallenge(isTimeout = true)
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
        // H-01: Don't trigger anti-cheat during submission or when finishing
        if (!isFinishing && !isSubmitting) handleAppBackground()
    }
    
    private var isTerminated = false
    
    private fun handleAppBackground() {
        // C-07: Pause timer when going to background
        timer?.cancel()
        timerRunning = false  // BUG#7-FIX: mark timer as stopped
        
        warningCount++
        lifecycleScope.launch {
            // Save current answer first
            saveCurrentAnswer()
            
            // Serialize complete state (all answers + timer + current index)
            val stateJson = serializeCurrentState()
            eventService.saveChallengeState(
                rollNumber = rollNumber,
                challengeNumber = 2,
                stateJson = stateJson,
                timeRemainingMs = timeRemainingMs,
                currentProblemIndex = currentQuestionIndex
            )
            android.util.Log.i("Challenge2", "Saved state on exit (warningCount=$warningCount, timeRemaining=${timeRemainingMs}ms)")
            
            eventService.addWarning(rollNumber)
            if (warningCount >= 2) {
                eventService.terminateParticipant(rollNumber, ParticipantStatus.TERMINATION_EXIT_CHOICE)
                isTerminated = true
                android.util.Log.i("Challenge2", "User terminated after 2nd exit, state preserved for resume")
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
            android.util.Log.i("Challenge2", "Restored state: questionIndex=$savedQuestionIndex, answers=${questionAnswers.size}")
            
        } catch (e: Exception) {
            android.util.Log.e("Challenge2", "Failed to parse state JSON", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // BUG#4-FIX: Sync warningCount FIRST, then decide what to show
        lifecycleScope.launch {
            try {
                val snapshot = eventService.getParticipantRef(rollNumber)
                    .child("status/warningCount").get().await()
                val firebaseCount = snapshot.getValue(Int::class.java) ?: 0
                if (firebaseCount > warningCount) {
                    warningCount = firebaseCount
                    android.util.Log.i("Challenge2", "Synced warningCount from Firebase: $warningCount")
                }
            } catch (e: Exception) {
                android.util.Log.w("Challenge2", "Failed to sync warningCount", e)
            }
            
            // BUG#4-FIX: All decisions now happen AFTER warningCount sync completes
            if (isTerminated) {
                showTerminationDialog()
            } else if (warningCount >= 1 && !timerRunning) {
                // BUG#2-FIX: Deduct background time before restarting timer
                if (timerStartRealtime > 0) {
                    val backgroundTimeMs = android.os.SystemClock.elapsedRealtime() - timerStartRealtime
                    if (backgroundTimeMs > 1000) {
                        timeRemainingMs = maxOf(0L, timeRemainingMs - backgroundTimeMs)
                        android.util.Log.i("Challenge2", "Deducted ${backgroundTimeMs}ms background time, remaining=${timeRemainingMs}ms")
                    }
                }
                showWarningDialog()
                if (timerStartRealtime > 0) {
                    startTimer()
                }
            }
            // BUG#7-FIX: Removed warningCount==0 branch — initial timer is started by loadQuestions()
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
            eventService.getParticipantRef(rollNumber).child("challenge2/extraTimeGrantedMs")
                .removeEventListener(listener)
        }
    }
    
    // H-06: Use OnBackPressedCallback instead of deprecated onBackPressed
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@Challenge2Activity)
                    .setTitle("Exit Challenge?")
                    .setMessage("If you exit, you will receive a warning and may be terminated.")
                    .setPositiveButton("Exit") { _, _ ->
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .setNegativeButton("Stay", null)
                    .show()
            }
        })
    }
    
    // Block keyboard paste shortcuts (Ctrl+V) for tablets/ChromeOS
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V) {
            return true // Block paste
        }
        return super.dispatchKeyEvent(event)
    }
}
