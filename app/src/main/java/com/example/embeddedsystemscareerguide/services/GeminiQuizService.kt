package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
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
 * AI Quiz Generation Service - Powered by Gemini API
 * Generates unique quizzes for each learning stage
 */
class GeminiQuizService {

    // M3 fix: Use shared client from NetworkModule
    private val client = NetworkModule.standardClient

    private val gson = Gson()

    // M6 fix: Use centralized API URL from NetworkModule
    private val GEMINI_API_URL = NetworkModule.getGeminiApiUrl()

    companion object {
        private const val TAG = "GeminiQuizService"
    }

    data class QuizQuestion(
        val question: String,
        val options: List<String>,
        val correctAnswerIndex: Int,
        val explanation: String
    )

    /**
     * Generate quiz questions for a specific stage
     * Makes 2 API calls to get 10 questions total (5 per call to avoid truncation)
     */
    suspend fun generateQuiz(
        stageTitle: String,
        stageTopics: List<String>,
        numberOfQuestions: Int = 10,  // 10 questions per stage
        difficulty: String = "medium"
    ): List<QuizQuestion> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating quiz for: $stageTitle")
            
            val allQuestions = mutableListOf<QuizQuestion>()
            val questionsPerCall = 5  // Request 5 at a time to avoid truncation
            val callsNeeded = (numberOfQuestions + questionsPerCall - 1) / questionsPerCall
            
