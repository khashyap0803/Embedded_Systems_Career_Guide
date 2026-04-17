package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ChallengeService - AI-Powered Challenge Generation and Evaluation
 * Powered by local Ollama LLM via Ngrok
 * 
 * Provides:
 * - Dynamic problem generation for all 3 challenges
 * - Multi-parameter evaluation (6 criteria as per spec)
 * - Component-aware problem statements
 * - Code analysis and scoring
 */
class GeminiChallengeService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiChallengeService"
        
        // Available components for Challenge 1
        val AVAILABLE_MCUS = listOf("Arduino UNO", "ESP32")
        val AVAILABLE_SENSORS = listOf(
            "MQ-2 Gas Sensor", "MQ-7 CO Sensor", "Flame Sensor", "DHT11",
            "PIR Motion Sensor", "Ultrasonic HC-SR04", "IR Sensor", "LDR Module",
            "Soil Moisture Sensor", "Rain Sensor", "Sound Sensor", "Vibration Sensor",
            "Water Level Sensor", "Touch Sensor"
        )
        val AVAILABLE_MODULES = listOf(
            "Buzzer", "LED Module (RGB)", "Relay Module", "LCD 16x2 I2C",
            "Servo Motor", "DC Motor + L298N", "GSM Module", "Bluetooth HC-05",
            "SD Card Module", "RTC Module", "Push Button", "Keypad 4x4",
            "DC Fan", "Battery Holder"
        )
        
        // Singleton instance
        @Volatile
        private var instance: GeminiChallengeService? = null
        
        fun getInstance(context: Context): GeminiChallengeService {
            return instance ?: synchronized(this) {
                instance ?: GeminiChallengeService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val client = NetworkModule.longTimeoutClient
    private val gson = Gson()

    // ========== CHALLENGE 1: PROBLEM GENERATION ==========
    
    /**
     * Generate 3 unique hardware design problems for Challenge 1
     * Returns Challenge1Problem objects for internal use
     */
    suspend fun generateChallenge1Problems(): Result<List<Challenge1Problem>> = withContext(Dispatchers.IO) {
        // Randomization: unique seed + shuffled components + random categories
        val seed = java.util.UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val allCategories = listOf(
            "Home Safety", "Industrial Monitoring", "Agriculture", "Smart Home",
            "Health & Wearables", "Vehicle/Transport", "Environment Monitoring",
            "Security Systems", "Energy Management", "Robotics",
            "Weather Station", "Animal/Pet Care"
        )
        val pickedCategories = allCategories.shuffled().take(3)
        val shuffledSensors = AVAILABLE_SENSORS.shuffled()
        val shuffledModules = AVAILABLE_MODULES.shuffled()

    val prompt = """
[SESSION: $seed | TS: $timestamp]
Generate 3 UNIQUE and CREATIVE embedded systems problems for a hardware selection challenge.
Each problem MUST be DIFFERENT from common examples. Be ORIGINAL and INVENTIVE.

MANDATORY CATEGORIES (use exactly these 3):
1. ${pickedCategories[0]}
2. ${pickedCategories[1]}
3. ${pickedCategories[2]}

AVAILABLE COMPONENTS:
MCU Boards: ${AVAILABLE_MCUS.shuffled().joinToString(", ")}
Sensors: ${shuffledSensors.joinToString(", ")}
Modules: ${shuffledModules.joinToString(", ")}

STRICT REQUIREMENTS:
- MUST use exactly 1 MCU + 1-2 sensors + 1-2 modules (MAX 4 components including MCU)
- Problems MUST be VERY SIMPLE and solvable by beginners
- Use SIMPLE logic: read sensor → do action
- Real-world practical but EASY scenarios
- DO NOT repeat common examples like "gas detector + buzzer" or "temperature + LCD"
- Think of UNCOMMON, CREATIVE scenarios that beginners can still solve

Return ONLY valid JSON array with exactly 3 problems:
[
  {
"problemStatement": "A creative, unique problem statement",
"expectedMcu": "Arduino UNO or ESP32",
"expectedComponents": ["Sensor1", "Module1"],
"difficulty": "Easy",
"category": "${pickedCategories[0]}"
  },
  {
"problemStatement": "...",
"expectedMcu": "...",
"expectedComponents": [...],
"difficulty": "Easy",
"category": "${pickedCategories[1]}"
  },
  {
"problemStatement": "...",
"expectedMcu": "...",
"expectedComponents": [...],
"difficulty": "Easy",
"category": "${pickedCategories[2]}"
  }
]
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt, temperature = 1.0)
            val jsonStr = extractJsonArray(response)
            val type = object : TypeToken<List<Challenge1ProblemResponse>>() {}.type
            val problems: List<Challenge1ProblemResponse> = gson.fromJson(jsonStr, type)
            
            val result = problems.mapIndexed { index, p ->
                Challenge1Problem(
                    id = index + 1,
                    statement = "🎯 ${p.category}: ${p.problemStatement}",
                    requiredComponents = p.expectedComponents,
                    codeBlocks = emptyList(), // Populated by generateCodeBlocksForChallenge1
                    problemStatement = p.problemStatement,
                    expectedMcu = p.expectedMcu,
                    expectedComponents = p.expectedComponents,
                    difficulty = p.difficulty,
                    category = p.category
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Challenge 1 problems", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate problem-specific code blocks using Gemini API
     * Returns shuffled CodeBlock list with correct categories (INCLUDE, DEFINE, SETUP, LOOP)
     */
    suspend fun generateCodeBlocksForChallenge1(
        problemStatement: String,
        mcu: String,
        components: List<String>
    ): Result<List<CodeBlock>> = withContext(Dispatchers.IO) {
        val prompt = """
Generate Arduino/ESP32 code blocks for the following embedded systems problem.

PROBLEM STATEMENT: $problemStatement
MCU: $mcu
COMPONENTS: ${components.joinToString(", ")}

REQUIREMENTS:
1. Generate REALISTIC, CORRECT code specifically for this problem
2. Use CORRECT pin modes: INPUT for sensors, OUTPUT for actuators/modules
3. Use CORRECT reading methods: analogRead() for analog sensors, digitalRead() for digital
4. Use CORRECT libraries for each component (e.g., DHT.h for DHT11, LiquidCrystal_I2C.h for LCD)
5. Create meaningful logic based on the problem (not generic thresholds)
6. Each code line should be a separate block in the JSON array
7. DO NOT include any comments (// or /* */) as separate code blocks
8. DO NOT generate comment-only lines - only generate actual code statements

Return ONLY valid JSON array with code blocks:
[
  {"content": "#include <DHT.h>", "category": "INCLUDE"},
  {"content": "#define DHT_PIN 2", "category": "DEFINE"},
  {"content": "DHT dht(DHT_PIN, DHT11);", "category": "DEFINE"},
  {"content": "void setup() {", "category": "SETUP"},
  {"content": "  Serial.begin(115200);", "category": "SETUP"},
  {"content": "  dht.begin();", "category": "SETUP"},
  {"content": "  pinMode(BUZZER_PIN, OUTPUT);", "category": "SETUP"},
  {"content": "}", "category": "SETUP"},
  {"content": "void loop() {", "category": "LOOP"},
  {"content": "  float temp = dht.readTemperature();", "category": "LOOP"},
  {"content": "  if (temp > 30) {", "category": "LOOP"},
  {"content": "    digitalWrite(BUZZER_PIN, HIGH);", "category": "LOOP"},
  {"content": "  }", "category": "LOOP"},
  {"content": "  delay(1000);", "category": "LOOP"},
  {"content": "}", "category": "LOOP"}
]

CATEGORIES:
- INCLUDE: #include statements
- DEFINE: #define, variable declarations, object instantiations
- SETUP: Everything inside void setup() including the function signature
- LOOP: Everything inside void loop() including the function signature

IMPORTANT: Generate 15-25 code blocks total. Each block should be one logical line of actual code.
DO NOT include comments - students should order pure code blocks.
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt, temperature = 1.0)
            val jsonStr = extractJsonArray(response)
            val type = object : TypeToken<List<CodeBlockResponse>>() {}.type
            val blocks: List<CodeBlockResponse?> = gson.fromJson(jsonStr, type)
            
            val result = blocks
                .filterNotNull()
                // Filter out null/empty content and comment-only lines
                .filter { block ->
                    val content = block.content ?: return@filter false
                    val trimmed = content.trim()
                    !trimmed.startsWith("//") && 
                    !trimmed.startsWith("/*") && 
                    !trimmed.startsWith("*") &&
                    trimmed.isNotEmpty()
                }
                .mapIndexed { index, b ->
                    val category = when ((b.category ?: "LOOP").uppercase()) {
                        "INCLUDE" -> CodeBlockCategory.INCLUDE
                        "DEFINE" -> CodeBlockCategory.DEFINE
                        "SETUP" -> CodeBlockCategory.SETUP
                        "LOOP" -> CodeBlockCategory.LOOP
                        else -> CodeBlockCategory.LOOP
                    }
                    CodeBlock(
                        id = index + 1,
                        content = b.content ?: "",
                        category = category
                    )
                }.shuffled() // Shuffle for the challenge
            
            Log.i(TAG, "Generated ${result.size} code blocks for problem: $problemStatement")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate code blocks", e)
            Result.failure(e)
        }
    }

    // Helper data class for code block parsing
    private data class CodeBlockResponse(
        val content: String? = "",
        val category: String? = "LOOP"
    )

    // ========== CHALLENGE 2: CODE COMPLETION QUESTIONS ==========
    
    /**
     * Generate 3 code completion questions for Challenge 2
     * Returns Challenge2QuestionInternal for use by Challenge2Activity
     */
    suspend fun generateChallenge2Questions(): Result<List<Challenge2QuestionInternal>> = withContext(Dispatchers.IO) {
        // Randomization: unique seed + random component/topic picks
        val seed = java.util.UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val allComponents = listOf(
            "LED", "Relay", "PIR", "DHT11", "Servo", "Buzzer",
            "Ultrasonic HC-SR04", "LDR", "IR Sensor", "Soil Moisture Sensor",
            "LCD I2C", "DC Motor", "MQ-2 Gas Sensor", "Rain Sensor"
        )
        val pickedComponents = allComponents.shuffled().take(6)
        val allTopics = listOf(
            "home automation", "security alert", "plant care", "weather monitoring",
            "motor control", "distance measurement", "light control", "fire detection",
            "water level monitoring", "door lock system", "parking sensor", "fan speed control"
        )
        val pickedTopics = allTopics.shuffled().take(3)

        val prompt = """
[SESSION: $seed | TS: $timestamp]
Generate 3 UNIQUE code completion problems for embedded systems. Each MUST be DIFFERENT and CREATIVE.
Keep ALL code SHORT (under 8 lines each).

MANDATORY TOPICS (one per question, use exactly these):
1. ${pickedTopics[0]}
2. ${pickedTopics[1]}
3. ${pickedTopics[2]}

USE THESE COMPONENTS: ${pickedComponents.joinToString(", ")}

FORMAT (JSON only, no markdown):
[
  {
    "scenario": "🏠 Title (short)",
    "description": "One sentence description with components used",
    "preCode": "Short setup code with // FILL IN comment",
    "postCode": "Closing brackets/delay only",
    "missingLineCount": 2,
    "hints": ["Short hint 1", "Short hint 2"],
    "correctAnswer": "Only the missing lines, 2-3 lines max"
  }
]

RULES:
- Use ESP32 or Arduino
- preCode: MAX 5 lines, postCode: MAX 2 lines, correctAnswer: MAX 3 lines
- DO NOT reuse the same scenario across questions
- No lengthy explanations. JSON only.
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt, temperature = 1.0)
            val jsonStr = extractJsonArray(response)
            val type = object : TypeToken<List<Challenge2QuestionResponse>>() {}.type
            val questions: List<Challenge2QuestionResponse> = gson.fromJson(jsonStr, type)
            
            val result = questions.mapIndexed { index, q ->
                Challenge2QuestionInternal(
                    id = index + 1,
                    scenario = q.scenario,
                    description = q.description,
                    preCode = q.preCode.replace("\\n", "\n"),
                    postCode = q.postCode.replace("\\n", "\n"),
                    missingLineCount = q.missingLineCount,
                    hints = q.hints,
                    correctAnswer = q.correctAnswer.replace("\\n", "\n"),
                    userAnswer = null
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Challenge 2 questions", e)
            Result.failure(e)
        }
    }

    // ========== CHALLENGE 3: COMPLETE CODE QUESTIONS ==========
    
    /**
     * Generate 3 complete code writing questions for Challenge 3
     * Returns Challenge3QuestionInternal for use by Challenge3Activity
     */
    suspend fun generateChallenge3Questions(): Result<List<Challenge3QuestionInternal>> = withContext(Dispatchers.IO) {
        // Randomization: unique seed + random domain picks
        val seed = java.util.UUID.randomUUID().toString().take(8)
        val timestamp = System.currentTimeMillis()
        val allDomains = listOf(
            "Industrial Safety", "Smart Agriculture", "Home Automation",
            "Environmental Monitoring", "Healthcare Devices", "Vehicle Systems",
            "Building Management", "Warehouse Automation", "Energy Systems",
            "Water Management", "Smart Parking", "Animal Husbandry"
        )
        val pickedDomains = allDomains.shuffled().take(3)
        val allComponentSets = listOf(
            "MQ-2 Gas Sensor, MQ-7 CO Sensor, Buzzer, LCD 16x2 I2C, GSM Module",
            "DHT11, Soil Moisture Sensor, Relay Module, LCD 16x2 I2C, DC Fan",
            "Ultrasonic HC-SR04, PIR Motion Sensor, Servo Motor, Buzzer, LED Module (RGB)",
            "LDR Module, Rain Sensor, DC Motor + L298N, LCD 16x2 I2C, Buzzer",
            "Flame Sensor, MQ-2 Gas Sensor, Buzzer, GSM Module, Relay Module",
            "IR Sensor, Servo Motor, Keypad 4x4, LCD 16x2 I2C, Buzzer",
            "DHT11, Water Level Sensor, Relay Module, LCD 16x2 I2C, Bluetooth HC-05",
            "Vibration Sensor, Sound Sensor, LED Module (RGB), Buzzer, SD Card Module",
            "Soil Moisture Sensor, Rain Sensor, RTC Module, Relay Module, LCD 16x2 I2C",
            "Touch Sensor, Servo Motor, Buzzer, LED Module (RGB), Bluetooth HC-05"
        )
        val pickedSets = allComponentSets.shuffled().take(3)

        val prompt = """
[SESSION: $seed | TS: $timestamp]
Generate 3 UNIQUE HIGH difficulty embedded systems problems requiring COMPLETE code from scratch.
Each problem MUST be CREATIVE and DIFFERENT. DO NOT repeat common examples.

MANDATORY DOMAINS (one per question):
1. ${pickedDomains[0]} — use components: ${pickedSets[0]}
2. ${pickedDomains[1]} — use components: ${pickedSets[1]}
3. ${pickedDomains[2]} — use components: ${pickedSets[2]}

MCU: Use ESP32 or Arduino UNO

QUESTION FORMAT:
1. Detailed problem statement with all components listed
2. Clear functional requirements (4-6 bullet points)
3. Expected code structure: includes, definitions, setup(), loop()

Return ONLY valid JSON array:
[
  {
    "scenario": "🏭 CREATIVE TITLE HERE",
    "description": "Detailed problem using the assigned components",
    "requirements": [
      "Requirement 1",
      "Requirement 2",
      "Requirement 3",
      "Requirement 4"
    ],
    "hints": ["Hint 1", "Hint 2"],
    "expectedElements": ["#include", "setup()", "loop()", "analogRead", "digitalWrite", "if"]
  },
  {...},
  {...}
]
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt, temperature = 1.0)
            val jsonStr = extractJsonArray(response)
            val type = object : TypeToken<List<Challenge3QuestionResponse>>() {}.type
            val questions: List<Challenge3QuestionResponse> = gson.fromJson(jsonStr, type)
            
            val result = questions.mapIndexed { index, q ->
                Challenge3QuestionInternal(
                    id = index + 1,
                    scenario = q.scenario,
                    description = q.description,
                    requirements = q.requirements,
                    hints = q.hints,
                    expectedElements = q.expectedElements,
                    userCode = null
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Challenge 3 questions", e)
            Result.failure(e)
        }
    }

    // ========== MULTI-PARAMETER EVALUATION ==========
    
    /**
     * Evaluate Challenge 1 submission with 6-parameter scoring.
     * Retries up to 3 times on JSON parse failures (truncated/malformed responses).
     */
    suspend fun evaluateChallenge1(
        problemStatement: String,
        selectedMcu: String,
        selectedComponents: List<String>,
        codeBlocks: List<String>,
        codeModified: Boolean = false
    ): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        val prompt = """
You are a STRICT embedded systems examination evaluator. Score this solution using ONLY the rubric below.
Apply DEDUCTIONS from maximum. Do NOT give benefit of doubt. Be harsh and accurate like HackerRank.

═══ PROBLEM STATEMENT ═══
$problemStatement

═══ STUDENT'S SUBMISSION ═══
MCU Selected: "${selectedMcu.ifEmpty { "NONE (not selected)" }}"
Components Selected: ${if (selectedComponents.isEmpty()) "NONE (not selected)" else selectedComponents.joinToString(", ")}
NOTE: Accept these component name aliases as equivalent (left=student UI name, right=full name): Servo SG90=Servo Motor, DC Motor=DC Motor + L298N, LCD I2C=LCD 16x2 I2C, RGB LED=LED Module (RGB), MQ-2 Gas=MQ-2 Gas Sensor, MQ-7 CO=MQ-7 CO Sensor, Bluetooth=Bluetooth HC-05, GSM SIM800L=GSM Module, RTC DS3231=RTC Module, DC Fan=DC Fan, Push Button=Push Button, PIR Motion=PIR Motion Sensor, Ultrasonic=Ultrasonic HC-SR04, LDR=LDR Module, Flame=Flame Sensor, Rain=Rain Sensor, Soil Moisture=Soil Moisture Sensor, Sound=Sound Sensor, Vibration=Vibration Sensor, Water Level=Water Level Sensor, Touch=Touch Sensor, Relay=Relay Module, SD Card=SD Card Module
Code Blocks Modified by Student: ${if (codeModified) "YES" else "NO — code was NOT touched/reordered by student"}
Code Blocks (in student's order):
${if (!codeModified) "DEFAULT SHUFFLED ORDER (student did NOT modify code)" else codeBlocks.mapIndexed { i, block -> "${i+1}. $block" }.joinToString("\n")}

═══ STRICT SCORING RUBRIC (100 points total) ═══

CRITICAL RULES:
- If Code Blocks Modified = "NO", then syntaxCorrectness=0, codeQuality=0, errorCount=0
- If MCU is "NONE", attemptCompleteness loses 6 pts, logicAccuracy loses 10 pts
- If Components is "NONE", criticalElements=0, attemptCompleteness loses 6 pts
- If ALL three are empty/unmodified, ALL scores = 0

1. attemptCompleteness (max 20):
   - MCU selected: +6 pts (0 if empty/none)
   - At least 1 component selected: +6 pts (0 if empty)
   - Code blocks reordered by student: +8 pts (0 if NOT modified)

2. syntaxCorrectness (max 20):
   - ONLY scored if student modified code blocks
   - Code blocks follow correct structure (#include → #define → setup() → loop()): +20
   - Deduct 5 pts for each block out of structural order
   - If code NOT modified: 0

3. logicAccuracy (max 25):
   - Correct MCU for the problem: +10 (wrong MCU: 0, no MCU: 0)
   - Code logic correctly addresses requirements: +15 (0 if code not modified)
   - Deduct 5 pts per major logic gap

4. criticalElements (max 15):
   - Each REQUIRED component present: +(15 / total_required_count) per match
   - Each WRONG/unnecessary component: -2
   - If nothing selected: 0

5. codeQuality (max 10):
   - ONLY scored if student modified code blocks
   - If code NOT modified: 0

6. errorCount (max 10):
   - ONLY scored if student modified code blocks
   - If code NOT modified: 0
   - 0 errors: 10, 1: 7, 2: 5, 3: 3, 4+: 0

═══ MANDATORY RULES ═══
- totalScore MUST equal attemptCompleteness + syntaxCorrectness + logicAccuracy + criticalElements + codeQuality + errorCount
- Scores can NEVER exceed their maxScore
- KEEP EACH "details" UNDER 20 WORDS. Be concise.
- "feedback" must be under 30 words.

Return ONLY this JSON (no markdown, no explanation):
{
  "attemptCompleteness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "syntaxCorrectness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "logicAccuracy": {"score": 0, "maxScore": 25, "details": "brief reason"},
  "criticalElements": {"score": 0, "maxScore": 15, "details": "brief reason"},
  "codeQuality": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "errorCount": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "totalScore": 0,
  "feedback": "brief summary"
}
""".trimIndent()

        val maxParseRetries = 3
        var lastException: Exception? = null

        for (parseAttempt in 1..maxParseRetries) {
            try {
                Log.d(TAG, "evaluateChallenge1 attempt $parseAttempt/$maxParseRetries")
                val response = callGeminiAPI(prompt, temperature = 0.3)
                val jsonStr = extractJsonObject(response)
                val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)

                // Validate that essential fields were parsed (guards against null/zero from malformed JSON)
                requireNotNull(evalResponse.attemptCompleteness) { "Missing attemptCompleteness" }
                requireNotNull(evalResponse.syntaxCorrectness) { "Missing syntaxCorrectness" }
                requireNotNull(evalResponse.logicAccuracy) { "Missing logicAccuracy" }
                requireNotNull(evalResponse.criticalElements) { "Missing criticalElements" }
                requireNotNull(evalResponse.codeQuality) { "Missing codeQuality" }
                requireNotNull(evalResponse.errorCount) { "Missing errorCount" }

                // Step 1: Clamp each category to valid range
                var ac = evalResponse.attemptCompleteness.score.coerceIn(0, 20)
                var sc = evalResponse.syntaxCorrectness.score.coerceIn(0, 20)
                var la = evalResponse.logicAccuracy.score.coerceIn(0, 25)
                var ce = evalResponse.criticalElements.score.coerceIn(0, 15)
                var cq = evalResponse.codeQuality.score.coerceIn(0, 10)
                var ec = evalResponse.errorCount.score.coerceIn(0, 10)

                // Step 2: Server-side deterministic enforcement (overrides AI inconsistency)
                // Rule 1: Code NOT modified → force code-related scores to 0
                if (!codeModified) {
                    sc = 0  // syntaxCorrectness: can't evaluate unmodified code
                    cq = 0  // codeQuality: no code effort
                    ec = 0  // errorCount: no attempt
                    ac = ac.coerceAtMost(12) // attemptCompleteness: max MCU(6) + components(6)
                    la = la.coerceAtMost(10) // logicAccuracy: only MCU selection credit
                    Log.d(TAG, "Server-side: code not modified → sc=0, cq=0, ec=0, ac≤12, la≤10")
                }

                // Rule 2: No MCU selected → cap MCU-dependent scores
                if (selectedMcu.isEmpty()) {
                    ac = ac.coerceAtMost(14)  // lose 6 for MCU
                    la = la.coerceAtMost(15)  // lose 10 for MCU
                    Log.d(TAG, "Server-side: no MCU → ac≤14, la≤15")
                }

                // Rule 3: No components selected → force component score to 0
                if (selectedComponents.isEmpty()) {
                    ce = 0
                    ac = ac.coerceAtMost(14)  // lose 6 for components
                    Log.d(TAG, "Server-side: no components → ce=0, ac≤14")
                }

                // Rule 4: Nothing at all → force everything to 0
                if (selectedMcu.isEmpty() && selectedComponents.isEmpty() && !codeModified) {
                    ac = 0; sc = 0; la = 0; ce = 0; cq = 0; ec = 0
                    Log.d(TAG, "Server-side: completely unattempted → all scores = 0")
                }

                val validatedTotal = ac + sc + la + ce + cq + ec
                Log.i(TAG, "Server-validated scores: ac=$ac sc=$sc la=$la ce=$ce cq=$cq ec=$ec total=$validatedTotal")

                val result = EvaluationResult(
                    attemptCompleteness = EvaluationCategory(ac, 20, evalResponse.attemptCompleteness.details),
                    syntaxCorrectness = EvaluationCategory(sc, 20, evalResponse.syntaxCorrectness.details),
                    logicAccuracy = EvaluationCategory(la, 25, evalResponse.logicAccuracy.details),
                    criticalElements = EvaluationCategory(ce, 15, evalResponse.criticalElements.details),
                    codeQuality = EvaluationCategory(cq, 10, evalResponse.codeQuality.details),
                    errorCount = EvaluationCategory(ec, 10, evalResponse.errorCount.details),
                    totalScore = validatedTotal,
                    maxScore = 100,
                    percentage = validatedTotal.toDouble(),
                    weightedScore = (validatedTotal * ChallengeConstants.CHALLENGE_1_WEIGHT).toInt(),
                    feedback = evalResponse.feedback,
                    evaluatedAt = System.currentTimeMillis()
                )
                Log.i(TAG, "evaluateChallenge1 succeeded on attempt $parseAttempt")
                return@withContext Result.success(result)
            } catch (e: ClientException) {
                // 4xx errors (bad API key, etc.) are not retryable
                Log.e(TAG, "Non-retryable client error in evaluateChallenge1", e)
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "evaluateChallenge1 parse attempt $parseAttempt failed: ${e.message}")
                if (parseAttempt < maxParseRetries) {
                    // BUG-L3: Use coroutine delay instead of blocking Thread.sleep
                    kotlinx.coroutines.delay(1000L * parseAttempt)
                }
            }
        }

        Log.e(TAG, "evaluateChallenge1 failed after $maxParseRetries attempts", lastException)
        Result.failure(lastException ?: Exception("Evaluation failed after $maxParseRetries retries"))
    }

    /**
     * Evaluate Challenge 2 code completion submission.
     * Retries up to 3 times on JSON parse failures.
     */
    suspend fun evaluateChallenge2(
        questions: List<Challenge2QuestionInternal>
    ): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        val questionsText = questions.mapIndexed { i, q ->
            """
Question ${i+1}: ${q.scenario}
Expected Answer Pattern: ${q.correctAnswer}
User's Answer: ${q.userAnswer ?: "NOT ANSWERED"}
"""
        }.joinToString("\n---\n")

        val prompt = """
You are a STRICT code examination evaluator. Score these code completion answers using ONLY the rubric below.
Apply DEDUCTIONS from maximum. Do NOT give benefit of doubt. Be harsh and accurate like HackerRank.

═══ QUESTIONS AND ANSWERS ═══
$questionsText

═══ STRICT SCORING RUBRIC (100 points total across 3 questions) ═══

CRITICAL RULE: If a question's User's Answer is "NOT ANSWERED" or empty/blank, that question scores 0 in ALL categories. No partial credit for blank answers.

1. attemptCompleteness (max 20):
   - 3/3 questions answered: 20
   - 2/3 questions answered: 13
   - 1/3 questions answered: 7
   - 0/3 questions answered: 0
   - "Answered" means non-empty, non-trivial code (not just whitespace or comments)

2. syntaxCorrectness (max 20):
   - Per answered question: check for valid C/C++ syntax
   - Start at 20, deduct proportionally:
   - Missing semicolons: -2 each
   - Unmatched brackets/braces: -3 each
   - Invalid function calls: -3 each
   - Unanswered questions contribute 0 (already penalized in attemptCompleteness)

3. logicAccuracy (max 25):
   - Per answered question: compare user's answer to expected answer pattern
   - Correct logic matching expected behavior: proportional to questions answered correctly
   - 3/3 correct logic: 25, 2/3: 17, 1/3: 8, 0: 0
   - Partially correct logic: reduce by 50% for that question's share

4. criticalElements (max 15):
   - Check each answered question for required operations (digitalWrite, analogRead, if/else, loops, etc.)
   - All critical operations present across answered questions: 15
   - Deduct (15 / total_critical_ops) per missing operation
   - Unanswered: 0

5. codeQuality (max 10):
   - Proper variable types and usage: +3
   - Efficient structure (no unnecessary code): +3
   - Follows C/C++ best practices: +2
   - Consistent style: +2
   - Scale proportionally to questions actually answered
   - If 0 answered: 0

6. errorCount (max 10):
   - Count total syntax + logic errors across ALL answered questions
   - 0 errors: 10, 1: 7, 2: 5, 3: 3, 4+: 0
   - If 0 answered: 0

═══ MANDATORY RULES ═══
- totalScore MUST equal attemptCompleteness + syntaxCorrectness + logicAccuracy + criticalElements + codeQuality + errorCount
- Scores can NEVER exceed their maxScore
- Blank/empty answers = ZERO credit for that question
- KEEP EACH "details" UNDER 20 WORDS. Be concise.
- "feedback" must be under 30 words.

Return ONLY this JSON (no markdown, no explanation):
{
  "attemptCompleteness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "syntaxCorrectness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "logicAccuracy": {"score": 0, "maxScore": 25, "details": "brief reason"},
  "criticalElements": {"score": 0, "maxScore": 15, "details": "brief reason"},
  "codeQuality": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "errorCount": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "totalScore": 0,
  "feedback": "brief summary"
}
""".trimIndent()

        val maxParseRetries = 3
        var lastException: Exception? = null

        for (parseAttempt in 1..maxParseRetries) {
            try {
                Log.d(TAG, "evaluateChallenge2 attempt $parseAttempt/$maxParseRetries")
                val response = callGeminiAPI(prompt, temperature = 0.3)
                val jsonStr = extractJsonObject(response)
                val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)

                requireNotNull(evalResponse.attemptCompleteness) { "Missing attemptCompleteness" }
                requireNotNull(evalResponse.syntaxCorrectness) { "Missing syntaxCorrectness" }
                requireNotNull(evalResponse.logicAccuracy) { "Missing logicAccuracy" }

                // Step 1: Clamp each category to valid range
                var ac = evalResponse.attemptCompleteness.score.coerceIn(0, 20)
                var sc = evalResponse.syntaxCorrectness.score.coerceIn(0, 20)
                var la = evalResponse.logicAccuracy.score.coerceIn(0, 25)
                var ce = evalResponse.criticalElements.score.coerceIn(0, 15)
                var cq = evalResponse.codeQuality.score.coerceIn(0, 10)
                var ec = evalResponse.errorCount.score.coerceIn(0, 10)

                // Step 2: Server-side deterministic enforcement based on answered count
                val answeredCount = questions.count { q -> !q.userAnswer.isNullOrBlank() }
                Log.d(TAG, "Ch2 server-side: $answeredCount/${questions.size} questions answered")

                // Rule 1: Cap attemptCompleteness based on answered count
                when (answeredCount) {
                    0 -> { ac = 0; sc = 0; la = 0; ce = 0; cq = 0; ec = 0
                        Log.d(TAG, "Server-side: 0 answered → all scores = 0") }
                    1 -> { ac = ac.coerceAtMost(7)
                        Log.d(TAG, "Server-side: 1/3 answered → ac≤7") }
                    2 -> { ac = ac.coerceAtMost(13)
                        Log.d(TAG, "Server-side: 2/3 answered → ac≤13") }
                }

                // Rule 2: Scale code-related scores proportionally to answered count
                if (answeredCount < 3 && answeredCount > 0) {
                    val scale = answeredCount.toDouble() / 3.0
                    sc = (sc * scale).toInt().coerceIn(0, 20)
                    la = (la * scale).toInt().coerceIn(0, 25)
                    ce = (ce * scale).toInt().coerceIn(0, 15)
                    cq = (cq * scale).toInt().coerceIn(0, 10)
                    ec = (ec * scale).toInt().coerceIn(0, 10)  // BUG#11-FIX: scale errorCount too
                    Log.d(TAG, "Server-side: scaled scores by ${String.format("%.1f", scale)}x for partial attempt")
                }

                val validatedTotal = ac + sc + la + ce + cq + ec
                Log.i(TAG, "Server-validated Ch2 scores: ac=$ac sc=$sc la=$la ce=$ce cq=$cq ec=$ec total=$validatedTotal")

                val result = EvaluationResult(
                    attemptCompleteness = EvaluationCategory(ac, 20, evalResponse.attemptCompleteness.details),
                    syntaxCorrectness = EvaluationCategory(sc, 20, evalResponse.syntaxCorrectness.details),
                    logicAccuracy = EvaluationCategory(la, 25, evalResponse.logicAccuracy.details),
                    criticalElements = EvaluationCategory(ce, 15, evalResponse.criticalElements.details),
                    codeQuality = EvaluationCategory(cq, 10, evalResponse.codeQuality.details),
                    errorCount = EvaluationCategory(ec, 10, evalResponse.errorCount.details),
                    totalScore = validatedTotal,
                    maxScore = 100,
                    percentage = validatedTotal.toDouble(),
                    weightedScore = (validatedTotal * ChallengeConstants.CHALLENGE_2_WEIGHT).toInt(),
                    feedback = evalResponse.feedback,
                    evaluatedAt = System.currentTimeMillis()
                )
                Log.i(TAG, "evaluateChallenge2 succeeded on attempt $parseAttempt")
                return@withContext Result.success(result)
            } catch (e: ClientException) {
                Log.e(TAG, "Non-retryable client error in evaluateChallenge2", e)
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "evaluateChallenge2 parse attempt $parseAttempt failed: ${e.message}")
                if (parseAttempt < maxParseRetries) {
                    kotlinx.coroutines.delay(1000L * parseAttempt)
                }
            }
        }

        Log.e(TAG, "evaluateChallenge2 failed after $maxParseRetries attempts", lastException)
        Result.failure(lastException ?: Exception("Challenge 2 evaluation failed after $maxParseRetries retries"))
    }

    /**
     * Evaluate Challenge 3 complete code submission.
     * Retries up to 3 times on JSON parse failures.
     */
    suspend fun evaluateChallenge3(
        questions: List<Challenge3QuestionInternal>
    ): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        val questionsText = questions.mapIndexed { i, q ->
            """
Question ${i+1}: ${q.scenario}
Requirements: ${q.requirements.joinToString("; ")}
Expected Elements: ${q.expectedElements.joinToString(", ")}
User's Code:
```cpp
${q.userCode ?: "// NOT ANSWERED"}
```
"""
        }.joinToString("\n---\n")

        val prompt = """
You are a STRICT code examination evaluator. Score these complete code solutions using ONLY the rubric below.
Apply DEDUCTIONS from maximum. Do NOT give benefit of doubt. Be harsh and accurate like HackerRank.

═══ QUESTIONS AND SOLUTIONS ═══
$questionsText

═══ STRICT SCORING RUBRIC (100 points total across 3 questions) ═══

CRITICAL RULE: If a question's User's Code is "// NOT ANSWERED" or empty/blank, that question scores 0 in ALL categories. No partial credit for blank code.

1. attemptCompleteness (max 20):
   - 3/3 questions with meaningful code: 20
   - 2/3 questions with meaningful code: 13
   - 1/3 questions with meaningful code: 7
   - 0/3 questions with code: 0
   - "Meaningful" = has actual logic, not just empty setup()/loop() or comments only

2. syntaxCorrectness (max 20):
   - Per answered question: would this code compile?
   - Start at 20 (proportional to answered questions), deduct:
   - Missing semicolons: -2 each
   - Unmatched braces: -3 each
   - Undeclared variables used: -3 each
   - Invalid function signatures: -3 each
   - Unanswered questions: 0

3. logicAccuracy (max 25):
   - Per answered question: check against Requirements listed
   - Each requirement met correctly: +(25 / (3 * total_requirements_per_question))
   - Each requirement NOT met or incorrectly implemented: 0 for that share
   - Completely wrong logic: 0 for that question's share
   - If unattempted: 0

4. criticalElements (max 15):
   - Per answered question check: #include present? setup() present? loop() present? Pin definitions?
   - All expected elements present: proportional score
   - Deduct (15 / total_expected_elements) per missing element
   - Unanswered: 0

5. codeQuality (max 10):
   - Clean code structure with proper indentation: +2
   - Meaningful variable names: +2
   - No unnecessary code/dead code: +2
   - Efficient logic (no redundant operations): +2
   - Follows embedded systems best practices (debouncing, proper delays): +2
   - Scale to questions answered (e.g., 1/3 answered = max 3.3)
   - If 0 answered: 0

6. errorCount (max 10):
   - Count total errors (syntax + logic + missing elements) across ALL answered questions
   - 0 errors: 10, 1: 7, 2: 5, 3: 3, 4+: 0
   - If 0 answered: 0

═══ MANDATORY RULES ═══
- totalScore MUST equal attemptCompleteness + syntaxCorrectness + logicAccuracy + criticalElements + codeQuality + errorCount
- Scores can NEVER exceed their maxScore
- Empty/blank code = ZERO credit for that question
- KEEP EACH "details" UNDER 20 WORDS. Be concise.
- "feedback" must be under 30 words.

Return ONLY this JSON (no markdown, no explanation):
{
  "attemptCompleteness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "syntaxCorrectness": {"score": 0, "maxScore": 20, "details": "brief reason"},
  "logicAccuracy": {"score": 0, "maxScore": 25, "details": "brief reason"},
  "criticalElements": {"score": 0, "maxScore": 15, "details": "brief reason"},
  "codeQuality": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "errorCount": {"score": 0, "maxScore": 10, "details": "brief reason"},
  "totalScore": 0,
  "feedback": "brief summary"
}
""".trimIndent()

        val maxParseRetries = 3
        var lastException: Exception? = null

        for (parseAttempt in 1..maxParseRetries) {
            try {
                Log.d(TAG, "evaluateChallenge3 attempt $parseAttempt/$maxParseRetries")
                val response = callGeminiAPI(prompt, temperature = 0.3)
                val jsonStr = extractJsonObject(response)
                val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)

                requireNotNull(evalResponse.attemptCompleteness) { "Missing attemptCompleteness" }
                requireNotNull(evalResponse.syntaxCorrectness) { "Missing syntaxCorrectness" }
                requireNotNull(evalResponse.logicAccuracy) { "Missing logicAccuracy" }

                // Step 1: Clamp each category to valid range
                var ac = evalResponse.attemptCompleteness.score.coerceIn(0, 20)
                var sc = evalResponse.syntaxCorrectness.score.coerceIn(0, 20)
                var la = evalResponse.logicAccuracy.score.coerceIn(0, 25)
                var ce = evalResponse.criticalElements.score.coerceIn(0, 15)
                var cq = evalResponse.codeQuality.score.coerceIn(0, 10)
                var ec = evalResponse.errorCount.score.coerceIn(0, 10)

                // Step 2: Server-side deterministic enforcement based on answered count
                val answeredCount = questions.count { q -> !q.userCode.isNullOrBlank() && q.userCode != "// NOT ANSWERED" }
                Log.d(TAG, "Ch3 server-side: $answeredCount/${questions.size} questions answered")

                // Rule 1: Cap attemptCompleteness based on answered count
                when (answeredCount) {
                    0 -> { ac = 0; sc = 0; la = 0; ce = 0; cq = 0; ec = 0
                        Log.d(TAG, "Server-side: 0 answered → all scores = 0") }
                    1 -> { ac = ac.coerceAtMost(7)
                        Log.d(TAG, "Server-side: 1/3 answered → ac≤7") }
                    2 -> { ac = ac.coerceAtMost(13)
                        Log.d(TAG, "Server-side: 2/3 answered → ac≤13") }
                }

                // Rule 2: Scale code-related scores proportionally to answered count
                if (answeredCount < 3 && answeredCount > 0) {
                    val scale = answeredCount.toDouble() / 3.0
                    sc = (sc * scale).toInt().coerceIn(0, 20)
                    la = (la * scale).toInt().coerceIn(0, 25)
                    ce = (ce * scale).toInt().coerceIn(0, 15)
                    cq = (cq * scale).toInt().coerceIn(0, 10)
                    ec = (ec * scale).toInt().coerceIn(0, 10)  // BUG#9-FIX: Scale errorCount too
                    Log.d(TAG, "Server-side: scaled scores by ${String.format("%.1f", scale)}x for partial attempt")
                }

                val validatedTotal = ac + sc + la + ce + cq + ec
                Log.i(TAG, "Server-validated Ch3 scores: ac=$ac sc=$sc la=$la ce=$ce cq=$cq ec=$ec total=$validatedTotal")

                val result = EvaluationResult(
                    attemptCompleteness = EvaluationCategory(ac, 20, evalResponse.attemptCompleteness.details),
                    syntaxCorrectness = EvaluationCategory(sc, 20, evalResponse.syntaxCorrectness.details),
                    logicAccuracy = EvaluationCategory(la, 25, evalResponse.logicAccuracy.details),
                    criticalElements = EvaluationCategory(ce, 15, evalResponse.criticalElements.details),
                    codeQuality = EvaluationCategory(cq, 10, evalResponse.codeQuality.details),
                    errorCount = EvaluationCategory(ec, 10, evalResponse.errorCount.details),
                    totalScore = validatedTotal,
                    maxScore = 100,
                    percentage = validatedTotal.toDouble(),
                    weightedScore = (validatedTotal * ChallengeConstants.CHALLENGE_3_WEIGHT).toInt(),
                    feedback = evalResponse.feedback,
                    evaluatedAt = System.currentTimeMillis()
                )
                Log.i(TAG, "evaluateChallenge3 succeeded on attempt $parseAttempt")
                return@withContext Result.success(result)
            } catch (e: ClientException) {
                Log.e(TAG, "Non-retryable client error in evaluateChallenge3", e)
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "evaluateChallenge3 parse attempt $parseAttempt failed: ${e.message}")
                if (parseAttempt < maxParseRetries) {
                    kotlinx.coroutines.delay(1000L * parseAttempt)
                }
            }
        }

        Log.e(TAG, "evaluateChallenge3 failed after $maxParseRetries attempts", lastException)
        Result.failure(lastException ?: Exception("Challenge 3 evaluation failed after $maxParseRetries retries"))
    }

    // ========== HELPER METHODS ==========
    
    /**
     * Call Ollama API with robust retry logic
     * - 5 retry attempts
     * - Exponential backoff: 1s, 2s, 4s, 8s, 16s (+ random jitter)
     * - Detailed error logging
     */
    private suspend fun callGeminiAPI(prompt: String, temperature: Double = 0.7): String {
        val maxRetries = 5
        val baseDelayMs = 1000L
        
        val requestBody = JsonObject().apply {
            addProperty("model", NetworkModule.DEFAULT_MODEL)
            addProperty("prompt", prompt)
            addProperty("stream", false)
            add("options", JsonObject().apply {
                addProperty("temperature", temperature)
                addProperty("num_predict", 16384)
                addProperty("top_p", 0.95)
            })
        }

        val request = Request.Builder()
            .url(NetworkModule.getOllamaGenerateUrl())
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()

        var lastException: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                Log.d(TAG, "API call attempt ${attempt + 1}/$maxRetries")
                
                client.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    
                    if (responseCode == 429) {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (baseDelayMs * (1 shl attempt))
                        Log.w(TAG, "Rate limited (429). Retry after: ${retryAfter}ms")
                        throw RateLimitException("Rate limited by API. Retry after ${retryAfter}ms")
                    }
                    
                    if (responseCode in 500..599) {
                        throw ServerException("Server error: $responseCode - ${response.message}")
                    }
                    
                    if (responseCode in 400..499) {
                        throw ClientException("Client error: $responseCode - ${response.message}")
                    }
                    
                    if (!response.isSuccessful) {
                        throw Exception("API error: $responseCode - ${response.message}")
                    }

                    val responseBody = response.body?.string() 
                        ?: throw Exception("Empty response body")
                    
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

                    jsonResponse.get("error")?.let { error ->
                        val errorMessage = error.asJsonObject.get("message")?.asString ?: "Unknown API error"
                        throw Exception("Ollama API error: $errorMessage")
                    }

                    val content = jsonResponse.get("response")?.asString
                        ?: throw Exception("No response text from Ollama")

                    // Strip Qwen3 <think>...</think> reasoning blocks before JSON parsing
                    val cleaned = content.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()

                    Log.d(TAG, "API call successful on attempt ${attempt + 1}")
                    return cleaned
                }
            } catch (e: ClientException) {
                Log.e(TAG, "Non-retryable client error: ${e.message}")
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    val delayMs = baseDelayMs * (1L shl attempt) + (Math.random() * 500).toLong()
                    Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        
        val errorMessage = when (lastException) {
            is RateLimitException -> "API rate limit exceeded. Please wait a moment and try again."
            is ServerException -> NetworkModule.SERVER_DOWN_MESSAGE
            is java.net.UnknownHostException -> NetworkModule.SERVER_DOWN_MESSAGE
            is java.net.SocketTimeoutException -> NetworkModule.SERVER_DOWN_MESSAGE
            else -> lastException?.message ?: "Unknown error occurred"
        }
        
        throw Exception("Failed after $maxRetries attempts: $errorMessage")
    }
    
    // Custom exceptions for better error handling
    private class RateLimitException(message: String) : Exception(message)
    private class ServerException(message: String) : Exception(message)
    private class ClientException(message: String) : Exception(message)


    private fun extractJsonArray(response: String): String {
        // Log the raw response for debugging
        Log.d(TAG, "Raw response (first 500 chars): ${response.take(500)}")
        
        var cleaned = response.trim()
        
        // Remove markdown code blocks (```json ... ``` or ``` ... ```)
        val markdownPatterns = listOf(
            Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE),
            Regex("```\\s*([\\s\\S]*?)```")
        )
        
        var foundMarkdown = false
        for (pattern in markdownPatterns) {
            val match = pattern.find(cleaned)
            if (match != null) {
                cleaned = match.groupValues[1].trim()
                Log.d(TAG, "Extracted from markdown: ${cleaned.take(200)}")
                foundMarkdown = true
                break
            }
        }
        
        // If no complete markdown block found, try to just remove the opening ```json prefix
        if (!foundMarkdown) {
            val openingPatterns = listOf(
                Regex("^```json\\s*", RegexOption.IGNORE_CASE),
                Regex("^```\\s*")
            )
            for (pattern in openingPatterns) {
                if (pattern.containsMatchIn(cleaned)) {
                    cleaned = pattern.replace(cleaned, "").trim()
                    // Also remove trailing ``` if present
                    cleaned = cleaned.replace(Regex("```\\s*$"), "").trim()
                    Log.d(TAG, "Removed markdown prefix: ${cleaned.take(200)}")
                    break
                }
            }
        }
        
        // Normalize whitespace - replace CRLF and multiple newlines with single spaces for better parsing
        val normalizedForSearch = cleaned.replace("\r\n", "\n").replace("\r", "\n")
        
        // Find the JSON array within the cleaned string
        val start = normalizedForSearch.indexOf('[')
        val end = normalizedForSearch.lastIndexOf(']')
        
        // Also check the original cleaned string in case normalization changed indices
        val startOriginal = cleaned.indexOf('[')
        val endOriginal = cleaned.lastIndexOf(']')
        
        Log.d(TAG, "Array bracket search: normalized start=$start end=$end, original start=$startOriginal end=$endOriginal")
        
        // Use whichever approach finds valid brackets
        val (finalStart, finalEnd, useNormalized) = when {
            start != -1 && end != -1 && start < end -> Triple(start, end, true)
            startOriginal != -1 && endOriginal != -1 && startOriginal < endOriginal -> Triple(startOriginal, endOriginal, false)
            else -> Triple(-1, -1, false)
        }
        
        if (finalStart == -1 || finalEnd == -1 || finalStart >= finalEnd) {
            Log.e(TAG, "No valid JSON array found. Cleaned response: ${cleaned.take(500)}")
            throw Exception("Invalid JSON array in response. No array brackets found.")
        }
        
        val jsonArray = if (useNormalized) normalizedForSearch.substring(finalStart, finalEnd + 1) else cleaned.substring(finalStart, finalEnd + 1)
        Log.d(TAG, "Extracted JSON array (first 300 chars): ${jsonArray.take(300)}")
        
        return jsonArray
    }

    private fun extractJsonObject(response: String): String {
        var cleaned = response.trim()
        
        // Remove markdown code blocks (```json ... ``` or ``` ... ```)
        val markdownPatterns = listOf(
            Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE),
            Regex("```\\s*([\\s\\S]*?)```")
        )
        
        for (pattern in markdownPatterns) {
            val match = pattern.find(cleaned)
            if (match != null) {
                cleaned = match.groupValues[1].trim()
                break
            }
        }
        
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1 || start >= end) {
            Log.e(TAG, "No valid JSON object found. Cleaned response: $cleaned")
            throw Exception("Invalid JSON object in response")
        }
        return cleaned.substring(start, end + 1)
    }
}

