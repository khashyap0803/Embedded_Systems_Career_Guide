package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FlashcardService - AI-Powered Flashcard Generator
 * 
 * Generates spaced-repetition flashcards for each stage.
 * Supports swipe-based review with "Know It" and "Need Review" actions.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class FlashcardService(private val context: Context) {

    companion object {
        private const val TAG = "FlashcardService"
        private const val DEFAULT_CARD_COUNT = 15
        
        @Volatile
        private var instance: FlashcardService? = null
        
        fun getInstance(context: Context): FlashcardService {
            return instance ?: synchronized(this) {
                instance ?: FlashcardService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Callback interface for flashcard generation
     */
    interface FlashcardCallback {
        fun onProgress(message: String)
        fun onSuccess(flashcards: List<Flashcard>)
        fun onError(error: String)
    }

    /**
     * Get or generate flashcards for a stage
     */
    suspend fun getFlashcards(
        stage: PersonalizedStage,
        callback: FlashcardCallback
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting flashcards for stage ${stage.id}: ${stage.title}")
            callback.onProgress("Loading flashcards...")

            // Check if flashcards already exist
            val existing = firestoreManager.getFlashcards(stage.id)
            if (existing.isSuccess) {
                val flashcards = existing.getOrNull() ?: emptyList()
                if (flashcards.isNotEmpty()) {
                    Log.d(TAG, "Found ${flashcards.size} cached flashcards for stage ${stage.id}")
                    callback.onSuccess(flashcards)
                    return@withContext
                }
            }

            // Generate new flashcards
            callback.onProgress("Generating flashcards with AI...")
            generateAndCacheFlashcards(stage, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting flashcards", e)
            callback.onError("Failed to load flashcards: ${e.message}")
        }
    }

    /**
     * Generate flashcards using AI and cache in Firestore
     */
    private suspend fun generateAndCacheFlashcards(
        stage: PersonalizedStage,
        callback: FlashcardCallback
    ) {
        try {
            val prompt = GeminiServiceV2.PromptTemplates.flashcards(
                stageName = stage.title,
                topics = stage.topics,
                count = DEFAULT_CARD_COUNT
            )

            val result = geminiService.generateContent(prompt, maxOutputTokens = 4096)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                val fallback = createFallbackFlashcards(stage)
                firestoreManager.saveFlashcards(stage.id, fallback)
                callback.onSuccess(fallback)
                return
            }

            callback.onProgress("Processing AI response...")
            
            val responseText = result.getOrNull() ?: ""
            val flashcards = parseFlashcardsFromResponse(responseText)
            
            if (flashcards.isEmpty()) {
                Log.w(TAG, "Failed to parse flashcards, using fallback")
                val fallback = createFallbackFlashcards(stage)
                firestoreManager.saveFlashcards(stage.id, fallback)
                callback.onSuccess(fallback)
                return
            }

            firestoreManager.saveFlashcards(stage.id, flashcards)
            Log.d(TAG, "Generated and cached ${flashcards.size} flashcards for stage ${stage.id}")
            callback.onSuccess(flashcards)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating flashcards", e)
            val fallback = createFallbackFlashcards(stage)
            callback.onSuccess(fallback)
        }
    }

    /**
     * Parse AI response into Flashcard objects
     */
    private fun parseFlashcardsFromResponse(response: String): List<Flashcard> {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val flashcardsArray = jsonObject.getAsJsonArray("flashcards")
            
            val flashcards = mutableListOf<Flashcard>()
            
            flashcardsArray?.forEachIndexed { index, element ->
                try {
                    val obj = element.asJsonObject
                    flashcards.add(Flashcard(
                        id = obj.get("id")?.asInt ?: (index + 1),
                        front = obj.get("front")?.asString ?: "Question ${index + 1}",
                        back = obj.get("back")?.asString ?: "Answer",
                        difficulty = obj.get("difficulty")?.asString ?: "medium",
                        category = obj.get("category")?.asString ?: "concept",
                        needsReview = false
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse flashcard at index $index: ${e.message}")
                }
            }
            
            flashcards
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse flashcards response", e)
            emptyList()
        }
    }

    /**
     * Create fallback flashcards if AI generation fails
     */
    private fun createFallbackFlashcards(stage: PersonalizedStage): List<Flashcard> {
        val title = stage.title.lowercase()
        
        // Generic embedded systems flashcards based on common topics
        val genericCards = listOf(
            Flashcard(1, "What is an embedded system?", "A specialized computing system designed to perform dedicated functions within larger mechanical or electrical systems, optimized for specific tasks with real-time constraints.", "easy", "concept"),
            Flashcard(2, "What does 'volatile' keyword mean in embedded C?", "It tells the compiler that a variable's value may change at any time without any action being taken by the code. Used for hardware registers and interrupt-shared variables.", "medium", "code"),
            Flashcard(3, "Why is the main loop in embedded systems typically an infinite loop?", "Embedded systems run continuously without an operating system to return to. The infinite loop ensures the system keeps running its core functionality.", "easy", "concept"),
            Flashcard(4, "What is GPIO?", "General Purpose Input/Output - Digital pins on a microcontroller that can be configured as either inputs or outputs for interfacing with external hardware.", "easy", "concept"),
            Flashcard(5, "Difference between polling and interrupts?", "Polling: CPU continuously checks status (wastes cycles). Interrupts: Hardware signals CPU when attention needed (more efficient, allows other work).", "medium", "concept"),
            Flashcard(6, "What is a watchdog timer?", "A safety mechanism that resets the system if the main program fails to periodically 'kick' or 'feed' the timer, preventing system hangs.", "medium", "concept"),
            Flashcard(7, "Why use bit manipulation in embedded systems?", "To control individual bits in hardware registers efficiently. Important for GPIO, peripheral configuration, and memory-constrained systems.", "medium", "code"),
            Flashcard(8, "What is UART?", "Universal Asynchronous Receiver/Transmitter - Serial communication protocol for point-to-point data transfer using TX and RX lines, commonly at 9600-115200 baud.", "easy", "concept"),
            Flashcard(9, "Difference between I2C and SPI?", "I2C: 2-wire (SDA/SCL), multi-master, slower, uses addresses. SPI: 4-wire (MOSI/MISO/SCK/SS), faster, uses chip-select lines, simpler protocol.", "hard", "concept"),
            Flashcard(10, "What is debouncing?", "Technique to handle mechanical switch bounce - multiple rapid on/off transitions when pressed. Solutions include delays, hardware capacitors, or software state machines.", "medium", "application"),
            Flashcard(11, "What is PWM used for?", "Pulse Width Modulation - Controls analog-like behavior using digital signals. Used for LED dimming, motor speed control, and audio generation.", "easy", "application"),
            Flashcard(12, "What is the purpose of a pull-up/pull-down resistor?", "To define a default logic level (HIGH or LOW) for a pin when no active signal is present, preventing floating/undefined states.", "medium", "concept"),
            Flashcard(13, "What is an ADC?", "Analog-to-Digital Converter - Converts continuous analog voltage signals into discrete digital values that the microcontroller can process.", "easy", "concept"),
            Flashcard(14, "Name 3 common ARM Cortex-M core features", "Nested Vectored Interrupt Controller (NVIC), SysTick timer, low-latency interrupt handling, Thumb-2 instruction set.", "hard", "concept"),
            Flashcard(15, "What is RTOS and when would you use it?", "Real-Time Operating System - Provides task scheduling, synchronization, and timing guarantees. Used when multiple concurrent tasks need precise timing.", "hard", "concept")
        )
        
        return genericCards
    }

    /**
     * Update flashcard review status after user swipe
     */
    suspend fun updateFlashcardReview(
        stageId: Int,
        flashcardId: Int,
        needsReview: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestoreManager.updateFlashcardReview(stageId, flashcardId, needsReview)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating flashcard review status", e)
            Result.failure(e)
        }
    }

    /**
     * Get flashcards that need review
     */
    suspend fun getFlashcardsNeedingReview(stageId: Int): Result<List<Flashcard>> = 
        withContext(Dispatchers.IO) {
        try {
            val result = firestoreManager.getFlashcards(stageId)
            if (result.isSuccess) {
                val needReview = result.getOrNull()?.filter { it.needsReview } ?: emptyList()
                Result.success(needReview)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting flashcards needing review", e)
            Result.failure(e)
        }
    }

    /**
     * Get all flashcards across all stages that need review (for spaced repetition)
     */
    suspend fun getAllCardsNeedingReview(): Result<Map<Int, List<Flashcard>>> = 
        withContext(Dispatchers.IO) {
        try {
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            val reviewMap = mutableMapOf<Int, List<Flashcard>>()
            
            stages.filter { it.isCompleted }.forEach { stage ->
                val flashcards = firestoreManager.getFlashcards(stage.id).getOrNull() ?: emptyList()
                val needReview = flashcards.filter { it.needsReview }
                if (needReview.isNotEmpty()) {
                    reviewMap[stage.id] = needReview
                }
            }
            
            Result.success(reviewMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all cards needing review", e)
            Result.failure(e)
        }
    }

    /**
     * Regenerate flashcards for a stage
     */
    suspend fun regenerateFlashcards(
        stage: PersonalizedStage,
        callback: FlashcardCallback
    ) = withContext(Dispatchers.IO) {
        callback.onProgress("Regenerating flashcards with AI...")
        generateAndCacheFlashcards(stage, callback)
    }
}
