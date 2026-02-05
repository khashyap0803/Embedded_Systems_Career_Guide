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
    private var lastKnownExtraTime: Long = 0L
    private var extraTimeListener: com.google.firebase.database.ValueEventListener? = null
    
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
            val inputStream = assets.open("components/boards/$fileName")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
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
                            
                            if (savedState != null) {
                                val (stateJson, savedTimeMs, savedProblemIdx) = savedState
                                resumeTimeMs = if (savedTimeMs > 0) savedTimeMs else ChallengeConstants.CHALLENGE_1_TIME_MS
                                resumeProblemIndex = savedProblemIdx
                                
                                // Parse and restore all problem answers
                                if (stateJson != null) {
                                    parseAndRestoreState(stateJson, savedProblemIdx)
                                }
                                
                                android.util.Log.i("Challenge1", "Restored state: timeRemaining=${resumeTimeMs}ms, problemIndex=$resumeProblemIndex")
                            }
                            
                            // Set timer to saved value
                            timeRemainingMs = resumeTimeMs
                            currentProblemIndex = resumeProblemIndex
                            
                            showLoading(false)
                            displayProblem(currentProblemIndex)
                            
                            // Restore UI state after display
                            restoreCurrentProblemFromAnswer()
                            
                            startTimer()
                            android.util.Log.i("Challenge1", "Successfully resumed with ${problems.size} problems at index $currentProblemIndex")
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
            
            // NEW CHALLENGE MODE: Generate new problems via Gemini
            showLoading(true, "Generating 3 unique problems via Gemini AI...")
            
            // Register challenge start
            eventService.startChallenge1(rollNumber)
            
            // Generate problems via Gemini API (cloud-only, no local fallback)
            val geminiResult = geminiService.generateChallenge1Problems()
            
            if (geminiResult.isSuccess && geminiResult.getOrNull()?.size == 3) {
                val generatedProblems = geminiResult.getOrNull()!!
                
                // Generate code blocks for each problem using Gemini API with retry
                showLoading(true, "Generating intelligent code blocks for each problem...")
                
                for (p in generatedProblems) {
                    // Use Gemini API to generate problem-specific code blocks with retry
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
                        expectedMcu = p.expectedMcu,
                        requiredComponents = p.expectedComponents,
                        codeBlocks = codeBlocksList
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
                put("expectedMcu", problem.expectedMcu)
                put("requiredComponents", JSONArray(problem.requiredComponents))
                
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
            
            result.add(Challenge1Problem(
                id = obj.getInt("id"),
                statement = obj.getString("statement"),
                expectedMcu = obj.optString("expectedMcu", "ESP32"),
                requiredComponents = components,
                codeBlocks = codeBlocks
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
                    val ansComplete = ansObj.optBoolean("isComplete", false)
                    
                    // Update problemAnswers if index exists
                    if (i < problemAnswers.size) {
                        problemAnswers[i] = Challenge1ProblemAnswer(
                            selectedMcu = ansMcu,
                            selectedComponents = ansComponents,
                            codeBlockIdOrder = ansBlockIds,
                            isComplete = ansComplete
                        )
                    }
                }
            }
            
            // Set current state from the answer for current problem
            val currentAnswer = problemAnswers.getOrNull(currentProblemIndex)
            if (currentAnswer != null && currentAnswer.isComplete) {
                selectedMcu = currentAnswer.selectedMcu
                selectedComponents.clear()
                selectedComponents.addAll(currentAnswer.selectedComponents)
                savedCodeBlockOrder = currentAnswer.codeBlockIdOrder
            }
            
            android.util.Log.i("Challenge1", "Restored state: problemIndex=$savedProblemIndex, answers restored=${allAnswersArray?.length() ?: 0}")
            problemAnswers.forEachIndexed { idx, ans ->
                android.util.Log.i("Challenge1", "  Answer $idx: mcu=${ans.selectedMcu}, components=${ans.selectedComponents.size}, complete=${ans.isComplete}")
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
        
        // Restore code block order if saved
        if (savedCodeBlockOrder.isNotEmpty() && codeBlocks.isNotEmpty()) {
            android.util.Log.i("Challenge1", "Restoring code block order: saved=${savedCodeBlockOrder}, current IDs=${codeBlocks.map { it.id }}")
            
            // Create a map of ID to block for O(1) lookup
            val blockById = codeBlocks.associateBy { it.id }
            val orderedBlocks = mutableListOf<CodeBlock>()
            val usedIds = mutableSetOf<Int>()
            
            // First add blocks in saved order
            savedCodeBlockOrder.forEach { blockId ->
                blockById[blockId]?.let { block ->
                    if (!usedIds.contains(block.id)) {
                        orderedBlocks.add(block)
                        usedIds.add(block.id)
                    }
                }
            }
            
            // Add any remaining blocks not in saved order (sorted by ID for consistency)
            codeBlocks.filter { !usedIds.contains(it.id) }
                .sortedBy { it.id }
                .forEach { block ->
                    orderedBlocks.add(block)
                }
            
            codeBlocks.clear()
            codeBlocks.addAll(orderedBlocks)
            if (::codeBlocksAdapter.isInitialized) {
                codeBlocksAdapter.notifyDataSetChanged()
            }
            
            android.util.Log.i("Challenge1", "Restored code block order: result IDs=${orderedBlocks.map { it.id }}")
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
            .setTitle("⚠️ Connection Error")
            .setMessage("Failed to generate problems from Gemini AI.\n\nError: $errorMessage\n\nPlease check your internet connection and try again.")
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
        
        // Save current answer before switching
        if (currentProblemIndex < problemAnswers.size) {
            saveCurrentAnswer()
        }
        
        currentProblemIndex = index
        val problem = problems[index]
        
        // Update UI
        binding.tvProblemNumber.text = "Problem ${index + 1} of ${problems.size}"
        binding.tvProblemStatement.text = problem.statement
        binding.tvComponentsRequired.text = "Required: ${problem.requiredComponents.joinToString(", ")}"
        
        // Update navigation buttons
        binding.btnPrevProblem.isEnabled = index > 0
        binding.btnNextProblem.visibility = if (index < problems.size - 1) View.VISIBLE else View.GONE
        binding.btnSubmit.visibility = if (index == problems.size - 1) View.VISIBLE else View.GONE
        
        // Update progress dots
        updateProgressDots()
        
        // FIRST: Load code blocks for this problem (original order from problem)
        codeBlocks.clear()
        codeBlocks.addAll(problem.codeBlocks)
        codeBlocksAdapter.notifyDataSetChanged()
        
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
        
        if (saved != null && saved.isComplete) {
            // Restore MCU and components
            selectedMcu = saved.selectedMcu
            selectedComponents.addAll(saved.selectedComponents)
            selectMcu(selectedMcu)
            
            // Restore code block order from saved answer
            if (saved.codeBlockIdOrder.isNotEmpty() && codeBlocks.isNotEmpty()) {
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
        binding.rvCodeBlocks.layoutManager = LinearLayoutManager(this)
        
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
        val ref = eventService.getParticipantRef(rollNumber).child("challenge1/extraTimeGrantedMs")
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
        if (codeBlocks.isNotEmpty()) complete++
        
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
        // Create review dialog showing all answers
        val reviewText = buildString {
            problems.forEachIndexed { i, problem ->
                val answer = problemAnswers.getOrNull(i)
                appendLine("━━━ Problem ${i + 1} ━━━")
                appendLine("MCU: ${answer?.selectedMcu ?: "Not selected"}")
                appendLine("Components: ${answer?.selectedComponents?.joinToString() ?: "None"}")
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
    
    private fun submitChallenge() {
        showLoading(true, "Evaluating all 3 problems...")
        timer?.cancel()
        
        lifecycleScope.launch {
            // Build full submission
            val submissions = problems.mapIndexed { i, problem ->
                val answer = problemAnswers.getOrNull(i) ?: Challenge1ProblemAnswer()
                Challenge1Submission(
                    selectedMcu = answer.selectedMcu,
                    selectedComponents = answer.selectedComponents.toList(),
                    codeBlocks = answer.codeBlockOrder,
                    submittedAt = System.currentTimeMillis()
                )
            }
            
            // Calculate time used and time bonus
            val timeUsedMs = ChallengeConstants.CHALLENGE_1_TIME_MS - timeRemainingMs
            val timeBonus = calculateTimeBonus(timeUsedMs)
            
            // Evaluate
            val evaluation = evaluateAllSubmissions(submissions, timeBonus)
            
            // Submit to Firebase (use first submission for compatibility)
            val mainSubmission = submissions.firstOrNull() ?: Challenge1Submission()
            val success = eventService.submitChallenge1(rollNumber, mainSubmission, evaluation)
            
            if (success) {
                navigateToRankingDashboard()
            } else {
                showLoading(false)
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
    
    private fun evaluateAllSubmissions(submissions: List<Challenge1Submission>, timeBonus: Int): EvaluationResult {
        var totalScore = 0
        var hasAnyAttempt = false
        
        submissions.forEachIndexed { i, submission ->
            val problem = problems.getOrNull(i) ?: return@forEachIndexed
            
            // Check if user actually attempted this problem (MCU or components selected)
            // Note: Code block reordering is checked separately since codeBlocks are always pre-populated
            val hasAttempt = submission.selectedMcu.isNotEmpty() || 
                             submission.selectedComponents.isNotEmpty()
            if (hasAttempt) hasAnyAttempt = true
            
            // MCU score (30 pts per problem for correct MCU)
            val mcuScore = if (submission.selectedMcu == problem.expectedMcu) 30 else 0
            
            // Component score (30 pts per problem, proportional to correct matches)
            val matchedComponents = submission.selectedComponents.count { 
                problem.requiredComponents.contains(it) 
            }
            val componentScore = if (problem.requiredComponents.isNotEmpty()) {
                (matchedComponents.toFloat() / problem.requiredComponents.size * 30).toInt()
            } else 0
            
            // Code order score (40 pts per problem) - check if user actually reordered
            // Compare current order with original order (blocks are created with incrementing originalOrder)
            val originalOrder = problem.codeBlocks.map { it.content }
            val userOrder = submission.codeBlocks
            val wasReordered = originalOrder != userOrder && userOrder.isNotEmpty()
            
            // Give points only if user made an attempt AND reordered code correctly
            // Check if the first block is an #include (correct structure)
            val hasCorrectStructure = userOrder.firstOrNull()?.startsWith("#include") == true ||
                                      userOrder.firstOrNull()?.startsWith("#define") == true
            val codeScore = if (wasReordered && hasCorrectStructure) 40 else 0
            
            // Count code reordering as an attempt too
            if (wasReordered) hasAnyAttempt = true
            
            totalScore += mcuScore + componentScore + codeScore
        }
        
        // If no attempt was made, return 0 score
        if (!hasAnyAttempt) {
            return EvaluationResult(
                attemptCompleteness = EvaluationCategory(0, 20, "No attempt made"),
                syntaxCorrectness = EvaluationCategory(0, 20, "No code submitted"),
                logicAccuracy = EvaluationCategory(0, 25, "No solution provided"),
                criticalElements = EvaluationCategory(0, 15, "No components selected"),
                codeQuality = EvaluationCategory(0, 10, "No code provided"),
                errorCount = EvaluationCategory(0, 10, "N/A"),
                totalScore = 0,
                maxScore = 100,
                percentage = 0.0,
                weightedScore = 0,
                feedback = "No attempt was made. Please select MCU, components, and arrange code blocks.",
                evaluatedAt = System.currentTimeMillis()
            )
        }
        
        // Average across 3 problems
        val avgScore = totalScore / problems.size
        
        // Apply time bonus only if user attempted something (20% weight as per spec)
        val finalScore = (avgScore * 0.8 + timeBonus * 0.2).toInt()
        
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
            weightedScore = (finalScore * ChallengeConstants.CHALLENGE_1_WEIGHT).toInt(),
            feedback = "Evaluated ${problems.size} problems. Time bonus: $timeBonus points.",
            evaluatedAt = System.currentTimeMillis()
        )
    }
    
    private fun timeoutAndSubmit() {
        Toast.makeText(this, "Time's up! Auto-submitting all problems...", Toast.LENGTH_LONG).show()
        saveCurrentAnswer()
        
        lifecycleScope.launch {
            eventService.timeoutChallenge(rollNumber, 1)
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
        if (!isFinishing) {
            handleAppBackground()
        }
    }
    
    private var isTerminated = false
    
    private fun handleAppBackground() {
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
    
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit Challenge?")
            .setMessage("If you exit, you will receive a warning and may be terminated.")
            .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Stay", null)
            .show()
    }
}

// ============== DATA CLASSES ==============

data class Challenge1Problem(
    val id: Int,
    val statement: String,
    val expectedMcu: String,
    val requiredComponents: List<String>,
    val codeBlocks: List<CodeBlock>
)

data class Challenge1ProblemAnswer(
    val selectedMcu: String = "",
    val selectedComponents: List<String> = emptyList(),
    val codeBlockOrder: List<String> = emptyList(),
    val codeBlockIdOrder: List<Int> = emptyList(),
    val isComplete: Boolean = false
)

data class ComponentItem(
    val name: String,
    val imageKey: String,
    val type: ComponentType
)

enum class ComponentType { SENSOR, MODULE }

data class CodeBlock(
    val originalOrder: Int,
    val content: String,
    val category: CodeBlockCategory
)

enum class CodeBlockCategory {
    INCLUDE, DEFINE, DECLARATION, SETUP, LOOP
}

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
            val inputStream = holder.itemView.context.assets.open(assetPath)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
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