// ========== RESPONSE DATA CLASSES ==========

data class Challenge1ProblemResponse(
    val problemStatement: String = "",
    val expectedMcu: String = "",
    val expectedComponents: List<String> = emptyList(),
    val difficulty: String = "Easy",
    val category: String = ""
)

data class Challenge2QuestionResponse(
    val scenario: String = "",
    val description: String = "",
    val preCode: String = "",
    val postCode: String = "",
    val missingLineCount: Int = 4,
    val hints: List<String> = emptyList(),
    val correctAnswer: String = ""
)

data class Challenge3QuestionResponse(
    val scenario: String = "",
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val expectedElements: List<String> = emptyList()
)

data class EvaluationCategoryResponse(
    val score: Int = 0,
    val maxScore: Int = 0,
    val details: String = ""
)

data class EvaluationResponse(
    val attemptCompleteness: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val syntaxCorrectness: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val logicAccuracy: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val criticalElements: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val codeQuality: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val errorCount: EvaluationCategoryResponse = EvaluationCategoryResponse(),
    val totalScore: Int = 0,
    val feedback: String = ""
)

// ========== INTERNAL DATA CLASSES FOR SERVICE USE ==========
// These are used internally by GeminiChallengeService and Challenge Activities

/**
 * Challenge 1 Problem - Hardware Selection
 * Used internally for problem generation
 */
