package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * AI Chat Tutor Service - Powered by local Ollama LLM
 * Provides embedded systems expertise for student Q&A
 */
class GeminiChatService {

    private val client = NetworkModule.standardClient
    private val gson = Gson()

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
            val response = callOllamaAPI(userMessage)

            // Add response to history
            conversationHistory.add(ChatMessage("model", response))

            // Keep only last 10 exchanges to manage context length
            if (conversationHistory.size > 20) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            return@withContext response

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Server timeout", e)
            return@withContext NetworkModule.SERVER_DOWN_MESSAGE
        } catch (e: ConnectException) {
            Log.e(TAG, "Connection failed", e)
            return@withContext NetworkModule.SERVER_DOWN_MESSAGE
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            return@withContext NetworkModule.SERVER_DOWN_MESSAGE
        }
    }

    /**
     * Make API call to Ollama /api/chat (multi-turn)
     */
    private suspend fun callOllamaAPI(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            // Build messages array for Ollama chat API
            val messagesArray = com.google.gson.JsonArray()
            
            // System message
            val systemMsg = JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", SYSTEM_PROMPT)
            }
            messagesArray.add(systemMsg)
            
            // Add conversation history
            for (message in conversationHistory.takeLast(10)) {
                val msgObj = JsonObject().apply {
                    addProperty("role", if (message.role == "user") "user" else "assistant")
                    addProperty("content", message.content)
                }
                messagesArray.add(msgObj)
            }

            val requestBody = JsonObject().apply {
                addProperty("model", NetworkModule.DEFAULT_MODEL)
                add("messages", messagesArray)
                addProperty("stream", false)
                add("options", JsonObject().apply {
                    addProperty("temperature", 0.7)
                    addProperty("num_predict", 1024)
                })
            }

            val jsonBody = gson.toJson(requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(NetworkModule.getOllamaChatUrl())
                .post(body)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} - $responseBody")
                throw Exception("API call failed: ${response.code}")
            }

            // Parse Ollama chat response
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val content = jsonResponse
                .getAsJsonObject("message")
                ?.get("content")?.asString
                ?: throw Exception("No content in response")

            // Strip Qwen3 <think>...</think> reasoning blocks — they must not appear in UI
            val cleaned = content.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()

            return@withContext cleaned

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Ollama API", e)
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
