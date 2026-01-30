package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StageContentService - AI-Powered Learning Content Generator
 * 
 * Generates comprehensive learning content for each stage on-demand.
 * Content is generated once and cached in Firestore for future access.
 * Provides Kindle-style reading experience with rich educational material.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class StageContentService(private val context: Context) {

    companion object {
        private const val TAG = "StageContentService"
        
        @Volatile
        private var instance: StageContentService? = null
        
        fun getInstance(context: Context): StageContentService {
            return instance ?: synchronized(this) {
                instance ?: StageContentService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Callback interface for content generation progress
     */
    interface ContentCallback {
        fun onProgress(message: String)
        fun onSuccess(content: StageContent)
        fun onError(error: String)
    }

    /**
     * Get or generate content for a stage
     * First checks Firestore cache, generates if not found
     */
    suspend fun getStageContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting content for stage ${stage.id}: ${stage.title}")
            callback.onProgress("Loading content...")

            // Check if content already exists in Firestore
            val existingContent = firestoreManager.getStageContent(stage.id)
            if (existingContent.isSuccess && existingContent.getOrNull() != null) {
                Log.d(TAG, "Found cached content for stage ${stage.id}")
                callback.onSuccess(existingContent.getOrThrow()!!)
                return@withContext
            }

            // Generate new content
            callback.onProgress("Generating learning content with AI...")
            generateAndCacheContent(stage, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting stage content", e)
            callback.onError("Failed to load content: ${e.message}")
        }
    }

    /**
     * Generate content using AI and cache in Firestore
     */
    private suspend fun generateAndCacheContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) {
        try {
            // Build prompt using template
            val prompt = GeminiServiceV2.PromptTemplates.stageContent(
                stageName = stage.title,
                topics = stage.topics
            )

            // Call Gemini API
            val result = geminiService.generateContent(prompt, maxOutputTokens = 4096)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                // Return fallback content
                val fallback = createFallbackContent(stage)
                firestoreManager.saveStageContent(stage.id, fallback)
                callback.onSuccess(fallback)
                return
            }

            callback.onProgress("Processing AI response...")
            
            // Parse response
            val responseText = result.getOrNull() ?: ""
            val content = parseContentFromResponse(responseText, stage.id)
            
            if (content == null) {
                Log.w(TAG, "Failed to parse content, using fallback")
                val fallback = createFallbackContent(stage)
                firestoreManager.saveStageContent(stage.id, fallback)
                callback.onSuccess(fallback)
                return
            }

            // Save to Firestore
            firestoreManager.saveStageContent(stage.id, content)
            Log.d(TAG, "Successfully generated and cached content for stage ${stage.id}")
            callback.onSuccess(content)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            val fallback = createFallbackContent(stage)
            callback.onSuccess(fallback)
        }
    }

    /**
     * Parse AI response into StageContent object
     */
    private fun parseContentFromResponse(response: String, stageId: Int): StageContent? {
        return try {
            // Clean up response
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)

            // Parse key points
            val keyPoints = jsonObject.getAsJsonArray("keyPoints")?.map { 
                it.asString 
            } ?: emptyList()

            // Parse code example
            val codeExampleObj = jsonObject.getAsJsonObject("codeExample")
            val codeExample = CodeExample(
                language = codeExampleObj?.get("language")?.asString ?: "c",
                code = codeExampleObj?.get("code")?.asString ?: "",
                explanation = codeExampleObj?.get("explanation")?.asString ?: ""
            )

            // Parse common mistakes
            val mistakesArray = jsonObject.getAsJsonArray("commonMistakes")
            val commonMistakes = mistakesArray?.map { elem ->
                val obj = elem.asJsonObject
                Mistake(
                    mistake = obj.get("mistake")?.asString ?: "",
                    solution = obj.get("solution")?.asString ?: ""
                )
            } ?: emptyList()

            // Parse pro tips
            val proTips = jsonObject.getAsJsonArray("proTips")?.map { 
                it.asString 
            } ?: emptyList()

            // Parse mini challenge
            val challengeObj = jsonObject.getAsJsonObject("miniChallenge")
            val miniChallenge = Challenge(
                task = challengeObj?.get("task")?.asString ?: "",
                hint = challengeObj?.get("hint")?.asString ?: ""
            )

            StageContent(
                stageId = stageId,
                theory = jsonObject.get("theory")?.asString ?: "",
                keyPoints = keyPoints,
                codeExample = codeExample,
                commonMistakes = commonMistakes,
                proTips = proTips,
                miniChallenge = miniChallenge,
                generatedAt = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse content response", e)
            null
        }
    }

    /**
     * Create fallback content if AI generation fails
     */
    private fun createFallbackContent(stage: PersonalizedStage): StageContent {
        val topicsText = stage.topics.joinToString(", ")
        
        return StageContent(
            stageId = stage.id,
            theory = """
                # ${stage.title}
                
                ${stage.description}
                
                ## Overview
                
                This stage covers the following topics: $topicsText.
                
                Embedded systems are specialized computing systems designed to perform dedicated functions
                within larger mechanical or electrical systems. Unlike general-purpose computers, embedded
                systems are optimized for specific tasks, often with real-time computing constraints.
                
                ## Key Concepts
                
                In this module, you'll learn about the fundamental concepts related to ${stage.title.lowercase()}.
                These concepts form the building blocks for more advanced embedded systems development.
                
                Understanding these principles is essential for:
                - Writing efficient, reliable embedded code
                - Interfacing with hardware peripherals
                - Debugging and troubleshooting embedded systems
                - Building production-ready embedded applications
                
                ## Practical Applications
                
                The concepts covered in this stage are widely used in:
                - Consumer electronics (smartphones, smartwatches)
                - Automotive systems (engine control, infotainment)
                - Industrial automation (PLCs, robotics)
                - Medical devices (pacemakers, monitors)
                - IoT applications (smart home, wearables)
                
                Take your time to understand each concept thoroughly before moving on.
                Practice with the code examples and complete the mini-challenge to reinforce your learning.
            """.trimIndent(),
            keyPoints = listOf(
                "Embedded systems are designed for specific, dedicated functions",
                "Resource constraints (memory, processing power) require optimized code",
                "Real-time requirements often influence design decisions",
                "Hardware-software integration is a key aspect of embedded development",
                "Testing and debugging embedded systems requires specialized tools",
                "Documentation and code quality are essential for maintainability"
            ),
            codeExample = CodeExample(
                language = "c",
                code = """
// Example: Basic embedded systems pattern
#include <stdint.h>

// Define hardware register addresses
#define GPIO_BASE    0x40020000
#define GPIO_MODER   (*(volatile uint32_t *)(GPIO_BASE + 0x00))
#define GPIO_ODR     (*(volatile uint32_t *)(GPIO_BASE + 0x14))

// Pin definitions
#define LED_PIN      5

// Initialize GPIO for LED
void gpio_init(void) {
    // Set pin as output (mode = 01)
    GPIO_MODER &= ~(0x3 << (LED_PIN * 2));  // Clear bits
    GPIO_MODER |= (0x1 << (LED_PIN * 2));   // Set output mode
}

// Toggle LED state
void led_toggle(void) {
    GPIO_ODR ^= (1 << LED_PIN);  // XOR to toggle
}

// Simple delay (not production-ready)
void delay(volatile uint32_t count) {
    while(count--);
}

int main(void) {
    gpio_init();
    
    while(1) {
        led_toggle();
        delay(1000000);
    }
    
    return 0;  // Never reached
}
                """.trimIndent(),
                explanation = "This code demonstrates a basic embedded systems pattern: initializing hardware registers and implementing a simple control loop. Notice the use of volatile for hardware registers and the infinite main loop, which is typical in embedded systems."
            ),
            commonMistakes = listOf(
                Mistake(
                    mistake = "Forgetting to use 'volatile' for hardware registers",
                    solution = "Always declare hardware register pointers as volatile to prevent compiler optimizations that could skip reads/writes"
                ),
                Mistake(
                    mistake = "Using blocking delays in production code",
                    solution = "Use timer interrupts or RTOS delays instead of busy-wait loops for better CPU utilization"
                ),
                Mistake(
                    mistake = "Not initializing peripherals before use",
                    solution = "Always configure GPIO mode, clock, and other settings before accessing peripheral registers"
                )
            ),
            proTips = listOf(
                "Always check the datasheet for exact register addresses and bit positions",
                "Use header files provided by chip manufacturers when available",
                "Create abstraction layers to make code portable across different MCUs",
                "Use debugging tools like JTAG/SWD to step through code and inspect registers"
            ),
            miniChallenge = Challenge(
                task = "Modify the LED toggle example to blink the LED at exactly 1 Hz (1 second on, 1 second off) using a timer interrupt instead of a delay loop.",
                hint = "Look up the timer peripheral for your target MCU. You'll need to configure the timer prescaler and period to generate 1-second intervals."
            ),
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Force regenerate content for a stage
     */
    suspend fun regenerateContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) = withContext(Dispatchers.IO) {
        callback.onProgress("Regenerating content with AI...")
        generateAndCacheContent(stage, callback)
    }

    /**
     * Preload content for next few stages (background loading)
     */
    suspend fun preloadNextStages(currentStageId: Int, count: Int = 2) = withContext(Dispatchers.IO) {
        try {
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: return@withContext
            
            // Get next few stages after current
            val nextStages = stages.filter { it.id > currentStageId }.take(count)
            
            nextStages.forEach { stage ->
                // Check if already cached
                val existing = firestoreManager.getStageContent(stage.id)
                if (existing.isFailure || existing.getOrNull() == null) {
                    // Generate in background
                    Log.d(TAG, "Preloading content for stage ${stage.id}")
                    generateAndCacheContent(stage, object : ContentCallback {
                        override fun onProgress(message: String) {}
                        override fun onSuccess(content: StageContent) {
                            Log.d(TAG, "Preloaded stage ${stage.id}")
                        }
                        override fun onError(error: String) {
                            Log.w(TAG, "Failed to preload stage ${stage.id}: $error")
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading stages", e)
        }
    }
}