data class Challenge1Problem(
    val id: Int = 0,
    val statement: String = "",
    val requiredComponents: List<String> = emptyList(),
    val codeBlocks: List<CodeBlock> = emptyList(),
    // Extended fields from Gemini generation
    val problemStatement: String = "",
    val expectedMcu: String = "",
    val expectedComponents: List<String> = emptyList(),
    val difficulty: String = "Easy",
    val category: String = ""
)

data class CodeBlock(
    val id: Int = 0,
    val content: String = "",
    val category: CodeBlockCategory = CodeBlockCategory.SETUP
)

enum class CodeBlockCategory {
    INCLUDE, DEFINE, DECLARATION, SETUP, LOOP, FUNCTION
}

/**
 * Challenge 1 Answer - tracks user selections
 */
data class Challenge1ProblemAnswer(
    val selectedMcu: String = "",
    val selectedComponents: List<String> = emptyList(),
    val codeBlockOrder: List<String> = emptyList(),
    val codeBlockIdOrder: List<Int> = emptyList(),
    val isComplete: Boolean = false
)

/**
 * Extended Challenge 2 Question - Code Completion
 * Includes all fields needed by Challenge2Activity
 */
data class Challenge2QuestionInternal(
    val id: Int = 0,
    val scenario: String = "",
    val description: String = "",
    val preCode: String = "",
    val postCode: String = "",
    val missingLineCount: Int = 4,
    val hints: List<String> = emptyList(),
    val correctAnswer: String = "",
    var userAnswer: String? = null
)

/**
 * Extended Challenge 3 Question - Complete Code
 * Includes all fields needed by Challenge3Activity
 */
data class Challenge3QuestionInternal(
    val id: Int = 0,
    val scenario: String = "",
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val expectedElements: List<String> = emptyList(),
    var userCode: String? = null
)

