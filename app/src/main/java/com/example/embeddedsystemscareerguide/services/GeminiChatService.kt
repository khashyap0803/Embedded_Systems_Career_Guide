package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI Chat Tutor Service - Powered by Gemini API
 * Provides embedded systems expertise for student Q&A
 */
class GeminiChatService {

    // M3 fix: Use shared client from NetworkModule
    private val client = NetworkModule.standardClient

    private val gson = Gson()

    // M6 fix: Use centralized API URL from NetworkModule
    private val GEMINI_API_URL = NetworkModule.getGeminiApiUrl()

    companion object {
        private const val TAG = "GeminiChatService"
        
        // System prompt for embedded systems tutor
        private const val SYSTEM_PROMPT = """
You are an expert Embedded Systems Tutor named "EmbedBot" 🤖. You help students learn embedded systems concepts.

YOUR EXPERTISE INCLUDES:
- Microcontrollers (ARM Cortex-M, AVR, PIC, 8051)
- C/C++ programming for embedded systems
- Communication protocols (UART, SPI, I2C, CAN, Ethernet)
- RTOS concepts (FreeRTOS, Zephyr)
- Digital electronics and circuit design
- Sensor interfacing and ADC/DAC
- Memory management and optimization
- Debugging techniques and tools
- IoT and wireless communication

RESPONSE GUIDELINES:
1. Be friendly, encouraging, and patient with beginners
2. Use simple language to explain complex concepts
3. Provide practical code examples when relevant (use C language)
4. Give real-world applications to make learning relatable
5. Keep responses concise but informative (under 300 words unless detailed explanation needed)
6. Use emojis sparingly to make responses engaging 🎯
7. If asked about non-embedded topics, politely redirect to embedded systems
8. Encourage hands-on practice with specific project suggestions

When providing code examples, format them properly for readability.
"""
    }

    // Chat history for context
    private val conversationHistory = mutableListOf<ChatMessage>()

    data class ChatMessage(
        val role: String, // "user" or "model"
        val content: String
    )

    /**
     * Send a message and get AI response
     */
    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending message: $userMessage")

            // Add user message to history
            conversationHistory.add(ChatMessage("user", userMessage))

            // Build conversation with system prompt
            val response = callGeminiAPI(userMessage)

            // Add response to history
            conversationHistory.add(ChatMessage("model", response))

            // Keep only last 10 exchanges to manage context length
            if (conversationHistory.size > 20) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            return@withContext response

        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            return@withContext "Sorry, I'm having trouble connecting right now. Please check your internet and try again! 🔄"
        }
    }

    /**
     * Make API call to Gemini
     */
    private suspend fun callGeminiAPI(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBody = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray()
                
                // Add system instruction
                val systemContent = JsonObject().apply {
                    addProperty("role", "user")
                    val partsArray = com.google.gson.JsonArray()
                    val partObject = JsonObject().apply {
                        addProperty("text", SYSTEM_PROMPT)
                    }
                    partsArray.add(partObject)
                    add("parts", partsArray)
                }
                contentsArray.add(systemContent)
                
                // Add model acknowledgment
                val ackContent = JsonObject().apply {
                    addProperty("role", "model")
                    val partsArray = com.google.gson.JsonArray()
                    val partObject = JsonObject().apply {
                        addProperty("text", "I understand. I'm EmbedBot, your friendly Embedded Systems Tutor! I'll help you learn about microcontrollers, programming, protocols, and more. How can I help you today? 🎯")
                    }
                    partsArray.add(partObject)
                    add("parts", partsArray)
                }
                contentsArray.add(ackContent)
                
                // Add conversation history
                for (message in conversationHistory.takeLast(10)) {
                    val contentObject = JsonObject().apply {
                        addProperty("role", if (message.role == "user") "user" else "model")
                        val partsArray = com.google.gson.JsonArray()
                        val partObject = JsonObject().apply {
                            addProperty("text", message.content)
                        }
                        partsArray.add(partObject)
                        add("parts", partsArray)
                    }
                    contentsArray.add(contentObject)
                }
                
                add("contents", contentsArray)

                // Generation config
                val generationConfig = JsonObject().apply {
                    addProperty("temperature", 0.7)
                    addProperty("topK", 40)
                    addProperty("topP", 0.95)
                    addProperty("maxOutputTokens", 1024)
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
                Log.e(TAG, "API Error: ${response.code} - $responseBody")
                throw Exception("API call failed: ${response.code}")
            }

            // Parse response
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            val content = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")[0].asJsonObject
                .get("text").asString

            return@withContext content

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            throw e
        }
    }

    /**
     * Clear conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Get suggested topics for new users
     */
    fun getSuggestedTopics(): List<String> {
        return listOf(
            "What is an embedded system?",
            "Explain GPIO in microcontrollers",
            "How does I2C communication work?",
            "What is RTOS and when to use it?",
            "How to debounce a button in C?",
            "Explain interrupt handling",
            "What are timers used for?",
            "PWM basics for motor control"
        )
    }
}
