package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StageGeneratorService - AI-Powered Personalized Stage Generator
 * 
 * Generates personalized learning stages based on assessment results.
 * Uses GeminiServiceV2 to create a customized curriculum of 30-50 stages
 * tailored to the student's strengths and weaknesses.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class StageGeneratorService(private val context: Context) {

    companion object {
        private const val TAG = "StageGeneratorService"
        private const val TARGET_STAGES = 40  // Target number of stages
        
        @Volatile
        private var instance: StageGeneratorService? = null
        
        fun getInstance(context: Context): StageGeneratorService {
            return instance ?: synchronized(this) {
                instance ?: StageGeneratorService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
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

            // Generate prompt using template
            val prompt = GeminiServiceV2.PromptTemplates.personalizedStages(
                userName = userName,
                weakAreas = weakAreas,
                strongAreas = strongAreas,
                targetStageCount = TARGET_STAGES
            )

            // Call Gemini API
            val result = geminiService.generateContent(prompt, maxOutputTokens = 8192)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                callback.onError("Failed to generate learning path. Please try again.")
                return@withContext
            }

            callback.onProgress(3, "Processing AI response...")
            
            // Parse response
            val responseText = result.getOrNull() ?: ""
            val stages = parseStagesFromResponse(responseText)
            
            if (stages.isEmpty()) {
                Log.e(TAG, "Failed to parse stages from response")
                // Use fallback stages
                val fallbackStages = createFallbackStages(weakAreas, strongAreas)
                callback.onProgress(4, "Saving your learning path...")
                firestoreManager.savePersonalizedStages(fallbackStages)
                callback.onSuccess(fallbackStages)
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
     */
    private fun parseStagesFromResponse(response: String): List<PersonalizedStage> {
        return try {
            // Clean up response - remove markdown code blocks if present
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            // Try to parse as JSON object with "stages" array
            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val stagesArray = jsonObject.getAsJsonArray("stages")
            
            val stages = mutableListOf<PersonalizedStage>()
            
            stagesArray.forEach { element ->
                try {
                    val obj = element.asJsonObject
                    val stage = PersonalizedStage(
                        id = obj.get("id")?.asInt ?: (stages.size + 1),
                        title = obj.get("title")?.asString ?: "Stage ${stages.size + 1}",
                        subtitle = obj.get("subtitle")?.asString ?: "",
                        description = obj.get("description")?.asString ?: "",
                        topics = obj.getAsJsonArray("topics")?.map { it.asString } ?: emptyList(),
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
     * Create fallback stages if AI generation fails
     */
    private fun createFallbackStages(
        weakAreas: List<String>,
        strongAreas: List<String>
    ): List<PersonalizedStage> {
        val stages = mutableListOf<PersonalizedStage>()
        
        // Foundation stages (stages 1-10)
        val foundationTopics = listOf(
            "Introduction to Embedded Systems" to "Learn what embedded systems are and their applications",
            "C Programming Essentials" to "Master C syntax, data types, and control structures",
            "Pointers and Memory" to "Understanding pointers, addresses, and memory management",
            "Functions and Modular Programming" to "Writing reusable and maintainable code",
            "Structures and Unions" to "Organizing complex data in C",
            "Bit Manipulation" to "Working with individual bits for hardware control",
            "File I/O Basics" to "Reading and writing data for embedded applications",
            "Preprocessor Directives" to "Using macros and conditional compilation",
            "Building a Mini Project" to "Apply C fundamentals in a practical project",
            "C Programming Assessment" to "Test your C programming knowledge"
        )
        
        // Microcontroller stages (stages 11-20)
        val microcontrollerTopics = listOf(
            "Microcontroller Architecture" to "Understanding CPU, memory, and peripherals",
            "GPIO Programming" to "Controlling digital inputs and outputs",
            "Interrupts and Timers" to "Event-driven programming techniques",
            "PWM and Analog Signals" to "Generating and measuring analog signals",
            "ADC and DAC" to "Converting between analog and digital",
            "Memory Types" to "Flash, SRAM, EEPROM usage patterns",
            "Clock and Power Management" to "Optimizing power consumption",
            "Watchdog Timers" to "Ensuring system reliability",
            "LED Blinker Project" to "Your first microcontroller project",
            "Microcontroller Assessment" to "Test your MCU knowledge"
        )
        
        // Protocol stages (stages 21-28)
        val protocolTopics = listOf(
            "UART Communication" to "Serial communication fundamentals",
            "SPI Protocol" to "High-speed peripheral communication",
            "I2C Protocol" to "Two-wire sensor interfacing",
            "CAN Bus Basics" to "Automotive and industrial communication",
            "USB Fundamentals" to "Universal Serial Bus concepts",
            "Wireless Protocols" to "Bluetooth, WiFi, and Zigbee",
            "Protocol Selection Guide" to "Choosing the right protocol",
            "Multi-Protocol Project" to "Integrate multiple protocols"
        )
        
        // RTOS stages (stages 29-35)
        val rtosTopics = listOf(
            "RTOS Concepts" to "Tasks, scheduling, and real-time requirements",
            "Task Management" to "Creating and managing tasks",
            "Synchronization" to "Semaphores, mutexes, and queues",
            "Memory Management in RTOS" to "Dynamic allocation strategies",
            "Inter-task Communication" to "Message passing and shared resources",
            "FreeRTOS Hands-on" to "Practical RTOS implementation",
            "RTOS Project" to "Build a multi-tasking application"
        )
        
        // Advanced stages (stages 36-40)
        val advancedTopics = listOf(
            "Debugging Techniques" to "Using JTAG, SWD, and logic analyzers",
            "Power Optimization" to "Low-power design strategies",
            "IoT Integration" to "Connecting embedded devices to cloud",
            "Industry Best Practices" to "Professional development workflows",
            "Capstone Project" to "Complete end-to-end embedded project"
        )
        
        var stageId = 1
        
        // Add foundation stages
        foundationTopics.forEach { (title, desc) ->
            stages.add(PersonalizedStage(
                id = stageId++,
                title = title,
                subtitle = if (stageId <= 10) "C Fundamentals" else "Assessment",
                description = desc,
                topics = listOf(title.split(" ").take(2).joinToString(" ")),
                difficulty = if (stageId <= 5) "beginner" else "intermediate",
                estimatedMinutes = if (title.contains("Project") || title.contains("Assessment")) 90 else 60,
                type = when {
                    title.contains("Project") -> "project"
                    title.contains("Assessment") -> "quiz"
                    else -> "theory"
                },
                xpReward = if (title.contains("Project") || title.contains("Assessment")) 150 else 100,
                isCompleted = false,
                starsEarned = 0,
                isUnlocked = stageId == 2  // First stage unlocked
            ))
        }
        
        // Add microcontroller stages
        microcontrollerTopics.forEach { (title, desc) ->
            stages.add(PersonalizedStage(
                id = stageId++,
                title = title,
                subtitle = "Microcontrollers",
                description = desc,
                topics = listOf(title.split(" ").take(2).joinToString(" ")),
                difficulty = "intermediate",
                estimatedMinutes = if (title.contains("Project") || title.contains("Assessment")) 90 else 60,
                type = when {
                    title.contains("Project") -> "project"
                    title.contains("Assessment") -> "quiz"
                    else -> "theory"
                },
                xpReward = if (title.contains("Project") || title.contains("Assessment")) 150 else 100
            ))
        }
        
        // Add protocol stages
        protocolTopics.forEach { (title, desc) ->
            stages.add(PersonalizedStage(
                id = stageId++,
                title = title,
                subtitle = "Protocols",
                description = desc,
                topics = listOf(title.split(" ").take(2).joinToString(" ")),
                difficulty = "intermediate",
                estimatedMinutes = if (title.contains("Project")) 90 else 60,
                type = if (title.contains("Project")) "project" else "theory",
                xpReward = if (title.contains("Project")) 150 else 100
            ))
        }
        
        // Add RTOS stages
        rtosTopics.forEach { (title, desc) ->
            stages.add(PersonalizedStage(
                id = stageId++,
                title = title,
                subtitle = "RTOS",
                description = desc,
                topics = listOf(title.split(" ").take(2).joinToString(" ")),
                difficulty = "advanced",
                estimatedMinutes = if (title.contains("Project")) 120 else 75,
                type = if (title.contains("Project")) "project" else "theory",
                xpReward = if (title.contains("Project")) 200 else 125
            ))
        }
        
        // Add advanced stages
        advancedTopics.forEach { (title, desc) ->
            stages.add(PersonalizedStage(
                id = stageId++,
                title = title,
                subtitle = "Advanced",
                description = desc,
                topics = listOf(title.split(" ").take(2).joinToString(" ")),
                difficulty = "advanced",
                estimatedMinutes = if (title.contains("Project")) 180 else 90,
                type = if (title.contains("Project")) "project" else "theory",
                xpReward = if (title.contains("Project")) 300 else 150
            ))
        }
        
        Log.d(TAG, "Created ${stages.size} fallback stages")
        return stages
    }

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
     * Regenerate stages with updated assessment
     */
    suspend fun regenerateStages(
        userName: String,
        assessmentResult: AssessmentResult,
        callback: GenerationCallback
    ) {
        // Simply call generatePersonalizedStages - it will overwrite existing stages
        generatePersonalizedStages(userName, assessmentResult, callback)
    }
}
