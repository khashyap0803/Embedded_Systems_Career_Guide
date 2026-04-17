package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * DailyTipService - AI-Powered Daily Learning Tips
 * 
 * Generates and delivers personalized daily tips based on
 * the user's current learning progress and weak areas.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class DailyTipService(private val context: Context) {

    companion object {
        private const val TAG = "DailyTipService"
        private const val TIPS_PER_GENERATION = 7  // Generate a week's worth
        
        @Volatile
        private var instance: DailyTipService? = null
        
        fun getInstance(context: Context): DailyTipService {
            return instance ?: synchronized(this) {
                instance ?: DailyTipService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = OllamaService.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Get today's tip, generating new ones if needed
     */
    suspend fun getTodaysTip(): Result<DailyTip> = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDateString()
            
            // Check for existing tip for today using FirestoreManager
            val existingTip = firestoreManager.getTodaysTip().getOrNull()
            
            if (existingTip != null) {
                Log.d(TAG, "Found existing tip for today")
                return@withContext Result.success(existingTip)
            }
            
            // Generate new tips if none for today
            Log.d(TAG, "No tip for today, generating new tips")
            val newTips = generateTips()
            
            if (newTips.isNotEmpty()) {
                // Save all generated tips
                newTips.forEach { tip ->
                    firestoreManager.saveDailyTip(tip)
                }
                return@withContext Result.success(newTips.first())
            }
            
            // Fallback tip
            val fallbackTip = createFallbackTip(todayDate)
            firestoreManager.saveDailyTip(fallbackTip)
            Result.success(fallbackTip)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's tip", e)
            Result.failure(e)
        }
    }

    /**
     * Generate tips based on user's current progress
     */
    private suspend fun generateTips(): List<DailyTip> {
        try {
            // Get user context for personalization
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            val currentStage = stages.find { !it.isCompleted }
            val completedTopics = stages.filter { it.isCompleted }.map { it.title }
            
            val prompt = OllamaService.PromptTemplates.dailyTips(
                currentTopic = currentStage?.title ?: "Embedded Systems",
                completedTopics = completedTopics,
                count = TIPS_PER_GENERATION
            )

            val result = geminiService.generateContent(prompt, maxOutputTokens = 2048)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                return createFallbackTips()
            }

            val responseText = result.getOrNull() ?: ""
            return parseTipsFromResponse(responseText)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating tips", e)
            return createFallbackTips()
        }
    }

    /**
     * Parse tips from AI response
     */
    private fun parseTipsFromResponse(response: String): List<DailyTip> {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val tipsArray = jsonObject.getAsJsonArray("tips")
            
            val tips = mutableListOf<DailyTip>()
            val calendar = Calendar.getInstance()
            
            tipsArray?.forEachIndexed { index, element ->
                try {
                    val obj = element.asJsonObject
                    
                    // Assign a date starting from today
                    calendar.add(Calendar.DAY_OF_YEAR, if (index == 0) 0 else 1)
                    val dateString = formatDateString(calendar)
                    
                    // Create tip with correct DailyTip data class fields
                    val title = obj.get("title")?.asString ?: "Pro Tip #${index + 1}"
                    val content = obj.get("content")?.asString ?: ""
                    val codeSnippet = obj.get("codeSnippet")?.asString ?: ""
                    
                    tips.add(DailyTip(
                        date = dateString,
                        tip = "**$title**\n\n$content" + if (codeSnippet.isNotEmpty()) "\n\n```c\n$codeSnippet\n```" else "",
                        category = obj.get("category")?.asString ?: "general",
                        actionItem = "Practice this concept in your next project"
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse tip at index $index")
                }
            }
            
            tips
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tips response", e)
            emptyList()
        }
    }

    /**
     * Create fallback tips if AI generation fails
     */
    private fun createFallbackTips(): List<DailyTip> {
        val calendar = Calendar.getInstance()
        val tips = mutableListOf<DailyTip>()
        
        val fallbackContent = listOf(
            Triple(
                "Use volatile Wisely",
                "Always declare hardware register pointers and ISR-shared variables as volatile. This tells the compiler not to optimize away reads or writes that may seem unnecessary but are actually critical for hardware interaction.",
                "volatile uint32_t *gpio_port = (volatile uint32_t *)0x40020014;"
            ),
            Triple(
                "Avoid Magic Numbers",
                "Define all constants with meaningful names using #define or const. This makes your code self-documenting and easier to maintain. Future you will thank present you!",
                "#define LED_PIN       5\n#define DEBOUNCE_MS   50\n#define BAUD_RATE     115200"
            ),
            Triple(
                "Initialize Everything",
                "Always initialize variables before use, especially in embedded systems. Uninitialized variables can have unpredictable values that cause hard-to-debug issues.",
                "uint8_t counter = 0;  // Always initialize!\nchar buffer[64] = {0};  // Zero-fill arrays"
            ),
            Triple(
                "Use Static for Local Persistence",
                "Static local variables retain their value between function calls without polluting the global namespace. Great for counters and state machines.",
                "void button_handler(void) {\n    static uint8_t press_count = 0;  // Persists!\n    press_count++;\n}"
            ),
            Triple(
                "Master Bit Manipulation",
                "Get comfortable with |=, &=, ^=, and ~. These are essential for register manipulation in embedded systems. Practice setting, clearing, and toggling specific bits.",
                "REG |= (1 << PIN);   // Set bit\nREG &= ~(1 << PIN);  // Clear bit\nREG ^= (1 << PIN);   // Toggle bit"
            ),
            Triple(
                "Add Debug Output Early",
                "Include debug serial output from the start of your project. It's much harder to add debugging later when things aren't working. A simple printf can save hours.",
                "#ifdef DEBUG\n  printf(\"State: %d, Value: 0x%X\\n\", state, value);\n#endif"
            ),
            Triple(
                "Understand Your Interrupt Latency",
                "Keep ISRs short and fast. Set a flag and process in main loop. Long ISRs can cause missed events and timing issues in real-time systems.",
                "volatile uint8_t event_flag = 0;\n\nISR(INT0_vect) {\n    event_flag = 1;  // Set flag, exit fast\n}"
            )
        )
        
        fallbackContent.forEachIndexed { index, (title, content, code) ->
            calendar.add(Calendar.DAY_OF_YEAR, if (index == 0) 0 else 1)
            tips.add(DailyTip(
                date = formatDateString(calendar),
                tip = "**$title**\n\n$content\n\n```c\n$code\n```",
                category = "programming",
                actionItem = "Try implementing this in your next project"
            ))
        }
        
        return tips
    }

    /**
     * Create a single fallback tip for today
     */
    private fun createFallbackTip(date: String): DailyTip {
        return DailyTip(
            date = date,
            tip = "**Embrace the Learning Process**\n\nEmbedded systems development has a steep learning curve, but every bug you fix and every project you complete builds your expertise. Keep experimenting, keep debugging, and keep building!",
            category = "motivation",
            actionItem = "Complete one stage today to continue your learning journey"
        )
    }

    /**
     * Get today's date as string (YYYY-MM-DD)
     */
    private fun getTodayDateString(): String {
        return formatDateString(Calendar.getInstance())
    }

    /**
     * Format calendar to date string
     */
    private fun formatDateString(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        return "$year-$month-$day"
    }
}