            for (callIndex in 0 until callsNeeded) {
                try {
                    val prompt = """
Generate 5 MCQ questions about "$stageTitle" for embedded systems (batch ${callIndex + 1}).

CRITICAL REQUIREMENTS:
- Questions must be SHORT (under 100 characters each)
- NO code snippets or complex syntax in questions
- Ask about CONCEPTS, not code details
- Options are short (1-4 words each)
- Explanation is ONE sentence (max 15 words)

⚠️ JSON FORMAT - MUST FOLLOW:
1. Return ONLY valid JSON array - NO markdown, NO ``` code blocks
2. Start with [ and end with ]
3. Escape quotes with \"
4. NO trailing commas

✅ CORRECT:
[{"question":"What prevents compiler optimization of hardware registers?","options":["static","const","volatile","register"],"correctAnswerIndex":2,"explanation":"volatile keyword prevents optimization."}]

❌ WRONG:
- Code snippets like: "Given `uint8_t x = 0xFF`..."
- Long questions over 100 chars
- ```json blocks

Generate 5 SHORT questions now:
"""
                    val response = callGeminiAPI(prompt)
                    val parsed = parseQuizResponse(response)
                    allQuestions.addAll(parsed)
                    Log.d(TAG, "Batch ${callIndex + 1}: Got ${parsed.size} questions")
                    
                    // Small delay between API calls 
                    if (callIndex < callsNeeded - 1) {
                        kotlinx.coroutines.delay(500)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Batch ${callIndex + 1} failed: ${e.message}")
                }
            }
            
            // If we got enough questions, return them
            if (allQuestions.size >= numberOfQuestions) {
                Log.d(TAG, "Returning $numberOfQuestions questions from API")
                return@withContext allQuestions.take(numberOfQuestions)
            }
            
            // If we got some but not enough, fill with fallbacks
            if (allQuestions.isNotEmpty()) {
                val needed = numberOfQuestions - allQuestions.size
                val fallbacks = getFallbackQuestions(stageTitle).take(needed)
                Log.d(TAG, "Returning ${allQuestions.size} API + $needed fallback = ${allQuestions.size + needed} questions")
                return@withContext allQuestions + fallbacks
            }
            
            // If nothing worked, return all fallbacks
            Log.d(TAG, "Returning ${getFallbackQuestions(stageTitle).size} fallback questions (API failed)")
            return@withContext getFallbackQuestions(stageTitle)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating quiz", e)
            return@withContext getFallbackQuestions(stageTitle)
        }
    }

    /**
     * N4 fix: Added exponential backoff retry for transient API failures
     */
    private suspend fun callGeminiAPI(prompt: String, maxRetries: Int = 3): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var currentDelay = 1000L // Start with 1 second
        
        repeat(maxRetries) { attempt ->
            try {
                return@withContext executeApiCall(prompt)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "API attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                
                // Don't retry on certain errors
                if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                    throw e // Auth errors shouldn't retry
                }
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff
                }
            }
        }
        
        throw lastException ?: Exception("Max retries exceeded")
    }
    
    private fun executeApiCall(prompt: String): String {
        val requestBody = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray()
            val contentObject = JsonObject().apply {
                addProperty("role", "user")
                val partsArray = com.google.gson.JsonArray()
                val partObject = JsonObject().apply {
                    addProperty("text", prompt)
                }
                partsArray.add(partObject)
                add("parts", partsArray)
            }
            contentsArray.add(contentObject)
            add("contents", contentsArray)

            val generationConfig = JsonObject().apply {
                addProperty("temperature", 0.8)
                addProperty("topK", 40)
                addProperty("topP", 0.95)
                addProperty("maxOutputTokens", 2048)
            }
            add("generationConfig", generationConfig)
        }

        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(GEMINI_API_URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            Log.e(TAG, "API Error: ${response.code}")
            throw Exception("API call failed: ${response.code}")
        }

        // Parse response and return text
        // H5 fix: Add null safety for API response parsing
        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
        val candidates = jsonResponse.getAsJsonArray("candidates")
        
        if (candidates == null || candidates.size() == 0) {
            throw Exception("No candidates in API response")
        }
        
        val content = candidates[0].asJsonObject
            .getAsJsonObject("content")
        
        if (content == null) {
            throw Exception("No content in API response")
        }
        
        val parts = content.getAsJsonArray("parts")
        if (parts == null || parts.size() == 0) {
            throw Exception("No parts in API response")
        }
        
        return parts[0].asJsonObject.get("text")?.asString 
            ?: throw Exception("No text in API response")
    }

    private fun parseQuizResponse(response: String): List<QuizQuestion> {
        val targetQuestions = 5  // Each API call requests 5 questions
        var parsedQuestions = listOf<QuizQuestion>()
        
        try {
            // Extract JSON array from response (remove markdown code blocks if present)
            var cleanJson = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            // Try to extract JSON array if there's extra text around it
            val startIndex = cleanJson.indexOf('[')
            val endIndex = cleanJson.lastIndexOf(']')
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1)
            }

            val type = object : TypeToken<List<QuizQuestion>>() {}.type
            parsedQuestions = gson.fromJson(cleanJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing quiz response: ${e.message}")
            Log.e(TAG, "Raw response (first 500 chars): ${response.take(500)}")
            
            // Try an alternative parsing approach - parse question by question
            try {
                parsedQuestions = parseQuizQuestionByQuestion(response)
            } catch (e2: Exception) {
                Log.e(TAG, "Alternative parsing also failed: ${e2.message}")
            }
        }
        
        // If we parsed some questions but not enough, fill with fallbacks
        if (parsedQuestions.isNotEmpty()) {
            Log.d(TAG, "Parsed ${parsedQuestions.size} questions from API response")
            if (parsedQuestions.size >= targetQuestions) {
                return parsedQuestions.take(targetQuestions)
            }
            // Fill remaining slots with fallback questions
            val fallbacks = getFallbackQuestions("").take(targetQuestions - parsedQuestions.size)
            return parsedQuestions + fallbacks
        }
        
        // No questions parsed - throw to trigger outer fallback handler
        throw Exception("Could not parse any questions from response")
    }
    
    /**
     * Alternative parsing method that extracts questions one by one
     * More resilient to malformed JSON - handles multi-line text
     */
    private fun parseQuizQuestionByQuestion(response: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        
        // Clean the response first
        val cleanResponse = response
            .replace("```json", "")
            .replace("```", "")
        
        // Find all question blocks by looking for question fields
        // Use a more flexible pattern that spans multiple lines
        val questionBlockPattern = Regex(
            """"question"\s*:\s*"((?:[^"\\]|\\.)*)"""",
            setOf(RegexOption.MULTILINE)
        )
        
        val optionsBlockPattern = Regex(
            """"options"\s*:\s*\[([\s\S]*?)\]""",
            setOf(RegexOption.MULTILINE)
        )
        
        val correctIndexPattern = Regex(
            """"correctAnswerIndex"\s*:\s*(\d+)"""
        )
        
        val explanationPattern = Regex(
            """"explanation"\s*:\s*"((?:[^"\\]|\\.)*)"""",
            setOf(RegexOption.MULTILINE)
        )
        
        // Split into individual question objects by finding { ... } blocks
        val objectPattern = Regex("""\{[\s\S]*?"question"[\s\S]*?"options"[\s\S]*?"correctAnswerIndex"[\s\S]*?\}""")
        val objectMatches = objectPattern.findAll(cleanResponse)
        
        for (objMatch in objectMatches) {
            try {
                val block = objMatch.value
                
                val questionMatch = questionBlockPattern.find(block)
                val optionsMatch = optionsBlockPattern.find(block)
                val correctIndexMatch = correctIndexPattern.find(block)
                val explanationMatch = explanationPattern.find(block)
                
                if (questionMatch != null && optionsMatch != null && correctIndexMatch != null) {
                    val questionText = questionMatch.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                    
                    val optionsRaw = optionsMatch.groupValues[1]
                    val correctIndex = correctIndexMatch.groupValues[1].toInt()
                    val explanation = explanationMatch?.groupValues?.get(1)
                        ?.replace("\\n", "\n")
                        ?.replace("\\\"", "\"")
                        ?: "See the correct answer above."
                    
                    // Parse options - handle escaped quotes
                    val optionPattern = Regex(""""((?:[^"\\]|\\.)*)"""")
                    val options = optionPattern.findAll(optionsRaw)
                        .map { it.groupValues[1].replace("\\\"", "\"") }
                        .toList()
                    
                    if (options.size >= 2) {
                        questions.add(QuizQuestion(questionText, options, correctIndex, explanation))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse individual question: ${e.message}")
            }
        }
        
        if (questions.isEmpty()) {
            Log.w(TAG, "Could not parse any questions from response using fallback parser")
        }
        
        return questions
    }

    /**
     * Get fallback questions when API fails
     */
    private fun getFallbackQuestions(stageTitle: String): List<QuizQuestion> {
        return listOf(
            QuizQuestion(
                question = "In embedded systems, what does GPIO stand for?",
                options = listOf(
                    "General Purpose Input/Output",
                    "Global Pin In/Out",
                    "Ground Port I/O",
                    "Generic Parallel I/O"
                ),
                correctAnswerIndex = 0,
                explanation = "GPIO stands for General Purpose Input/Output - pins that can be configured as either input or output."
            ),
            QuizQuestion(
                question = "Which communication protocol is synchronous and uses a clock signal?",
                options = listOf(
                    "UART",
                    "SPI",
                    "RS-232",
                    "USB"
                ),
                correctAnswerIndex = 1,
                explanation = "SPI (Serial Peripheral Interface) is synchronous and uses a clock signal (SCLK) for timing."
            ),
            QuizQuestion(
                question = "What is the main advantage of using interrupts in embedded systems?",
                options = listOf(
                    "They use more power",
                    "They eliminate the need for polling",
                    "They slow down the processor",
                    "They require more memory"
                ),
                correctAnswerIndex = 1,
                explanation = "Interrupts allow the CPU to respond to events immediately without constantly checking (polling)."
            ),
            QuizQuestion(
                question = "What type of memory loses its contents when power is removed?",
                options = listOf(
                    "Flash Memory",
                    "EEPROM",
                    "RAM",
                    "ROM"
                ),
                correctAnswerIndex = 2,
                explanation = "RAM (Random Access Memory) is volatile memory that loses data when power is removed."
            ),
            QuizQuestion(
                question = "In I2C protocol, what is the purpose of the SDA line?",
                options = listOf(
                    "Serial Clock",
                    "Serial Data",
                    "Power Supply",
                    "Ground Reference"
                ),
                correctAnswerIndex = 1,
                explanation = "SDA (Serial Data Line) carries the data in I2C communication, while SCL carries the clock."
            ),
            QuizQuestion(
                question = "What is PWM commonly used for in embedded systems?",
                options = listOf(
                    "Reading sensor data",
                    "Controlling motor speed and LED brightness",
                    "Storing data in memory",
                    "Network communication"
                ),
                correctAnswerIndex = 1,
                explanation = "PWM (Pulse Width Modulation) controls average power, commonly used for motor speed and LED dimming."
            ),
            QuizQuestion(
                question = "What is the function of a watchdog timer?",
                options = listOf(
                    "To measure temperature",
                    "To reset the system if it becomes unresponsive",
                    "To count external events",
                    "To generate audio signals"
                ),
                correctAnswerIndex = 1,
                explanation = "A watchdog timer resets the system if software fails to periodically reset it, preventing system hangs."
            ),
            QuizQuestion(
                question = "Which ARM Cortex-M core is commonly used in low-power embedded applications?",
                options = listOf(
                    "Cortex-A53",
                    "Cortex-M0+",
                    "Cortex-A72",
                    "Cortex-X1"
                ),
                correctAnswerIndex = 1,
                explanation = "Cortex-M0+ is designed for low-power embedded applications with minimal resources."
            ),
            QuizQuestion(
                question = "What does ADC stand for in embedded systems?",
                options = listOf(
                    "Automatic Data Control",
                    "Analog to Digital Converter",
                    "Advanced Device Controller",
                    "Address Data Channel"
                ),
                correctAnswerIndex = 1,
                explanation = "ADC converts analog signals from sensors into digital values for processing."
            ),
            QuizQuestion(
                question = "What is the main purpose of an RTOS in embedded systems?",
                options = listOf(
                    "To provide a graphical user interface",
                    "To manage tasks with timing guarantees",
                    "To connect to the internet",
                    "To compile source code"
                ),
                correctAnswerIndex = 1,
                explanation = "RTOS (Real-Time Operating System) manages tasks with predictable timing guarantees."
            )
        )
    }

    /**
     * Get example topics for each stage
     */
    fun getStageTopics(stageId: Int): List<String> {
        return when (stageId) {
            1 -> listOf("embedded systems basics", "microprocessors", "system components")
            2 -> listOf("digital electronics", "logic gates", "binary numbers")
            3 -> listOf("microcontroller architecture", "ARM Cortex-M", "memory types")
            4 -> listOf("C programming", "data types", "pointers", "structures")
            5 -> listOf("GPIO", "input/output programming", "registers")
            6 -> listOf("timers", "PWM", "pulse generation")
            7 -> listOf("interrupts", "ISR", "interrupt priority")
            8 -> listOf("UART", "SPI", "I2C communication")
            9 -> listOf("ADC", "DAC", "analog signals")
            10 -> listOf("sensors", "actuators", "interfacing")
            11 -> listOf("RTOS concepts", "tasks", "scheduling")
            12 -> listOf("debugging", "testing", "optimization")
            13 -> listOf("IoT", "WiFi", "Bluetooth")
            14 -> listOf("power management", "low power modes")
            15 -> listOf("project design", "integration", "deployment")
            else -> listOf("embedded systems", "microcontrollers", "programming")
        }
    }
}
