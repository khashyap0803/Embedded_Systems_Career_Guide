package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * StageGeneratorService - AI-Powered Personalized Stage Generator
 * 
 * Generates personalized learning stages based on assessment results.
 * Uses OllamaService (local fine-tuned LLM via Ngrok) to create a customized
 * curriculum of 30-50 stages tailored to the student's strengths and weaknesses.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class StageGeneratorService(private val context: Context) {

    companion object {
        private const val TAG = "StageGeneratorService"
        private const val TARGET_STAGES = 40  // Target number of stages
        private const val MAX_RETRIES = 3     // Maximum API retry attempts
        private const val INITIAL_RETRY_DELAY_MS = 1000L  // 1 second initial delay
        
        @Volatile
        private var instance: StageGeneratorService? = null
        
        fun getInstance(context: Context): StageGeneratorService {
            return instance ?: synchronized(this) {
                instance ?: StageGeneratorService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = OllamaService.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Assessment result data from the initial assessment
     */
    data class AssessmentResult(
        val totalScore: Int = 0,
        val maxScore: Int = 100,
        val topicScores: Map<String, TopicScore> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TopicScore(
        val score: Int = 0,
        val maxScore: Int = 0,
        val percentage: Int = 0
    )

    /**
     * Callback interface for stage generation progress
     */
    interface GenerationCallback {
        fun onProgress(phase: Int, message: String)
        fun onSuccess(stages: List<PersonalizedStage>)
        fun onError(error: String)
    }

    /**
     * Generate personalized learning stages based on assessment results
     * 
     * @param userName Student's name for personalization
     * @param assessmentResult Results from the initial assessment
     * @param callback Progress callback
     */
    suspend fun generatePersonalizedStages(
        userName: String,
        assessmentResult: AssessmentResult,
        callback: GenerationCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting stage generation for $userName")
            callback.onProgress(1, "Analyzing your assessment results...")

            // Analyze assessment to categorize topics
            val (weakAreas, strongAreas) = categorizeTopics(assessmentResult)
            Log.d(TAG, "Weak areas: $weakAreas, Strong areas: $strongAreas")
            
            callback.onProgress(2, "Creating your personalized learning path...")

            // Retry loop with exponential backoff
            var lastError: String? = null
            var stages: List<PersonalizedStage> = emptyList()
            
            for (attempt in 1..MAX_RETRIES) {
                Log.d(TAG, "API attempt $attempt of $MAX_RETRIES")
                
                // Generate prompt using template (add retry hint on subsequent attempts)
                val prompt = if (attempt == 1) {
                    OllamaService.PromptTemplates.personalizedStages(
                        userName = userName,
                        weakAreas = weakAreas,
                        strongAreas = strongAreas,
                        targetStageCount = TARGET_STAGES
                    )
                } else {
                    // On retry, add explicit instruction for cleaner JSON
                    OllamaService.PromptTemplates.personalizedStages(
                        userName = userName,
                        weakAreas = weakAreas,
                        strongAreas = strongAreas,
                        targetStageCount = TARGET_STAGES
                    ) + "\n\nIMPORTANT: Ensure valid, complete JSON. Close all arrays and objects properly."
                }

                // Call Ollama API
                val result = geminiService.generateContent(prompt, maxOutputTokens = 8192)
                
                if (result.isFailure) {
                    lastError = result.exceptionOrNull()?.message ?: "API call failed"
                    Log.e(TAG, "API call failed on attempt $attempt: $lastError")
                    
                    if (attempt < MAX_RETRIES) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1)) // Exponential backoff
                        callback.onProgress(2, "Retrying... (attempt ${attempt + 1}/$MAX_RETRIES)")
                        delay(delayMs)
                        continue
                    }
                    callback.onError("Failed to generate learning path after $MAX_RETRIES attempts. Please try again later.")
                    return@withContext
                }

                callback.onProgress(3, "Processing AI response...")
                
                // Parse response
                val responseText = result.getOrNull() ?: ""
                stages = parseStagesFromResponse(responseText)
                
                if (stages.isNotEmpty()) {
                    Log.d(TAG, "Successfully parsed ${stages.size} stages on attempt $attempt")
                    break // Success!
                }
                
                // Parsing failed, retry
                lastError = "Failed to parse JSON response"
                Log.w(TAG, "Parse failed on attempt $attempt, ${if (attempt < MAX_RETRIES) "retrying..." else "giving up"}")
                
                if (attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1))
                    callback.onProgress(2, "Response parsing failed. Retrying... (attempt ${attempt + 1}/$MAX_RETRIES)")
                    delay(delayMs)
                }
            }
            
            // Check if we got valid stages after all retries
            if (stages.isEmpty()) {
                Log.e(TAG, "Failed to generate valid stages after $MAX_RETRIES attempts")
                callback.onError("Failed to generate learning path. The AI response could not be parsed. Please try again.")
                return@withContext
            }

            callback.onProgress(4, "Saving your learning path...")
            
            // Mark first stage as unlocked
            val unlockedStages = stages.mapIndexed { index, stage ->
                stage.copy(isUnlocked = index == 0)
            }
            
            // Save to Firestore
            val saveResult = firestoreManager.savePersonalizedStages(unlockedStages)
            if (saveResult.isFailure) {
                Log.e(TAG, "Failed to save stages: ${saveResult.exceptionOrNull()?.message}")
                callback.onError("Failed to save learning path. Please try again.")
                return@withContext
            }

            Log.d(TAG, "Successfully generated and saved ${unlockedStages.size} stages")
            callback.onSuccess(unlockedStages)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating stages", e)
            callback.onError("An error occurred: ${e.message}")
        }
    }

    /**
     * Categorize topics based on assessment scores
     */
    private fun categorizeTopics(assessment: AssessmentResult): Pair<List<String>, List<String>> {
        val weakAreas = mutableListOf<String>()
        val strongAreas = mutableListOf<String>()
        
        if (assessment.topicScores.isEmpty()) {
            // Default categorization if no detailed scores
            val overallPercentage = (assessment.totalScore * 100) / assessment.maxScore.coerceAtLeast(1)
            
            if (overallPercentage < 50) {
                weakAreas.addAll(listOf(
                    "C Programming Basics",
                    "Microcontroller Fundamentals",
                    "Communication Protocols",
                    "RTOS Concepts",
                    "Debugging Techniques"
                ))
            } else if (overallPercentage < 75) {
                weakAreas.addAll(listOf(
                    "Advanced C Concepts",
                    "RTOS Concepts",
                    "IoT Integration"
                ))
                strongAreas.addAll(listOf(
                    "C Programming Basics",
                    "Microcontroller Fundamentals"
                ))
            } else {
                strongAreas.addAll(listOf(
                    "C Programming Basics",
                    "Microcontroller Fundamentals",
                    "Communication Protocols"
                ))
                weakAreas.addAll(listOf(
                    "Advanced RTOS",
                    "System Optimization",
                    "Industry Best Practices"
                ))
            }
        } else {
            // Use detailed topic scores
            assessment.topicScores.forEach { (topic, scoreData) ->
                if (scoreData.percentage < 60) {
                    weakAreas.add(topic)
                } else {
                    strongAreas.add(topic)
                }
            }
        }
        
        return Pair(weakAreas, strongAreas)
    }

    /**
     * Parse AI response into PersonalizedStage objects
     * Enhanced with robust JSON error handling
     */
    private fun parseStagesFromResponse(response: String): List<PersonalizedStage> {
        return try {
            // Clean up response - remove markdown code blocks if present
            var cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            // Try to extract JSON object from response if wrapped in other text
            val jsonStart = cleanedResponse.indexOf('{')
            val jsonEnd = cleanedResponse.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanedResponse = cleanedResponse.substring(jsonStart, jsonEnd + 1)
            }
            
            // Fix common malformed JSON issues
            cleanedResponse = fixMalformedJson(cleanedResponse)
            
            // Try to parse as JSON object with "stages" array
            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val stagesArray = jsonObject.getAsJsonArray("stages")
            
            val stages = mutableListOf<PersonalizedStage>()
            
            stagesArray?.forEach { element ->
                try {
                    val obj = element.asJsonObject
                    val stage = PersonalizedStage(
                        id = obj.get("id")?.asInt ?: (stages.size + 1),
                        title = obj.get("title")?.asString ?: "Stage ${stages.size + 1}",
                        subtitle = obj.get("subtitle")?.asString ?: "",
                        description = obj.get("description")?.asString ?: "",
                        topics = try { 
                            obj.getAsJsonArray("topics")?.mapNotNull { it.asString?.trim() } ?: emptyList()
                        } catch (e: Exception) { emptyList() },
                        difficulty = obj.get("difficulty")?.asString ?: "beginner",
                        estimatedMinutes = obj.get("estimatedMinutes")?.asInt ?: 60,
                        type = obj.get("type")?.asString ?: "theory",
                        xpReward = obj.get("xpReward")?.asInt ?: 100,
                        isCompleted = false,
                        starsEarned = 0,
                        isUnlocked = false
                    )
                    stages.add(stage)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse stage element: ${e.message}")
                }
            }
            
            Log.d(TAG, "Parsed ${stages.size} stages from response")
            stages
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse stages response", e)
            emptyList()
        }
    }

    /**
     * Attempt to fix common malformed JSON issues from AI responses
     * Enhanced with smart truncation repair for incomplete stage objects
     */
    private fun fixMalformedJson(json: String): String {
        var fixed = json.trim()
        
        // Remove trailing commas before closing brackets/braces (Android ICU compatible)
        // Simple loop-based approach instead of regex for compatibility
        fixed = removeTrailingCommas(fixed, ']')
        fixed = removeTrailingCommas(fixed, '}')
        
        // If JSON is truncated mid-object, try to find the last complete stage
        // Look for pattern where a stage object is incomplete
        val stagesStart = fixed.indexOf("\"stages\"")
        if (stagesStart >= 0) {
            val arrayStart = fixed.indexOf('[', stagesStart)
            if (arrayStart >= 0) {
                // Find all complete stage objects (those with closing brace)
                val stagesSection = fixed.substring(arrayStart)
                var depth = 0
                var lastCompleteObjectEnd = -1
                var inString = false
                var escaped = false
                
                for ((i, char) in stagesSection.withIndex()) {
                    if (escaped) {
                        escaped = false
                        continue
                    }
                    when {
                        char == '\\' -> escaped = true
                        char == '"' -> inString = !inString
                        !inString && char == '{' -> depth++
                        !inString && char == '}' -> {
                            depth--
                            if (depth == 0) {
                                lastCompleteObjectEnd = arrayStart + i
                            }
                        }
                    }
                }
                
                // If we found complete objects but JSON is unbalanced, truncate at last complete object
                if (lastCompleteObjectEnd > 0) {
                    val remainder = fixed.substring(lastCompleteObjectEnd + 1).trim()
                    // Check if remainder looks like incomplete object or just closing brackets
                    if (remainder.contains('{') && !remainder.contains('}')) {
                        // Truncate the incomplete object
                        fixed = fixed.substring(0, lastCompleteObjectEnd + 1) + "]}"
                        Log.d(TAG, "Truncated incomplete stage object at position $lastCompleteObjectEnd")
                        return fixed
                    }
                }
            }
        }
        
        // Count braces and brackets to check for balance
        val openBraces = fixed.count { it == '{' }
        val closeBraces = fixed.count { it == '}' }
        val openBrackets = fixed.count { it == '[' }
        val closeBrackets = fixed.count { it == ']' }
        
        // Add missing closing brackets first (inner)
        if (openBrackets > closeBrackets) {
            fixed = fixed + "]".repeat(openBrackets - closeBrackets)
        }
        
        // Add missing closing braces (outer)
        if (openBraces > closeBraces) {
            fixed = fixed + "}".repeat(openBraces - closeBraces)
        }
        
        return fixed
    }

    /**
     * Remove trailing commas before a specific closing character (Android ICU compatible)
     * e.g., [item1, item2,] -> [item1, item2]
     */
    private fun removeTrailingCommas(json: String, closingChar: Char): String {
        val result = StringBuilder()
        var i = 0
        while (i < json.length) {
            if (json[i] == ',') {
                // Look ahead for whitespace followed by closing char
                var j = i + 1
                while (j < json.length && json[j].isWhitespace()) {
                    j++
                }
                if (j < json.length && json[j] == closingChar) {
                    // Skip the comma, keep whitespace and closing char
                    i++
                    continue
                }
            }
            result.append(json[i])
            i++
        }
        return result.toString()
    }

    // NOTE: Fallback stages removed - app is 100% cloud-only
    // All stage generation must succeed via AI or return an error

    /**
     * Check if user has personalized stages
     */
    suspend fun hasPersonalizedStages(): Boolean {
        return firestoreManager.hasPersonalizedStages()
    }

    /**
     * Get user's personalized stages
     */
    suspend fun getPersonalizedStages(): Result<List<PersonalizedStage>> {
        return firestoreManager.getPersonalizedStages()
    }

    /**
     * Regenerate stages with updated assessment, considering user's learning history
     * 
     * This method:
     * 1. Collects user's previous performance data (completed stages, quiz scores, wrong answers)
     * 2. Uses the regenerateStagesWithHistory prompt to create a smarter curriculum
     * 3. Deletes old stages and saves new ones
     * 
     * @param userName Student's name for personalization
     * @param assessmentResult New assessment results
     * @param callback Progress callback
     */
    suspend fun regenerateStages(
        userName: String,
        assessmentResult: AssessmentResult,
        callback: GenerationCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting stage regeneration for $userName with history consideration")
            callback.onProgress(1, "Collecting your learning history...")

            // Collect user's previous performance data
            val performanceData = firestoreManager.collectUserPerformanceData()
            Log.d(TAG, "Performance data: ${performanceData.completedStageIds.size} completed, " +
                    "weak: ${performanceData.weakTopics.size}, strong: ${performanceData.strongTopics.size}")

            callback.onProgress(2, "Analyzing your assessment results...")

            // Categorize topics from new assessment
            val (weakAreas, strongAreas) = categorizeTopics(assessmentResult)
            
            callback.onProgress(3, "Creating your new personalized learning path...")

            // Retry loop with exponential backoff
            var lastError: String? = null
            var stages: List<PersonalizedStage> = emptyList()
            
            for (attempt in 1..MAX_RETRIES) {
                Log.d(TAG, "Regeneration API attempt $attempt of $MAX_RETRIES")
                
                // Use the enhanced regeneration prompt that considers history
                val prompt = if (attempt == 1) {
                    OllamaService.PromptTemplates.regenerateStagesWithHistory(
                        userName = userName,
                        weakAreas = weakAreas,
                        strongAreas = strongAreas,
                        performanceData = performanceData,
                        targetStageCount = TARGET_STAGES
                    )
                } else {
                    // On retry, add explicit instruction for cleaner JSON
                    OllamaService.PromptTemplates.regenerateStagesWithHistory(
                        userName = userName,
                        weakAreas = weakAreas,
                        strongAreas = strongAreas,
                        performanceData = performanceData,
                        targetStageCount = TARGET_STAGES
                    ) + "\n\nIMPORTANT: Ensure valid, complete JSON. Close all arrays and objects properly."
                }

                // Call Ollama API
                val result = geminiService.generateContent(prompt, maxOutputTokens = 8192)
                
                if (result.isFailure) {
                    lastError = result.exceptionOrNull()?.message ?: "API call failed"
                    Log.e(TAG, "API call failed on attempt $attempt: $lastError")
                    
                    if (attempt < MAX_RETRIES) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1))
                        callback.onProgress(3, "Retrying... (attempt ${attempt + 1}/$MAX_RETRIES)")
                        delay(delayMs)
                        continue
                    }
                    callback.onError("Failed to regenerate learning path after $MAX_RETRIES attempts. Please try again later.")
                    return@withContext
                }

                callback.onProgress(4, "Processing AI response...")
                
                // Parse response
                val responseText = result.getOrNull() ?: ""
                stages = parseStagesFromResponse(responseText)
                
                if (stages.isNotEmpty()) {
                    Log.d(TAG, "Successfully parsed ${stages.size} stages on attempt $attempt")
                    break // Success!
                }
                
                // Parsing failed, retry
                lastError = "Failed to parse JSON response"
                Log.w(TAG, "Parse failed on attempt $attempt, ${if (attempt < MAX_RETRIES) "retrying..." else "giving up"}")
                
                if (attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1))
                    callback.onProgress(3, "Response parsing failed. Retrying... (attempt ${attempt + 1}/$MAX_RETRIES)")
                    delay(delayMs)
                }
            }
            
            // Check if we got valid stages after all retries
            if (stages.isEmpty()) {
                Log.e(TAG, "Failed to regenerate valid stages after $MAX_RETRIES attempts")
                callback.onError("Failed to regenerate learning path. The AI response could not be parsed. Please try again.")
                return@withContext
            }

            callback.onProgress(5, "Deleting old stages and resetting progress...")
            
            // Delete old stages first
            firestoreManager.deleteAllPersonalizedStages()
            
            // V2: Reset user's stage progress (completedStages, stageStars)
            // This ensures old progress doesn't apply to new stages
            val progressSyncService = UserProgressSyncService(context)
            progressSyncService.resetStageProgressInCloud()
            Log.d(TAG, "Reset user stage progress for new personalized stages")
            
            // Mark first stage as unlocked
            val unlockedStages = stages.mapIndexed { index, stage ->
                stage.copy(isUnlocked = index == 0)
            }
            
            // Save new stages to Firestore
            val saveResult = firestoreManager.savePersonalizedStages(unlockedStages)
            if (saveResult.isFailure) {
                Log.e(TAG, "Failed to save stages: ${saveResult.exceptionOrNull()?.message}")
                callback.onError("Failed to save learning path. Please try again.")
                return@withContext
            }

            Log.d(TAG, "Successfully regenerated and saved ${unlockedStages.size} stages")
            callback.onSuccess(unlockedStages)

        } catch (e: Exception) {
            Log.e(TAG, "Error regenerating stages", e)
            callback.onError("An error occurred: ${e.message}")
        }
    }
}

