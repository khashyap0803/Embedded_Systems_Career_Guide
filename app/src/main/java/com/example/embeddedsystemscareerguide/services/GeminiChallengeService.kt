package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * GeminiChallengeService - AI-Powered Challenge Generation and Evaluation
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
        
        // API Configuration
        private const val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"
        
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

    // OkHttp client with appropriate timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // ========== CHALLENGE 1: PROBLEM GENERATION ==========
    
    /**
     * Generate 3 unique hardware design problems for Challenge 1
     * Returns Challenge1Problem objects for internal use
     */
    suspend fun generateChallenge1Problems(): Result<List<Challenge1Problem>> = withContext(Dispatchers.IO) {
        val prompt = """
Generate 3 unique VERY SIMPLE embedded systems problems for a beginner hardware selection challenge.

AVAILABLE COMPONENTS:
MCU Boards: ${AVAILABLE_MCUS.joinToString(", ")}
Sensors: ${AVAILABLE_SENSORS.joinToString(", ")}
Modules: ${AVAILABLE_MODULES.joinToString(", ")}

STRICT REQUIREMENTS FOR EACH PROBLEM:
- MUST use exactly 1 MCU + 1-2 sensors + 1-2 modules (MAXIMUM 4 components total including MCU)
- Problems MUST be VERY SIMPLE and solvable by absolute beginners
- Use SIMPLE logic: read sensor → do action (no complex conditions)
- Real-world practical but EASY scenarios
- Clear objective with MINIMAL component requirements
- Different scenarios for each (e.g., safety, automation, monitoring)

EXAMPLES OF ACCEPTABLE SIMPLE PROBLEMS:
- "Detect motion and turn on LED" (PIR + LED = 2 components)
- "Measure temperature and display on LCD" (DHT11 + LCD = 2 components)  
- "Detect gas and sound buzzer" (MQ-2 + Buzzer = 2 components)

Return ONLY valid JSON array with exactly 3 problems:
[
  {
    "problemStatement": "Design a motion-activated LED light that turns on when motion is detected",
    "expectedMcu": "Arduino UNO",
    "expectedComponents": ["PIR Motion Sensor", "LED Module (RGB)"],
    "difficulty": "Easy",
    "category": "Automation"
  },
  {
    "problemStatement": "...",
    "expectedMcu": "...",
    "expectedComponents": [...],
    "difficulty": "Easy",
    "category": "..."
  },
  {
    "problemStatement": "...",
    "expectedMcu": "...",
    "expectedComponents": [...],
    "difficulty": "Easy",
    "category": "..."
  }
]
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
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

Generate 15-25 code blocks total. Each block should be one logical line of code.
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
            val jsonStr = extractJsonArray(response)
            val type = object : TypeToken<List<CodeBlockResponse>>() {}.type
            val blocks: List<CodeBlockResponse> = gson.fromJson(jsonStr, type)
            
            val result = blocks.mapIndexed { index, b ->
                val category = when (b.category.uppercase()) {
                    "INCLUDE" -> CodeBlockCategory.INCLUDE
                    "DEFINE" -> CodeBlockCategory.DEFINE
                    "SETUP" -> CodeBlockCategory.SETUP
                    "LOOP" -> CodeBlockCategory.LOOP
                    else -> CodeBlockCategory.LOOP
                }
                CodeBlock(
                    id = index + 1,
                    content = b.content,
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
        val content: String = "",
        val category: String = "LOOP"
    )

    // ========== CHALLENGE 2: CODE COMPLETION QUESTIONS ==========
    
    /**
     * Generate 3 code completion questions for Challenge 2
     * Returns Challenge2QuestionInternal for use by Challenge2Activity
     */
    suspend fun generateChallenge2Questions(): Result<List<Challenge2QuestionInternal>> = withContext(Dispatchers.IO) {
        val prompt = """
Generate 3 code completion problems for embedded systems. Keep ALL code SHORT (under 8 lines each).

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
- Components: LED, Relay, PIR, DHT11, Servo, Buzzer
- No lengthy explanations. JSON only.
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
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
        val prompt = """
Generate 3 HIGH difficulty embedded systems problems requiring COMPLETE code from scratch.

SCENARIO REQUIREMENTS:
- Use ESP32 or Arduino UNO
- Include 4-6 components creating a complex system
- Require: sensor reading, data processing, actuator control, communication

QUESTION FORMAT:
1. Detailed problem statement with all components listed
2. Clear functional requirements (4-6 bullet points)
3. Expected code structure: includes, definitions, setup(), loop()

Return ONLY valid JSON array:
[
  {
    "scenario": "🏭 INDUSTRIAL GAS MONITORING SYSTEM",
    "description": "Design a complete gas monitoring and alert system using ESP32, MQ-2 Gas sensor, MQ-7 CO sensor, Buzzer, LCD 16x2 I2C, and GSM SIM800L.",
    "requirements": [
      "Read both gas sensors every 2 seconds",
      "Display current readings on LCD",
      "If MQ-2 > 400 OR MQ-7 > 200 → activate buzzer + send SMS",
      "Include proper pin definitions and setup()"
    ],
    "hints": ["Use Wire.h for I2C", "Check threshold values carefully"],
    "expectedElements": ["#include", "setup()", "loop()", "analogRead", "digitalWrite", "if"]
  },
  {...},
  {...}
]
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
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
     * Evaluate Challenge 1 submission with 6-parameter scoring
     */
    suspend fun evaluateChallenge1(
        problemStatement: String,
        selectedMcu: String,
        selectedComponents: List<String>,
        codeBlocks: List<String>
    ): Result<EvaluationResult> = withContext(Dispatchers.IO) {
        val prompt = """
Evaluate this embedded systems hardware + software solution.

PROBLEM: $problemStatement

USER'S HARDWARE:
- MCU: $selectedMcu
- Components: ${selectedComponents.joinToString(", ")}

USER'S SOFTWARE (Code Blocks in order):
${codeBlocks.mapIndexed { i, block -> "${i+1}. $block" }.joinToString("\n")}

EVALUATE WITH 6 PARAMETERS (0-100 scale per category):

1. **Attempt Completeness** (0-20 pts): How complete is the attempt?
2. **Syntax Correctness** (0-20 pts): Are code blocks properly ordered?
3. **Logic Accuracy** (0-25 pts): Does the logic solve the problem?
4. **Critical Elements** (0-15 pts): Are essential components present?
5. **Code Quality** (0-10 pts): Efficient code structure?
6. **Error Count** (0-10 pts): 0 errors=10, 1-2=7, 3-4=4, 5+=0

Return ONLY valid JSON:
{
  "attemptCompleteness": {"score": 18, "maxScore": 20, "details": "All components selected"},
  "syntaxCorrectness": {"score": 16, "maxScore": 20, "details": "Proper block ordering"},
  "logicAccuracy": {"score": 22, "maxScore": 25, "details": "Correct flow logic"},
  "criticalElements": {"score": 12, "maxScore": 15, "details": "Missing WiFi init"},
  "codeQuality": {"score": 8, "maxScore": 10, "details": "Good structure"},
  "errorCount": {"score": 7, "maxScore": 10, "details": "2 minor issues"},
  "totalScore": 83,
  "feedback": "Strong attempt! Component selection is excellent. Minor logic issue in order."
}
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
            val jsonStr = extractJsonObject(response)
            val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)
            
            val result = EvaluationResult(
                attemptCompleteness = EvaluationCategory(
                    evalResponse.attemptCompleteness.score,
                    evalResponse.attemptCompleteness.maxScore,
                    evalResponse.attemptCompleteness.details
                ),
                syntaxCorrectness = EvaluationCategory(
                    evalResponse.syntaxCorrectness.score,
                    evalResponse.syntaxCorrectness.maxScore,
                    evalResponse.syntaxCorrectness.details
                ),
                logicAccuracy = EvaluationCategory(
                    evalResponse.logicAccuracy.score,
                    evalResponse.logicAccuracy.maxScore,
                    evalResponse.logicAccuracy.details
                ),
                criticalElements = EvaluationCategory(
                    evalResponse.criticalElements.score,
                    evalResponse.criticalElements.maxScore,
                    evalResponse.criticalElements.details
                ),
                codeQuality = EvaluationCategory(
                    evalResponse.codeQuality.score,
                    evalResponse.codeQuality.maxScore,
                    evalResponse.codeQuality.details
                ),
                errorCount = EvaluationCategory(
                    evalResponse.errorCount.score,
                    evalResponse.errorCount.maxScore,
                    evalResponse.errorCount.details
                ),
                totalScore = evalResponse.totalScore,
                maxScore = 100,
                percentage = evalResponse.totalScore.toDouble(),
                weightedScore = (evalResponse.totalScore * ChallengeConstants.CHALLENGE_1_WEIGHT).toInt(),
                feedback = evalResponse.feedback,
                evaluatedAt = System.currentTimeMillis()
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate Challenge 1", e)
            Result.failure(e)
        }
    }

    /**
     * Evaluate Challenge 2 code completion submission
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
Evaluate these 3 code completion answers for an embedded systems challenge.

$questionsText

EVALUATE WITH 6 PARAMETERS:

1. **Attempt Completeness** (0-20): How many questions attempted (3/3 = 20)?
2. **Syntax Correctness** (0-20): Valid C/C++ syntax, proper semicolons, brackets?
3. **Logic Accuracy** (0-25): Correct conditionals, sensor reading, threshold logic?
4. **Critical Elements** (0-15): Required operations present (digitalWrite, if, etc)?
5. **Code Quality** (0-10): Efficient structure, proper variable types?
6. **Error Count** (0-10): Count of syntax/logic errors?

Return ONLY valid JSON:
{
  "attemptCompleteness": {"score": 18, "maxScore": 20, "details": "3/3 answered"},
  "syntaxCorrectness": {"score": 16, "maxScore": 20, "details": "Minor semicolon issue"},
  "logicAccuracy": {"score": 22, "maxScore": 25, "details": "Correct threshold logic"},
  "criticalElements": {"score": 12, "maxScore": 15, "details": "All controls present"},
  "codeQuality": {"score": 8, "maxScore": 10, "details": "Good structure"},
  "errorCount": {"score": 7, "maxScore": 10, "details": "2 minor errors"},
  "totalScore": 83,
  "feedback": "Strong code completion! Logic is mostly correct."
}
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
            val jsonStr = extractJsonObject(response)
            val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)
            
            val result = EvaluationResult(
                attemptCompleteness = EvaluationCategory(
                    evalResponse.attemptCompleteness.score,
                    evalResponse.attemptCompleteness.maxScore,
                    evalResponse.attemptCompleteness.details
                ),
                syntaxCorrectness = EvaluationCategory(
                    evalResponse.syntaxCorrectness.score,
                    evalResponse.syntaxCorrectness.maxScore,
                    evalResponse.syntaxCorrectness.details
                ),
                logicAccuracy = EvaluationCategory(
                    evalResponse.logicAccuracy.score,
                    evalResponse.logicAccuracy.maxScore,
                    evalResponse.logicAccuracy.details
                ),
                criticalElements = EvaluationCategory(
                    evalResponse.criticalElements.score,
                    evalResponse.criticalElements.maxScore,
                    evalResponse.criticalElements.details
                ),
                codeQuality = EvaluationCategory(
                    evalResponse.codeQuality.score,
                    evalResponse.codeQuality.maxScore,
                    evalResponse.codeQuality.details
                ),
                errorCount = EvaluationCategory(
                    evalResponse.errorCount.score,
                    evalResponse.errorCount.maxScore,
                    evalResponse.errorCount.details
                ),
                totalScore = evalResponse.totalScore,
                maxScore = 100,
                percentage = evalResponse.totalScore.toDouble(),
                weightedScore = (evalResponse.totalScore * ChallengeConstants.CHALLENGE_2_WEIGHT).toInt(),
                feedback = evalResponse.feedback,
                evaluatedAt = System.currentTimeMillis()
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate Challenge 2", e)
            Result.failure(e)
        }
    }

    /**
     * Evaluate Challenge 3 complete code submission
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
Evaluate these 3 COMPLETE CODE solutions for a hard embedded systems challenge.

$questionsText

EVALUATE WITH 6 PARAMETERS (score each 0-max):

1. **Attempt Completeness** (0-20): How complete are the solutions?
2. **Syntax Correctness** (0-20): Valid C/C++ syntax throughout?
3. **Logic Accuracy** (0-25): Does logic fulfill all requirements?
4. **Critical Elements** (0-15): setup(), loop(), includes, pin definitions?
5. **Code Quality** (0-10): Structure, efficiency, best practices?
6. **Error Count** (0-10): Count of errors found?

Return ONLY valid JSON:
{
  "attemptCompleteness": {"score": 18, "maxScore": 20, "details": "3/3 complete"},
  "syntaxCorrectness": {"score": 16, "maxScore": 20, "details": "Valid syntax"},
  "logicAccuracy": {"score": 22, "maxScore": 25, "details": "Requirements met"},
  "criticalElements": {"score": 12, "maxScore": 15, "details": "All elements present"},
  "codeQuality": {"score": 8, "maxScore": 10, "details": "Well structured"},
  "errorCount": {"score": 7, "maxScore": 10, "details": "Minor issues"},
  "totalScore": 83,
  "feedback": "Excellent complete code solutions!"
}
""".trimIndent()

        try {
            val response = callGeminiAPI(prompt)
            val jsonStr = extractJsonObject(response)
            val evalResponse = gson.fromJson(jsonStr, EvaluationResponse::class.java)
            
            val result = EvaluationResult(
                attemptCompleteness = EvaluationCategory(
                    evalResponse.attemptCompleteness.score,
                    evalResponse.attemptCompleteness.maxScore,
                    evalResponse.attemptCompleteness.details
                ),
                syntaxCorrectness = EvaluationCategory(
                    evalResponse.syntaxCorrectness.score,
                    evalResponse.syntaxCorrectness.maxScore,
                    evalResponse.syntaxCorrectness.details
                ),
                logicAccuracy = EvaluationCategory(
                    evalResponse.logicAccuracy.score,
                    evalResponse.logicAccuracy.maxScore,
                    evalResponse.logicAccuracy.details
                ),
                criticalElements = EvaluationCategory(
                    evalResponse.criticalElements.score,
                    evalResponse.criticalElements.maxScore,
                    evalResponse.criticalElements.details
                ),
                codeQuality = EvaluationCategory(
                    evalResponse.codeQuality.score,
                    evalResponse.codeQuality.maxScore,
                    evalResponse.codeQuality.details
                ),
                errorCount = EvaluationCategory(
                    evalResponse.errorCount.score,
                    evalResponse.errorCount.maxScore,
                    evalResponse.errorCount.details
                ),
                totalScore = evalResponse.totalScore,
                maxScore = 100,
                percentage = evalResponse.totalScore.toDouble(),
                weightedScore = (evalResponse.totalScore * ChallengeConstants.CHALLENGE_3_WEIGHT).toInt(),
                feedback = evalResponse.feedback,
                evaluatedAt = System.currentTimeMillis()
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate Challenge 3", e)
            Result.failure(e)
        }
    }

    // ========== HELPER METHODS ==========
    
    /**
     * Call Gemini API with robust retry logic
     * - 5 retry attempts
     * - Exponential backoff: 1s, 2s, 4s, 8s, 16s (+ random jitter)
     * - Detailed error logging
     */
    private fun callGeminiAPI(prompt: String): String {
        val maxRetries = 5
        val baseDelayMs = 1000L
        
        val requestBody = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            )))
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.7)
                addProperty("maxOutputTokens", 8192)
                addProperty("topP", 0.95)
            })
        }

        val request = Request.Builder()
            .url(GEMINI_API_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var lastException: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                Log.d(TAG, "API call attempt ${attempt + 1}/$maxRetries")
                
                client.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    
                    // Handle rate limiting specifically
                    if (responseCode == 429) {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (baseDelayMs * (1 shl attempt))
                        Log.w(TAG, "Rate limited (429). Retry after: ${retryAfter}ms")
                        throw RateLimitException("Rate limited by API. Retry after ${retryAfter}ms")
                    }
                    
                    // Handle server errors (5xx) - these are retryable
                    if (responseCode in 500..599) {
                        throw ServerException("Server error: $responseCode - ${response.message}")
                    }
                    
                    // Handle client errors (4xx except 429) - not retryable
                    if (responseCode in 400..499) {
                        throw ClientException("Client error: $responseCode - ${response.message}")
                    }
                    
                    if (!response.isSuccessful) {
                        throw Exception("API error: $responseCode - ${response.message}")
                    }

                    val responseBody = response.body?.string() 
                        ?: throw Exception("Empty response body")
                    
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

                    // Check for API-level errors
                    jsonResponse.get("error")?.let { error ->
                        val errorMessage = error.asJsonObject.get("message")?.asString ?: "Unknown API error"
                        throw Exception("Gemini API error: $errorMessage")
                    }

                    val candidates = jsonResponse.getAsJsonArray("candidates")
                    if (candidates == null || candidates.size() == 0) {
                        throw Exception("No candidates in response - model may have refused to generate content")
                    }

                    val content = candidates[0].asJsonObject
                        .getAsJsonObject("content")
                        ?.getAsJsonArray("parts")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString
                        ?: throw Exception("No text in response content")

                    Log.d(TAG, "API call successful on attempt ${attempt + 1}")
                    return content.trim()
                }
            } catch (e: ClientException) {
                // Client errors (4xx except 429) are not retryable
                Log.e(TAG, "Non-retryable client error: ${e.message}")
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    // Exponential backoff with jitter: base * 2^attempt + random(0-500ms)
                    val delayMs = baseDelayMs * (1L shl attempt) + (Math.random() * 500).toLong()
                    Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                    Thread.sleep(delayMs)
                }
            }
        }
        
        // All retries exhausted
        val errorMessage = when (lastException) {
            is RateLimitException -> "API rate limit exceeded. Please wait a moment and try again."
            is ServerException -> "Gemini server is temporarily unavailable. Please try again."
            is java.net.UnknownHostException -> "No internet connection. Please check your network."
            is java.net.SocketTimeoutException -> "Connection timed out. Please check your network."
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
    val orderedCodeBlocks: MutableList<CodeBlock> = mutableListOf(),
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

