package com.example.embeddedsystemscareerguide.ui.challenge

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityChallenge1Binding
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.example.embeddedsystemscareerguide.services.Challenge1Problem
import com.example.embeddedsystemscareerguide.services.Challenge1ProblemAnswer
import com.example.embeddedsystemscareerguide.services.CodeBlock
import com.example.embeddedsystemscareerguide.services.CodeBlockCategory
import com.example.embeddedsystemscareerguide.services.GeminiChallengeService
import com.example.embeddedsystemscareerguide.services.PreReleaseEventService
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Challenge 1 Activity - Easy Difficulty
 * Supports 3 PROBLEMS with navigation as per spec
 * Hardware selection + drag-drop code blocks per problem
 * Timer: 20 minutes total for all 3 problems
 * Supports resume functionality with saved questions
 */
class Challenge1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityChallenge1Binding
    private lateinit var eventService: PreReleaseEventService
    private lateinit var geminiService: GeminiChallengeService
    
    private var rollNumber: String = ""
    private var isResume: Boolean = false
    private var timer: CountDownTimer? = null
    private var timeRemainingMs: Long = ChallengeConstants.CHALLENGE_1_TIME_MS
    private var warningCount: Int = 0
    private var timerStartRealtime: Long = 0L  // C-07: wall-clock tracking
    private var isSubmitting: Boolean = false   // H-01: prevent false positive warnings during submit
    private var lastKnownExtraTime: Long = 0L
    private var timerRunning: Boolean = false  // BUG-FIX: prevent duplicate timer starts
    private var extraTimeListener: com.google.firebase.database.ValueEventListener? = null
    private var challengeStartTime: Long = 0L   // BUG-H2: actual start time for time bonus
    private var backgroundEnteredRealtime: Long = 0L // BUG-M4: track when app goes to background
    
    // ========== MULTI-PROBLEM SUPPORT ==========
    private var currentProblemIndex: Int = 0
    private val problems = mutableListOf<Challenge1Problem>()
    private val problemAnswers = mutableListOf<Challenge1ProblemAnswer>()
    
    // Current problem state
    private var selectedMcu: String = ""
    private val selectedComponents = mutableSetOf<String>()
    private val codeBlocks = mutableListOf<CodeBlock>()
    
    // Adapters
    private lateinit var sensorsAdapter: ComponentAdapter
    private lateinit var modulesAdapter: ComponentAdapter
    private lateinit var codeBlocksAdapter: CodeBlockAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallenge1Binding.inflate(layoutInflater)
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
        
        android.util.Log.i("Challenge1", "Starting Challenge 1 for $rollNumber, isResume=$isResume")
        
        setupUI()
        setupBackPressHandler()
        loadProblems()
    }
    
    private fun setupUI() {
        binding.tvRollNumber.text = "Roll: $rollNumber"
        
        // MCU Selection
        binding.cardEsp32.setOnClickListener { selectMcu("ESP32") }
        binding.cardArduino.setOnClickListener { selectMcu("Arduino UNO") }
        
        // Load MCU board images from assets
        loadBoardImageFromAssets("esp32.png", binding.ivEsp32)
        loadBoardImageFromAssets("arduino_uno.png", binding.ivArduino)
        
        // Navigation buttons
        binding.btnPrevProblem.setOnClickListener { navigateToProblem(currentProblemIndex - 1) }
        binding.btnNextProblem.setOnClickListener { navigateToProblem(currentProblemIndex + 1) }
        binding.btnSubmit.setOnClickListener { confirmFinalSubmit() }
        
        // Problem navigation dots
        binding.dot1.setOnClickListener { navigateToProblem(0) }
        binding.dot2.setOnClickListener { navigateToProblem(1) }
        binding.dot3.setOnClickListener { navigateToProblem(2) }
        
        setupComponentRecyclerViews()
        setupCodeBlocksRecyclerView()
    }
    
    private fun loadBoardImageFromAssets(fileName: String, imageView: android.widget.ImageView) {
        try {
            // L-08: Use use{} to ensure InputStream is closed even if decodeStream throws
            val bitmap = assets.open("components/boards/$fileName").use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            // Keep default placeholder if loading fails
            android.util.Log.w("Challenge1", "Failed to load board image: $fileName", e)
        }
    }
    
    private fun loadProblems() {
        lifecycleScope.launch {
            if (isResume) {
                // RESUME MODE: Load saved questions AND state from Firebase
                showLoading(true, "Restoring your saved progress...")
                android.util.Log.i("Challenge1", "Resume mode: Loading saved questions and state for $rollNumber")
                
                // Load questions
                val savedJson = eventService.loadSavedQuestions(rollNumber, 1)
                if (savedJson != null && savedJson.isNotEmpty()) {
                    try {
                        val loaded = parseProblemsFromJson(savedJson)
                        if (loaded.isNotEmpty()) {
                            problems.addAll(loaded)
                            problems.forEach { _ ->
                                problemAnswers.add(Challenge1ProblemAnswer())
                            }
                            
                            // Load saved state (answers, timer, problem index)
                            val savedState = eventService.loadChallengeState(rollNumber, 1)
                            var resumeTimeMs = ChallengeConstants.CHALLENGE_1_TIME_MS
                            var resumeProblemIndex = 0
                            
                            // Check if user has EXTRA TIME GRANTED (for timed-out users)
                            // If yes, use ONLY the extra time, not the original timer
                            val extraTimeInfo = eventService.getExtraTimeInfo(rollNumber)
                            val hasExtraTime = extraTimeInfo != null && extraTimeInfo.first == 1 && extraTimeInfo.second > 0
                            
                            if (hasExtraTime) {
                                // User timed out and admin granted extra time - use ONLY the extra time
                                resumeTimeMs = extraTimeInfo!!.second
                                // CRITICAL: Set lastKnownExtraTime so listenForExtraTime won't double-add this time
                                lastKnownExtraTime = extraTimeInfo.second
                                android.util.Log.i("Challenge1", "Using EXTRA TIME ONLY: ${resumeTimeMs}ms (${resumeTimeMs / 60000} minutes), lastKnownExtraTime set to prevent double-add")
                            } else if (savedState != null) {
                                // Normal resume (terminated user) - use saved remaining time
                                val (stateJson, savedTimeMs, savedProblemIdx) = savedState
                                resumeTimeMs = if (savedTimeMs > 0) savedTimeMs else ChallengeConstants.CHALLENGE_1_TIME_MS
                                android.util.Log.i("Challenge1", "Using saved time: ${resumeTimeMs}ms")
                            }
                            
                            // Restore answers from saved state regardless of timer source
                            if (savedState != null) {
                                val (stateJson, _, savedProblemIdx) = savedState
                                resumeProblemIndex = savedProblemIdx
                                
                                // Parse and restore all problem answers
                                if (stateJson != null) {
                                    parseAndRestoreState(stateJson, savedProblemIdx)
                                }
                                
                                android.util.Log.i("Challenge1", "Restored state: timeRemaining=${resumeTimeMs}ms, problemIndex=$resumeProblemIndex")
                            }
                            
                            // Set timer to calculated value
                            timeRemainingMs = resumeTimeMs
                            currentProblemIndex = resumeProblemIndex
                            
                            showLoading(false)
                            displayProblem(currentProblemIndex)
                            
                            // Restore UI state after display
                            restoreCurrentProblemFromAnswer()
                            
                            // BUG-M2: Sync warningCount from Firebase BEFORE starting timer
                            try {
                                val warnSnapshot = eventService.getParticipantRef(rollNumber)
                                    .child("status/warningCount").get().await()
                                val firebaseCount = warnSnapshot.getValue(Int::class.java) ?: 0
                                warningCount = firebaseCount
                                android.util.Log.i("Challenge1", "Init synced warningCount=$warningCount from Firebase")
                            } catch (e: Exception) {
                                android.util.Log.w("Challenge1", "Failed to sync initial warningCount", e)
                            }
                            
                            challengeStartTime = System.currentTimeMillis() - (ChallengeConstants.CHALLENGE_1_TIME_MS - resumeTimeMs)
                            startTimer()
                            android.util.Log.i("Challenge1", "Successfully resumed with ${problems.size} problems at index $currentProblemIndex, timer=${resumeTimeMs}ms")
                            return@launch
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Challenge1", "Failed to parse saved questions/state", e)
                    }
                }
                
                // If loading failed, fall back to generating new problems
                android.util.Log.w("Challenge1", "No saved questions found, generating new ones")
                isResume = false // Reset flag since we couldn't resume
            }
            
            // NEW CHALLENGE MODE: Generate new problems
            showLoading(true, "Generating 3 unique problems...")
            
            // Register challenge start
            eventService.startChallenge1(rollNumber)
            
            // Generate problems via API (cloud-only, no local fallback)
            val geminiResult = geminiService.generateChallenge1Problems()
            
            if (geminiResult.isSuccess && geminiResult.getOrNull()?.size == 3) {
                val generatedProblems = geminiResult.getOrNull()!!
                
                // Generate code blocks for each problem using API with retry
                showLoading(true, "Generating intelligent code blocks for each problem...")
                
                for (p in generatedProblems) {
                    // Use API to generate problem-specific code blocks with retry
                    var codeBlocksList: List<CodeBlock>? = null
                    var lastError: Exception? = null
                    
                    // Retry up to 3 times for code block generation
                    for (attempt in 1..3) {
                        showLoading(true, "Generating code blocks (attempt $attempt/3)...")
                        val codeBlocksResult = geminiService.generateCodeBlocksForChallenge1(
                            problemStatement = p.problemStatement,
                            mcu = p.expectedMcu,
                            components = p.expectedComponents
                        )
                        
                        if (codeBlocksResult.isSuccess && !codeBlocksResult.getOrNull().isNullOrEmpty()) {
                            codeBlocksList = codeBlocksResult.getOrNull()
                            android.util.Log.i("Challenge1", "Code block generation succeeded on attempt $attempt")
                            break
                        } else {
                            lastError = codeBlocksResult.exceptionOrNull() as? Exception
                            android.util.Log.w("Challenge1", "Code block generation attempt $attempt failed: ${lastError?.message}")
                            if (attempt < 3) {
                                kotlinx.coroutines.delay(1000) // Wait 1 second before retry
                            }
                        }
                    }
                    
                    // If all retries failed, show error and abort
                    if (codeBlocksList.isNullOrEmpty()) {
                        showLoading(false)
                        showGeminiErrorDialog("Failed to generate code blocks after 3 attempts: ${lastError?.message}")
                        return@launch
                    }
                    
                    problems.add(Challenge1Problem(
                        id = p.id,
                        statement = "🎯 ${p.category}: ${p.problemStatement}",
                        problemStatement = p.problemStatement, // N1-FIX: copy raw statement for evaluation
                        expectedMcu = p.expectedMcu,
                        expectedComponents = p.expectedComponents, // N1-FIX: copy expected components
                        requiredComponents = p.expectedComponents,
                        codeBlocks = codeBlocksList,
                        difficulty = p.difficulty, // N1-FIX: copy difficulty
                        category = p.category // N1-FIX: copy category
                    ))
                }
                
                // Initialize answers for each problem
                problems.forEach { _ ->
                    problemAnswers.add(Challenge1ProblemAnswer())
                }
                
                // SAVE PROBLEMS TO FIREBASE for future resume
                val problemsJson = serializeProblemsToJson(problems)
                eventService.saveGeneratedQuestions(rollNumber, 1, problemsJson)
                android.util.Log.i("Challenge1", "Saved ${problems.size} problems to Firebase for resume")
                
                showLoading(false)
                displayProblem(0)
                challengeStartTime = System.currentTimeMillis() // BUG-H2: record actual start
                startTimer()
            } else {
                // Show error dialog and allow retry
                showLoading(false)
                showGeminiErrorDialog(geminiResult.exceptionOrNull()?.message ?: "Failed to generate problems")
            }
        }
    }
    
    /**
     * Serialize problems to JSON for Firebase storage
     */
    private fun serializeProblemsToJson(problemsList: List<Challenge1Problem>): String {
        val jsonArray = JSONArray()
        problemsList.forEach { problem ->
            val jsonObj = JSONObject().apply {
                put("id", problem.id)
                put("statement", problem.statement)
                put("problemStatement", problem.problemStatement) // BUG-C1: save raw statement for evaluation
                put("expectedMcu", problem.expectedMcu)
                put("requiredComponents", JSONArray(problem.requiredComponents))
                put("expectedComponents", JSONArray(problem.expectedComponents)) // BUG-C1
                put("difficulty", problem.difficulty) // BUG-C1
                put("category", problem.category) // BUG-C1
                
                val codeBlocksArray = JSONArray()
                problem.codeBlocks.forEach { block ->
                    codeBlocksArray.put(JSONObject().apply {
                        put("id", block.id)
                        put("content", block.content)
                        put("category", block.category.name)
                    })
                }
                put("codeBlocks", codeBlocksArray)
            }
            jsonArray.put(jsonObj)
        }
        return jsonArray.toString()
    }
    
    /**
     * Parse problems from saved JSON
     */
    private fun parseProblemsFromJson(json: String): List<Challenge1Problem> {
        val result = mutableListOf<Challenge1Problem>()
        val jsonArray = JSONArray(json)
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            
            val componentsArray = obj.getJSONArray("requiredComponents")
            val components = mutableListOf<String>()
            for (j in 0 until componentsArray.length()) {
                components.add(componentsArray.getString(j))
            }
            
            val codeBlocksArray = obj.getJSONArray("codeBlocks")
            val codeBlocks = mutableListOf<CodeBlock>()
            for (j in 0 until codeBlocksArray.length()) {
                val blockObj = codeBlocksArray.getJSONObject(j)
                codeBlocks.add(CodeBlock(
                    id = blockObj.getInt("id"),
                    content = blockObj.getString("content"),
                    category = CodeBlockCategory.valueOf(blockObj.getString("category"))
                ))
            }
            
            // BUG-C1: Restore ALL fields including problemStatement for evaluation
            val rawStatement = obj.optString("problemStatement", "")
            val displayStatement = obj.getString("statement")
            
            // Parse expectedComponents separately (may differ from requiredComponents)
            val expectedCompArray = obj.optJSONArray("expectedComponents")
            val expectedComps = mutableListOf<String>()
            if (expectedCompArray != null) {
                for (j in 0 until expectedCompArray.length()) {
                    expectedComps.add(expectedCompArray.getString(j))
                }
            } else {
                expectedComps.addAll(components) // fallback for older saved data
            }
            
            result.add(Challenge1Problem(
                id = obj.getInt("id"),
                statement = displayStatement,
                // BUG-C1: If problemStatement was never saved, extract from display statement
                problemStatement = rawStatement.ifEmpty {
                    // Strip emoji prefix "🎯 Category: " to recover raw statement
                    val colonIdx = displayStatement.indexOf(": ")
                    if (colonIdx > 0) displayStatement.substring(colonIdx + 2) else displayStatement
                },
                expectedMcu = obj.optString("expectedMcu", "ESP32"),
                expectedComponents = expectedComps,
                requiredComponents = components,
                codeBlocks = codeBlocks,
                difficulty = obj.optString("difficulty", "Easy"),
                category = obj.optString("category", "")
            ))
        }
        
        return result
    }
    
    // ========== COMPREHENSIVE STATE SAVE/LOAD FOR RESUME ==========
    
    /**
     * Serialize complete current state including:
     * - Current MCU selection and components
     * - Current code blocks arrangement
     */
    private fun serializeCurrentState(): String {
        // First, save current problem to answers before serializing
        saveCurrentAnswer()
        
        val stateObj = JSONObject().apply {
            put("currentProblemIndex", currentProblemIndex)
            
            // Save ALL problem answers (not just current)
            val allAnswers = JSONArray()
            problemAnswers.forEachIndexed { idx, answer ->
                allAnswers.put(JSONObject().apply {
                    put("selectedMcu", answer.selectedMcu)
                    put("selectedComponents", JSONArray(answer.selectedComponents))
                    put("codeBlockIdOrder", JSONArray(answer.codeBlockIdOrder))
                    put("codeBlockOrder", JSONArray(answer.codeBlockOrder))
                    put("isComplete", answer.isComplete)
                })
            }
            put("allProblemAnswers", allAnswers)
            
            android.util.Log.i("Challenge1", "Saving state: problemIndex=$currentProblemIndex, answersCount=${problemAnswers.size}")
            problemAnswers.forEachIndexed { idx, ans ->
                android.util.Log.i("Challenge1", "  Answer $idx: mcu=${ans.selectedMcu}, components=${ans.selectedComponents.size}, blockIds=${ans.codeBlockIdOrder.size}, complete=${ans.isComplete}")
            }
        }
        
        return stateObj.toString()
    }
    
    /**
     * Parse and restore complete state from saved JSON
     */
    private var savedCodeBlockOrder: List<Int> = emptyList()
    
    private fun parseAndRestoreState(stateJson: String, savedProblemIndex: Int) {
        try {
            val stateObj = JSONObject(stateJson)
            
            // Restore current problem index
            currentProblemIndex = savedProblemIndex
            
            // Restore ALL problem answers from saved state
            val allAnswersArray = stateObj.optJSONArray("allProblemAnswers")
            if (allAnswersArray != null) {
                for (i in 0 until allAnswersArray.length()) {
                    val ansObj = allAnswersArray.getJSONObject(i)
                    
                    val ansMcu = ansObj.optString("selectedMcu", "")
                    val ansComponents = mutableListOf<String>()
                    val compArr = ansObj.optJSONArray("selectedComponents")
                    if (compArr != null) {
                        for (j in 0 until compArr.length()) {
                            ansComponents.add(compArr.getString(j))
                        }
                    }
                    val ansBlockIds = mutableListOf<Int>()
                    val blockArr = ansObj.optJSONArray("codeBlockIdOrder")
                    if (blockArr != null) {
                        for (j in 0 until blockArr.length()) {
                            ansBlockIds.add(blockArr.getInt(j))
                        }
                    }
                    val ansBlockOrder = mutableListOf<String>()
                    val blockOrderArr = ansObj.optJSONArray("codeBlockOrder")
                    if (blockOrderArr != null) {
                        for (j in 0 until blockOrderArr.length()) {
                            ansBlockOrder.add(blockOrderArr.getString(j))
                        }
                    }
                    val ansComplete = ansObj.optBoolean("isComplete", false)
                    
                    // Update problemAnswers if index exists
                    if (i < problemAnswers.size) {
                        problemAnswers[i] = Challenge1ProblemAnswer(
                            selectedMcu = ansMcu,
                            selectedComponents = ansComponents,
                            codeBlockIdOrder = ansBlockIds,
                            codeBlockOrder = ansBlockOrder,
                            isComplete = ansComplete
                        )
                    }
                }
            }
            
            // Set current state from the answer for current problem (restore even if incomplete)
            val currentAnswer = problemAnswers.getOrNull(currentProblemIndex)
            if (currentAnswer != null) {
                // Restore MCU if selected
                if (currentAnswer.selectedMcu.isNotEmpty()) {
                    selectedMcu = currentAnswer.selectedMcu
                }
                // Restore components if any selected
                if (currentAnswer.selectedComponents.isNotEmpty()) {
                    selectedComponents.clear()
                    selectedComponents.addAll(currentAnswer.selectedComponents)
                }
                // Restore code block order if arranged
                if (currentAnswer.codeBlockIdOrder.isNotEmpty()) {
                    savedCodeBlockOrder = currentAnswer.codeBlockIdOrder
                }
            }
            
            android.util.Log.i("Challenge1", "Restored state: problemIndex=$savedProblemIndex, answers restored=${allAnswersArray?.length() ?: 0}")
            problemAnswers.forEachIndexed { idx, ans ->
                android.util.Log.i("Challenge1", "  Answer $idx: mcu=${ans.selectedMcu}, components=${ans.selectedComponents.size}, blocks=${ans.codeBlockIdOrder.size}, complete=${ans.isComplete}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("Challenge1", "Failed to parse state JSON", e)
        }
    }
    
    /**
     * Restore UI state for current problem from saved state
     */
    private fun restoreCurrentProblemFromAnswer() {
        // Restore selected MCU
        if (selectedMcu.isNotEmpty()) {
            selectMcu(selectedMcu)
        }
        
        // Sync components to adapters
        updateComponentsUI()
        
        // Restore code block order from saved content strings (primary) or IDs (fallback)
        val currentAnswer = problemAnswers.getOrNull(currentProblemIndex)
        val savedContentOrder = currentAnswer?.codeBlockOrder ?: emptyList()
        
        if (savedContentOrder.isNotEmpty() && codeBlocks.isNotEmpty() && savedContentOrder.size == codeBlocks.size) {
            // PRIMARY: Content-string-based restore (most reliable — matches exactly what user saw)
            android.util.Log.i("Challenge1", "Restoring by CONTENT: ${savedContentOrder.size} saved strings, ${codeBlocks.size} blocks")
            
            val blockByContent = mutableMapOf<String, MutableList<CodeBlock>>()
            codeBlocks.forEach { block ->
                blockByContent.getOrPut(block.content) { mutableListOf() }.add(block)
            }
            
            val orderedBlocks = mutableListOf<CodeBlock>()
            val usedBlocks = mutableSetOf<Int>() // track by object identity via id
            
            savedContentOrder.forEach { content ->
                val candidates = blockByContent[content]
                val block = candidates?.firstOrNull { it.id !in usedBlocks }
                if (block != null) {
                    orderedBlocks.add(block)
                    usedBlocks.add(block.id)
                }
            }
            
            // Add any remaining blocks not matched (shouldn't happen if sizes match)
            codeBlocks.filter { it.id !in usedBlocks }
                .forEach { orderedBlocks.add(it) }
            
            codeBlocks.clear()
            codeBlocks.addAll(orderedBlocks)
            if (::codeBlocksAdapter.isInitialized) {
                codeBlocksAdapter.notifyDataSetChanged()
            }
            
            android.util.Log.i("Challenge1", "Content-restore result: IDs=${orderedBlocks.map { it.id }}, first3=${orderedBlocks.take(3).map { it.content.take(25) }}")
        } else if (savedCodeBlockOrder.isNotEmpty() && codeBlocks.isNotEmpty()) {
            // FALLBACK: ID-based restore (for sessions saved before content-string fix)
            android.util.Log.i("Challenge1", "Restoring by IDs (fallback): saved=${savedCodeBlockOrder}, current IDs=${codeBlocks.map { it.id }}")
            
            val blockById = codeBlocks.associateBy { it.id }
            val orderedBlocks = mutableListOf<CodeBlock>()
            val usedIds = mutableSetOf<Int>()
            
            savedCodeBlockOrder.forEach { blockId ->
                blockById[blockId]?.let { block ->
                    if (!usedIds.contains(block.id)) {
                        orderedBlocks.add(block)
                        usedIds.add(block.id)
                    }
                }
            }
            
            codeBlocks.filter { !usedIds.contains(it.id) }
                .sortedBy { it.id }
                .forEach { block -> orderedBlocks.add(block) }
            
            codeBlocks.clear()
            codeBlocks.addAll(orderedBlocks)
            if (::codeBlocksAdapter.isInitialized) {
                codeBlocksAdapter.notifyDataSetChanged()
            }
            
            android.util.Log.i("Challenge1", "ID-restore result: IDs=${orderedBlocks.map { it.id }}")
        }
        
        android.util.Log.i("Challenge1", "Restored UI for problem $currentProblemIndex: mcu=$selectedMcu, components=${selectedComponents.size}, blocks=${codeBlocks.size}")
    }
    
    private fun updateComponentsUI() {
        // Sync selected components to adapter and refresh UI
        if (::sensorsAdapter.isInitialized) {
            sensorsAdapter.setSelectedItems(selectedComponents)
        }
        if (::modulesAdapter.isInitialized) {
            modulesAdapter.setSelectedItems(selectedComponents)
        }
    }
    
    private fun showGeminiErrorDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Generation Failed")
            .setMessage("Failed to generate problems.\n\nError: $errorMessage\n\nPlease check your internet connection and try again.")
            .setPositiveButton("🔄 Retry") { _, _ ->
                problems.clear()
                problemAnswers.clear()
                loadProblems()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    

    
    private fun displayProblem(index: Int) {
        if (index < 0 || index >= problems.size) return
        
        // BUG-C2: Only save if codeBlocks are loaded (prevents saving empty state during resume)
        if (currentProblemIndex < problemAnswers.size && codeBlocks.isNotEmpty()) {
            saveCurrentAnswer()
        }
        
        currentProblemIndex = index
        val problem = problems[index]
        
        // Update UI
        binding.tvProblemNumber.text = "Problem ${index + 1} of ${problems.size}"
        binding.tvProblemStatement.text = problem.statement
        // Note: requiredComponents removed - showing them would give away the answer
        
        // Update navigation buttons
        binding.btnPrevProblem.isEnabled = index > 0
        binding.btnNextProblem.visibility = if (index < problems.size - 1) View.VISIBLE else View.GONE
        binding.btnSubmit.visibility = if (index == problems.size - 1) View.VISIBLE else View.GONE
        
        // Update progress dots
        updateProgressDots()
        
        // FIRST: Load code blocks for this problem (original order from problem)
        codeBlocks.clear()
        codeBlocks.addAll(problem.codeBlocks)
        
        // BUG-L1: Debug logging removed for production
        codeBlocksAdapter.notifyDataSetChanged()
        
        
        // L-03: Removed unnecessary requestLayout()/invalidate() calls
        // RecyclerView handles layout automatically after adapter notifications
        
        // THEN: Load saved answer which will restore user's MCU, components, and CODE BLOCK ORDER
        loadSavedAnswer(index)
        
        updateProgress()
    }
    
    private fun updateProgressDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { i, dot ->
            when {
                i == currentProblemIndex -> dot.setBackgroundResource(R.drawable.dot_active)
                problemAnswers.getOrNull(i)?.isComplete == true -> dot.setBackgroundResource(R.drawable.dot_completed)
                else -> dot.setBackgroundResource(R.drawable.dot_inactive)
            }
        }
    }
    
    private fun saveCurrentAnswer() {
        if (currentProblemIndex < problemAnswers.size) {
            val problem = problems.getOrNull(currentProblemIndex)
            val originalOrder = problem?.codeBlocks?.map { it.content } ?: emptyList()
            val currentOrder = codeBlocks.map { it.content }
            val currentBlockIds = codeBlocks.map { it.id }
            val wasCodeReordered = originalOrder != currentOrder
            
            // Debug: log the actual content being saved for verification
            android.util.Log.d("Challenge1", "saveCurrentAnswer p$currentProblemIndex: ids=${currentBlockIds.take(5)}..., contents=${currentOrder.take(3).map { it.take(25) }}...")
            
            problemAnswers[currentProblemIndex] = Challenge1ProblemAnswer(
                selectedMcu = selectedMcu,
                selectedComponents = selectedComponents.toList(),
                codeBlockOrder = currentOrder,
                codeBlockIdOrder = currentBlockIds,
                isComplete = selectedMcu.isNotEmpty() || selectedComponents.isNotEmpty() || wasCodeReordered
            )
        }
    }
    
    private fun loadSavedAnswer(index: Int) {
        val saved = problemAnswers.getOrNull(index)
        
        // Reset state first
        selectedMcu = ""
        selectedComponents.clear()
        binding.cardEsp32.strokeWidth = 0
        binding.cardArduino.strokeWidth = 0
        
        // BUG-H3: Restore unconditionally (not gated on isComplete) — partial answers should restore too
        if (saved != null) {
            // Restore MCU if selected
            if (saved.selectedMcu.isNotEmpty()) {
                selectedMcu = saved.selectedMcu
                selectMcu(selectedMcu)
            }
            // Restore components if any selected
            if (saved.selectedComponents.isNotEmpty()) {
                selectedComponents.addAll(saved.selectedComponents)
            }
            
            // Restore code block order: prefer content strings, fallback to IDs
            if (saved.codeBlockOrder.isNotEmpty() && codeBlocks.isNotEmpty() && saved.codeBlockOrder.size == codeBlocks.size) {
                reorderCodeBlocksByContent(saved.codeBlockOrder)
            } else if (saved.codeBlockIdOrder.isNotEmpty() && codeBlocks.isNotEmpty()) {
                reorderCodeBlocksByIds(saved.codeBlockIdOrder)
            }
        }
        
        // Update adapters AFTER setting selectedComponents
        if (::sensorsAdapter.isInitialized) {
            sensorsAdapter.setSelectedItems(selectedComponents)
        }
        if (::modulesAdapter.isInitialized) {
            modulesAdapter.setSelectedItems(selectedComponents)
        }
    }
    
    private fun reorderCodeBlocksByIds(savedOrder: List<Int>) {
        val blockById = codeBlocks.associateBy { it.id }
        val orderedBlocks = mutableListOf<CodeBlock>()
        val usedIds = mutableSetOf<Int>()
        
        // First add blocks in saved order
        savedOrder.forEach { blockId ->
            blockById[blockId]?.let { block ->
                if (!usedIds.contains(block.id)) {
                    orderedBlocks.add(block)
                    usedIds.add(block.id)
                }
            }
        }
        
        // Add any remaining blocks not in saved order
        codeBlocks.filter { !usedIds.contains(it.id) }
            .sortedBy { it.id }
            .forEach { orderedBlocks.add(it) }
        
        codeBlocks.clear()
        codeBlocks.addAll(orderedBlocks)
        if (::codeBlocksAdapter.isInitialized) {
            codeBlocksAdapter.notifyDataSetChanged()
        }
    }
    
    private fun reorderCodeBlocksByContent(savedContentOrder: List<String>) {
        // Build a map of content → list of blocks (handles duplicates like "}")
        val blockByContent = mutableMapOf<String, MutableList<CodeBlock>>()
        codeBlocks.forEach { block ->
            blockByContent.getOrPut(block.content) { mutableListOf() }.add(block)
        }
        
        val orderedBlocks = mutableListOf<CodeBlock>()
        val usedBlockIds = mutableSetOf<Int>()
        
        savedContentOrder.forEach { content ->
            val candidates = blockByContent[content]
            val block = candidates?.firstOrNull { it.id !in usedBlockIds }
            if (block != null) {
                orderedBlocks.add(block)
                usedBlockIds.add(block.id)
            }
        }
        
        // Add any unmatched blocks at the end
        codeBlocks.filter { it.id !in usedBlockIds }
            .forEach { orderedBlocks.add(it) }
        
        codeBlocks.clear()
        codeBlocks.addAll(orderedBlocks)
        if (::codeBlocksAdapter.isInitialized) {
            codeBlocksAdapter.notifyDataSetChanged()
        }
        
        android.util.Log.i("Challenge1", "reorderByContent: matched ${usedBlockIds.size}/${savedContentOrder.size}, first3=${orderedBlocks.take(3).map { it.content.take(25) }}")
    }
    
    private fun navigateToProblem(index: Int) {
        if (index in 0 until problems.size) {
            displayProblem(index)
        }
    }
    
    private fun selectMcu(mcu: String) {
        selectedMcu = mcu
        
        val primaryColor = getColor(R.color.indigo_600)
        
        binding.cardEsp32.apply {
            strokeWidth = if (mcu == "ESP32") 4 else 0
            strokeColor = if (mcu == "ESP32") primaryColor else 0
        }
        binding.cardArduino.apply {
            strokeWidth = if (mcu == "Arduino UNO") 4 else 0
            strokeColor = if (mcu == "Arduino UNO") primaryColor else 0
        }
        
        updateProgress()
    }
    
    private fun setupComponentRecyclerViews() {
        val sensors = listOf(
            ComponentItem("DHT11", "dht11", ComponentType.SENSOR),
            ComponentItem("PIR Motion", "pir", ComponentType.SENSOR),
            ComponentItem("Ultrasonic", "ultrasonic", ComponentType.SENSOR),
            ComponentItem("MQ-2 Gas", "mq2", ComponentType.SENSOR),
            ComponentItem("IR Sensor", "ir", ComponentType.SENSOR),
            ComponentItem("LDR", "ldr", ComponentType.SENSOR),
            ComponentItem("Flame", "flame", ComponentType.SENSOR),
            ComponentItem("Rain", "rain", ComponentType.SENSOR),
            ComponentItem("MQ-7 CO", "mq7", ComponentType.SENSOR),
            ComponentItem("Soil Moisture", "soil", ComponentType.SENSOR),
            ComponentItem("Sound", "sound", ComponentType.SENSOR),
            ComponentItem("Vibration", "vibration", ComponentType.SENSOR),
            ComponentItem("Water Level", "water", ComponentType.SENSOR),
            ComponentItem("Touch", "touch", ComponentType.SENSOR)
        )
        
        val modules = listOf(
            ComponentItem("Relay", "relay", ComponentType.MODULE),
            ComponentItem("LCD I2C", "lcd_i2c", ComponentType.MODULE),
            ComponentItem("Buzzer", "buzzer", ComponentType.MODULE),
            ComponentItem("Servo SG90", "servo", ComponentType.MODULE),
            ComponentItem("DC Motor", "dc_motor", ComponentType.MODULE),
            ComponentItem("RGB LED", "rgb_led", ComponentType.MODULE),
            ComponentItem("Keypad 4x4", "keypad", ComponentType.MODULE),
            ComponentItem("Bluetooth", "bluetooth", ComponentType.MODULE),
            ComponentItem("GSM SIM800L", "gsm", ComponentType.MODULE),
            ComponentItem("SD Card", "sd_card", ComponentType.MODULE),
            ComponentItem("RTC DS3231", "rtc", ComponentType.MODULE),
            ComponentItem("Push Button", "button", ComponentType.MODULE),
            ComponentItem("DC Fan", "fan", ComponentType.MODULE),
            ComponentItem("Battery Holder", "battery", ComponentType.MODULE)
        )
        
        sensorsAdapter = ComponentAdapter(sensors) { component, selected ->
            if (selected) selectedComponents.add(component.name)
            else selectedComponents.remove(component.name)
            updateProgress()
        }
        
        modulesAdapter = ComponentAdapter(modules) { component, selected ->
            if (selected) selectedComponents.add(component.name)
            else selectedComponents.remove(component.name)
            updateProgress()
        }
        
        binding.rvSensors.adapter = sensorsAdapter
        binding.rvModules.adapter = modulesAdapter
    }
    
    private fun setupCodeBlocksRecyclerView() {
        codeBlocksAdapter = CodeBlockAdapter(codeBlocks)
        binding.rvCodeBlocks.adapter = codeBlocksAdapter
        
        // NestedScrollView properly handles wrap_content RecyclerView, 
        // so we can use a standard LinearLayoutManager
        binding.rvCodeBlocks.layoutManager = LinearLayoutManager(this)
        binding.rvCodeBlocks.isNestedScrollingEnabled = false
        binding.rvCodeBlocks.setHasFixedSize(false)
        
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                Collections.swap(codeBlocks, fromPos, toPos)
                codeBlocksAdapter.notifyItemMoved(fromPos, toPos)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvCodeBlocks)
    }
    
    private fun startTimer() {
        // BUG-FIX: Listen for extra time only on first start (prevents duplicate listeners)
        if (!timerRunning) listenForExtraTime()
        
        // C-07: Record wall-clock start time for accurate background time tracking
        timerStartRealtime = android.os.SystemClock.elapsedRealtime()
        
        timer?.cancel()  // BUG-FIX: Cancel any existing timer before starting new one
        timerRunning = true
        
        timer = object : CountDownTimer(timeRemainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                updateTimerDisplay()
                updateTimerColor(millisUntilFinished) // L4-FIX: extracted helper
            }
            
            override fun onFinish() {
                timerRunning = false  // BUG-FIX: mark timer as stopped
                timeoutAndSubmit()
            }
        }.start()
    }
    
    /** L4-FIX: Extracted shared timer color logic */
    private fun updateTimerColor(millisUntilFinished: Long) {
        when {
            millisUntilFinished < 60000 -> {
                binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
            }
            millisUntilFinished < 300000 -> {
                binding.timerCard.setCardBackgroundColor(getColor(android.R.color.holo_orange_light))
            }
            else -> {
                binding.timerCard.setCardBackgroundColor(getColor(R.color.indigo_600))
            }
        }
    }
    
    private fun listenForExtraTime() {
        val ref = eventService.getParticipantRef(rollNumber).child("challenge1/extraTimeGrantedMs")
        extraTimeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val newExtraTime = snapshot.getValue(Long::class.java) ?: 0L
                if (newExtraTime > lastKnownExtraTime) {
                    val additionalTime = newExtraTime - lastKnownExtraTime
                    lastKnownExtraTime = newExtraTime
                    
                    // Add extra time to remaining time
                    timeRemainingMs += additionalTime
                    
                    // BUG-FIX: Reuse startTimer() instead of duplicating timer logic
                    startTimer()
                    
                    Toast.makeText(this@Challenge1Activity, "⏰ Admin added ${additionalTime / 60000} minutes!", Toast.LENGTH_LONG).show()
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
        var complete = 0
        if (selectedMcu.isNotEmpty()) complete++
        if (selectedComponents.isNotEmpty()) complete++
        // M-09: Only count code blocks as complete if user has actually interacted with them
        if (codeBlocks.isNotEmpty() && problemAnswers.getOrNull(currentProblemIndex)?.isComplete == true) complete++
        
        binding.tvProgress.text = "Progress: $complete/3 sections"
        binding.progressBarCompletion.progress = (complete * 33.33).toInt()
        binding.tvComponentCount.text = "${selectedComponents.size} selected"
        
        // Update overall progress
        val completedProblems = problemAnswers.count { it.isComplete }
        binding.tvOverallProgress?.text = "$completedProblems/${problems.size} problems done"
    }
    
    private fun confirmFinalSubmit() {
        // Save current answer first
        saveCurrentAnswer()
        
        // Check if all problems have answers
        val completedCount = problemAnswers.count { it.isComplete }
        
        val message = if (completedCount < problems.size) {
            "Warning: Only $completedCount of ${problems.size} problems are complete.\n\nSubmit anyway?"
        } else {
            "All ${problems.size} problems are complete!\n\nReady to submit?"
        }
        
        AlertDialog.Builder(this)
            .setTitle("📝 Final Submission")
            .setMessage(message)
            .setPositiveButton("Submit All") { _, _ -> showReviewScreen() }
            .setNegativeButton("Review Again", null)
            .show()
    }
    
    private fun showReviewScreen() {
        // BUG-M1: Enhanced review dialog showing code block status
        val reviewText = buildString {
            problems.forEachIndexed { i, problem ->
                val answer = problemAnswers.getOrNull(i)
                appendLine("━━━ Problem ${i + 1} ━━━")
                appendLine("MCU: ${answer?.selectedMcu?.ifEmpty { "Not selected" } ?: "Not selected"}")
                appendLine("Components: ${answer?.selectedComponents?.takeIf { it.isNotEmpty() }?.joinToString() ?: "None"}")
                val blockCount = answer?.codeBlockOrder?.size ?: 0
                val codeStatus = if (blockCount > 0) "$blockCount blocks arranged" else "Not arranged"
                appendLine("Code Blocks: $codeStatus")
                appendLine("Status: ${if (answer?.isComplete == true) "✅ Complete" else "⚠️ Incomplete"}")
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
        isSubmitting = true  // H-01: prevent anti-cheat from triggering during submission
        
        lifecycleScope.launch {
            // Build full submission
            val submissions = problems.mapIndexed { i, problem ->
                val answer = problemAnswers.getOrNull(i) ?: Challenge1ProblemAnswer()
                // Build codeBlocks as position→content map (e.g., {"1": "#include...", "2": "#define..."})
                val codeBlocksMap = answer.codeBlockOrder.mapIndexed { idx, content ->
                    "${idx + 1}" to content
                }.toMap()
                Challenge1Submission(
                    selectedMcu = answer.selectedMcu,
                    selectedComponents = answer.selectedComponents.toList(),
                    codeBlocks = codeBlocksMap,
                    submittedAt = System.currentTimeMillis()
                )
            }
            
            // BUG-H2: Use actual elapsed time for accurate time bonus (accounts for extra time)
            val actualTimeUsedMs = if (challengeStartTime > 0) {
                maxOf(0L, System.currentTimeMillis() - challengeStartTime)
            } else {
                maxOf(0L, ChallengeConstants.CHALLENGE_1_TIME_MS - timeRemainingMs)
            }
            val timeBonus = calculateTimeBonus(actualTimeUsedMs)
            
            // C-01 + R-02: Evaluate ALL problems with retry
            val evaluation = try {
                // Evaluate each problem, retrying individually on failure
                val perProblemResults = mutableListOf<EvaluationResult>()
                
                for (i in submissions.indices) {
                    val submission = submissions[i]
                    val problemStatement = problems.getOrNull(i)?.problemStatement ?: continue
                    
                    // Detect if user actually modified code blocks from default shuffled order
                    val originalCodeOrder = problems.getOrNull(i)?.codeBlocks?.map { it.content } ?: emptyList()
                    val codeBlockContentList = submission.codeBlocks.entries.sortedBy { it.key.toIntOrNull() ?: 0 }.map { it.value }
                    val codeModified = codeBlockContentList != originalCodeOrder && codeBlockContentList.isNotEmpty()
                    
                    var geminiResult: Result<EvaluationResult>? = null
                    val maxPerProblemRetries = 3
                    
                    for (retry in 1..maxPerProblemRetries) {
                        geminiResult = geminiService.evaluateChallenge1(
                            problemStatement = problemStatement,
                            selectedMcu = submission.selectedMcu,
                            selectedComponents = submission.selectedComponents,
                            codeBlocks = codeBlockContentList,
                            codeModified = codeModified
                        )
                        
                        if (geminiResult.isSuccess) {
                            perProblemResults.add(geminiResult.getOrThrow())
                            break
                        } else {
                            android.util.Log.w("Challenge1", 
                                "Gemini failed for problem ${i+1} (attempt $retry/$maxPerProblemRetries): ${geminiResult.exceptionOrNull()?.message}")
                            if (retry < maxPerProblemRetries) {
                                kotlinx.coroutines.delay(1000L * retry)
                            }
                        }
                    }
                }
                
                if (perProblemResults.isNotEmpty()) {
                    android.util.Log.i("Challenge1", "Evaluation successful for ${perProblemResults.size}/${submissions.size} problems")
                    averageEvaluationResults(perProblemResults, timeBonus)
                } else {
                    // All problems failed — cannot submit without evaluation
                    android.util.Log.e("Challenge1", "All Gemini evaluations failed after retries")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("Challenge1", "Gemini evaluation exception", e)
                null
            }
            
            if (evaluation == null) {
                showLoading(false)
                isSubmitting = false
                Toast.makeText(
                    this@Challenge1Activity,
                    "⚠️ Evaluation failed. Please check your internet connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            
            // Submit ALL problem submissions to Firebase (stored as p1, p2, p3 map)
            val success = eventService.submitChallenge1(rollNumber, submissions, evaluation, isTimeout)
            
            if (success) {
                navigateToRankingDashboard()
            } else {
                showLoading(false)
                isSubmitting = false
                Toast.makeText(this@Challenge1Activity, "Submission failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun calculateTimeBonus(timeUsedMs: Long): Int {
        // TimeBonus = (20 - timeUsed) / 20 × 100
        val timeUsedMinutes = timeUsedMs / 60000.0
        val bonus = ((20.0 - timeUsedMinutes) / 20.0 * 100.0).toInt()
        return maxOf(0, bonus)
    }
    

    /** R-02: Average multiple evaluation results into a single result */
    private fun averageEvaluationResults(results: List<EvaluationResult>, timeBonus: Int): EvaluationResult {
        val n = results.size
        // BUG-H1: Use double division to avoid losing precision via integer truncation
        val avgTotal = (results.sumOf { it.totalScore }.toDouble() / n).toInt()
        val finalScore = (avgTotal * 0.8 + timeBonus * 0.2).toInt()
        
        return EvaluationResult(
            attemptCompleteness = EvaluationCategory(
                (results.sumOf { it.attemptCompleteness.score }.toDouble() / n).toInt(), // N2-FIX: float division
                results.first().attemptCompleteness.maxScore,
                "Averaged over $n problems"
            ),
            syntaxCorrectness = EvaluationCategory(
                (results.sumOf { it.syntaxCorrectness.score }.toDouble() / n).toInt(),
                results.first().syntaxCorrectness.maxScore,
                "Averaged over $n problems"
            ),
            logicAccuracy = EvaluationCategory(
                (results.sumOf { it.logicAccuracy.score }.toDouble() / n).toInt(),
                results.first().logicAccuracy.maxScore,
                "Averaged over $n problems"
            ),
            criticalElements = EvaluationCategory(
                (results.sumOf { it.criticalElements.score }.toDouble() / n).toInt(),
                results.first().criticalElements.maxScore,
                "Averaged over $n problems"
            ),
            codeQuality = EvaluationCategory(
                (results.sumOf { it.codeQuality.score }.toDouble() / n).toInt(),
                results.first().codeQuality.maxScore,
                "Averaged over $n problems"
            ),
            errorCount = EvaluationCategory(
                (results.sumOf { it.errorCount.score }.toDouble() / n).toInt(),
                results.first().errorCount.maxScore,
                "Averaged over $n problems"
            ),
            totalScore = finalScore,
            maxScore = 100,
            percentage = finalScore.toDouble(),
            weightedScore = (finalScore * ChallengeConstants.CHALLENGE_1_WEIGHT).toInt(),
            feedback = results.mapIndexed { i, r -> "Problem ${i+1}: ${r.feedback}" }.joinToString(" | "),
            evaluatedAt = System.currentTimeMillis()
        )
    }
    
    private fun timeoutAndSubmit() {
        Toast.makeText(this, "Time's up! Auto-submitting all problems...", Toast.LENGTH_LONG).show()
        saveCurrentAnswer()
        
        lifecycleScope.launch {
            // CRITICAL: Save state to Firebase BEFORE timeout so user can resume with their selections
            val stateJson = serializeCurrentState()
            eventService.saveChallengeState(
                rollNumber = rollNumber,
                challengeNumber = 1,
                stateJson = stateJson,
                timeRemainingMs = 0L, // Timed out, no time remaining
                currentProblemIndex = currentProblemIndex
            )
            android.util.Log.i("Challenge1", "Saved challenge state to Firebase before timeout")
            
            eventService.timeoutChallenge(rollNumber, 1)
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
        if (!isFinishing && !isSubmitting) {
            handleAppBackground()
        }
    }
    
    private var isTerminated = false
    
    private fun handleAppBackground() {
        // C-07: Pause timer and record wall-clock time when going to background
        timer?.cancel()
        timerRunning = false  // BUG-FIX: mark timer as stopped
        backgroundEnteredRealtime = android.os.SystemClock.elapsedRealtime() // BUG-M4
        
        warningCount++
        lifecycleScope.launch {
            // ALWAYS save state when exiting app (for safety)
            val stateJson = serializeCurrentState()
            eventService.saveChallengeState(
                rollNumber = rollNumber,
                challengeNumber = 1,
                stateJson = stateJson,
                timeRemainingMs = timeRemainingMs,
                currentProblemIndex = currentProblemIndex
            )
            android.util.Log.i("Challenge1", "Saved state on exit (warningCount=$warningCount, timeRemaining=${timeRemainingMs}ms)")
            
            eventService.addWarning(rollNumber)
            if (warningCount >= 2) {
                // State is already saved above, now terminate
                eventService.terminateParticipant(rollNumber, ParticipantStatus.TERMINATION_EXIT_CHOICE)
                isTerminated = true
                android.util.Log.i("Challenge1", "User terminated after 2nd exit, state preserved for resume")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // BUG-FIX: Sync warningCount FIRST, then make all decisions INSIDE the coroutine
        lifecycleScope.launch {
            try {
                val snapshot = eventService.getParticipantRef(rollNumber)
                    .child("status/warningCount").get().await()
                val firebaseCount = snapshot.getValue(Int::class.java) ?: 0
                if (firebaseCount > warningCount) {
                    warningCount = firebaseCount
                    android.util.Log.i("Challenge1", "Synced warningCount from Firebase: $warningCount")
                }
            } catch (e: Exception) {
                android.util.Log.w("Challenge1", "Failed to sync warningCount", e)
            }
            
            // BUG-FIX: All decisions now happen AFTER warningCount sync completes
            if (isTerminated) {
                showTerminationDialog()
            } else if (warningCount >= 1 && !timerRunning) {
                // BUG-M4: Deduct actual background time from timer
                if (backgroundEnteredRealtime > 0) {
                    val backgroundDuration = android.os.SystemClock.elapsedRealtime() - backgroundEnteredRealtime
                    timeRemainingMs = maxOf(0L, timeRemainingMs - backgroundDuration)
                    backgroundEnteredRealtime = 0L
                    android.util.Log.i("Challenge1", "Deducted ${backgroundDuration}ms background time, remaining=${timeRemainingMs}ms")
                }
                showWarningDialog()
                timerStartRealtime = android.os.SystemClock.elapsedRealtime()
                if (timeRemainingMs <= 0) {
                    timeoutAndSubmit()
                } else {
                    startTimer()
                }
            } else if (warningCount == 0 && timerStartRealtime > 0 && !timerRunning) {
                // Returning from a non-warning background (e.g., split-screen dismissed)
                // BUG-M4: Also deduct background time for non-warning returns
                if (backgroundEnteredRealtime > 0) {
                    val backgroundDuration = android.os.SystemClock.elapsedRealtime() - backgroundEnteredRealtime
                    timeRemainingMs = maxOf(0L, timeRemainingMs - backgroundDuration)
                    backgroundEnteredRealtime = 0L
                }
                if (timeRemainingMs <= 0) {
                    timeoutAndSubmit()
                } else {
                    startTimer()
                }
            }
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
            .setMessage("You left the challenge! This is your FIRST and FINAL warning.\n\nIf you leave again, you will be TERMINATED.")
            .setPositiveButton("I Understand") { _, _ -> }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        // Clean up extra time listener
        extraTimeListener?.let { listener ->
            eventService.getParticipantRef(rollNumber).child("challenge1/extraTimeGrantedMs")
                .removeEventListener(listener)
        }
    }
    
    // H-06: Use OnBackPressedCallback instead of deprecated onBackPressed
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@Challenge1Activity)
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
}

// ============== DATA CLASSES ==============

// R-06: Removed duplicate Challenge1Problem, Challenge1ProblemAnswer, CodeBlock, CodeBlockCategory
// — these are already imported from services package

data class ComponentItem(
    val name: String,
    val imageKey: String,
    val type: ComponentType
)

enum class ComponentType { SENSOR, MODULE }

// ============== ADAPTERS ==============

class ComponentAdapter(
    private val items: List<ComponentItem>,
    private val onSelectionChanged: (ComponentItem, Boolean) -> Unit
) : RecyclerView.Adapter<ComponentAdapter.ViewHolder>() {
    
    private val selectedItems = mutableSetOf<String>()
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.cardComponent)
        val image: ImageView = itemView.findViewById(R.id.ivComponent)
        val name: TextView = itemView.findViewById(R.id.tvComponentName)
        val selectedIcon: ImageView = itemView.findViewById(R.id.ivSelected)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_component, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        
        // Load component image from assets/components directory
        val imageFileName = getImageFileName(item.imageKey, item.type)
        val assetPath = "components/${if (item.type == ComponentType.SENSOR) "sensors" else "modules"}/$imageFileName"
        
        try {
            // L-08: Use use{} to ensure InputStream is closed even if decodeStream throws
            val bitmap = holder.itemView.context.assets.open(assetPath).use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
            holder.image.setImageBitmap(bitmap)
            holder.image.clearColorFilter()
        } catch (e: Exception) {
            // Fallback to icon if image not found
            val iconRes = when (item.type) {
                ComponentType.SENSOR -> R.drawable.ic_sensors
                ComponentType.MODULE -> R.drawable.ic_microcontroller
            }
            holder.image.setImageResource(iconRes)
            holder.image.setColorFilter(
                if (selectedItems.contains(item.name)) 
                    holder.itemView.context.getColor(R.color.cyan_400)
                else 
                    holder.itemView.context.getColor(R.color.slate_400)
            )
        }
        
        val isSelected = selectedItems.contains(item.name)
        holder.selectedIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.card.strokeWidth = if (isSelected) 4 else 0
        holder.card.strokeColor = if (isSelected) 
            holder.itemView.context.getColor(R.color.cyan_500) 
        else 
            holder.itemView.context.getColor(android.R.color.transparent)
        
        holder.card.setOnClickListener {
            if (selectedItems.contains(item.name)) {
                selectedItems.remove(item.name)
                onSelectionChanged(item, false)
            } else {
                selectedItems.add(item.name)
                onSelectionChanged(item, true)
            }
            notifyItemChanged(position)
        }
    }
    
    override fun getItemCount() = items.size
    
    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    fun setSelectedItems(items: Collection<String>) {
        selectedItems.clear()
        selectedItems.addAll(items)
        notifyDataSetChanged()
    }
    
    private fun getImageFileName(imageKey: String, type: ComponentType): String {
        return when (imageKey) {
            // Sensors
            "dht11" -> "dht11.png"
            "mq2" -> "mq2_gas_sensor.png"
            "mq7" -> "mq7_co_sensor.png"
            "flame" -> "flame_sensor.png"
            "pir" -> "pir_motion_sensor.png"
            "ultrasonic" -> "ultrasonic_hcsr04.png"
            "ir" -> "ir_sensor.png"
            "ldr" -> "ldr_module.png"
            "soil" -> "soil_moisture_sensor.png"
            "rain" -> "rain_sensor.png"
            "sound" -> "sound_sensor.png"
            "vibration" -> "vibration_sensor.png"
            "water" -> "water_level_sensor.png"
            "touch" -> "touch_sensor.png"
            // Modules
            "relay" -> "relay_module.png"
            "lcd_i2c" -> "lcd_16x2_i2c.png"
            "buzzer" -> "buzzer.png"
            "servo" -> "servo_sg90.png"
            "dc_motor" -> "dc_motor_l298n.png"
            "rgb_led" -> "led_rgb.png"
            "keypad" -> "keypad_4x4.png"
            "bluetooth" -> "bluetooth_hc05.png"
            "gsm" -> "gsm_sim800l.png"
            "sd_card" -> "sd_card_module.png"
            "rtc" -> "rtc_ds3231.png"
            "button" -> "push_button.png"
            "fan" -> "dc_fan.png"
            "battery" -> "battery_holder.png"
            else -> "${imageKey}.png"
        }
    }
}

class CodeBlockAdapter(
    private val items: List<CodeBlock>
) : RecyclerView.Adapter<CodeBlockAdapter.ViewHolder>() {
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lineNumber: TextView = itemView.findViewById(R.id.tvLineNumber)
        val content: TextView = itemView.findViewById(R.id.tvCodeContent)
        val categoryIndicator: View = itemView.findViewById(R.id.viewCategory)
        val dragHandle: ImageView = itemView.findViewById(R.id.ivDragHandle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_code_block, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.lineNumber.text = (position + 1).toString()
        holder.content.text = item.content
        
        // Category colors (colorful as per spec)
        val color = when (item.category) {
            CodeBlockCategory.INCLUDE -> android.R.color.holo_purple      // Purple for includes
            CodeBlockCategory.DEFINE -> android.R.color.holo_blue_dark    // Blue for defines
            CodeBlockCategory.DECLARATION -> android.R.color.holo_green_dark // Green for declarations
            CodeBlockCategory.SETUP -> android.R.color.holo_orange_dark   // Orange for setup
            CodeBlockCategory.LOOP -> android.R.color.holo_red_dark       // Red for loop
            CodeBlockCategory.FUNCTION -> android.R.color.holo_blue_light // Blue for functions
        }
        holder.categoryIndicator.setBackgroundResource(color)
    }
    
    override fun getItemCount() = items.size
}
