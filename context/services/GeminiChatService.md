# GeminiChatService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiChatService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 8,066 bytes (212 lines)

---

## What This File Does (Simple Explanation)

This is the **AI Chat Tutor backend**. It powers the "EmbedBot" — an AI tutor that students can have conversations with about embedded systems. It maintains conversation history so the AI remembers what was discussed earlier, sends messages to the Gemini API, and returns the AI's responses.

The AI is personality-configured via a system prompt to be:
- Named "EmbedBot" 🤖
- Expert in microcontrollers, protocols, RTOS, debugging
- Friendly, encouraging, and beginner-friendly
- Focused on embedded systems (redirects off-topic questions)

---

## Why This File Exists

The chat tutor is one of the app's core features, allowing students to ask questions in natural language and get expert-level answers about embedded systems topics.

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `ChatFragment.kt` | Creates instance, calls `sendMessage()`, `clearHistory()`, `getSuggestedTopics()` |

---

## Complete Code Walkthrough

### Lines 19-22: Class & HTTP Client
```kotlin
class GeminiChatService {
    private val client = NetworkModule.standardClient
```
Uses the shared OkHttpClient from `NetworkModule` (labeled "M3 fix"). Not a singleton — each `ChatFragment` creates its own instance (which means each chat session has its own history).

### Lines 29-58: System Prompt
The `SYSTEM_PROMPT` constant defines the AI's personality and behavior:
- Name: "EmbedBot" 🤖
- Expertise: 9 areas (MCUs, C/C++, protocols, RTOS, electronics, sensors, memory, debugging, IoT)
- 8 response guidelines (friendly, simple language, code examples, under 300 words, etc.)
- Redirects off-topic questions back to embedded systems

### Lines 62-67: Conversation History
```kotlin
private val conversationHistory = mutableListOf<ChatMessage>()

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String
)
```
In-memory list of messages. Each has a role ("user" for human, "model" for AI) and content.

### Lines 72-97: `sendMessage()` — Main Entry Point
1. Adds user message to `conversationHistory`
2. Calls `callGeminiAPI()` with the message
3. Adds AI response to `conversationHistory`
4. **Keeps only last 10 exchanges** (20 messages total) — removes oldest pair when limit exceeded
5. Returns response on error: "Sorry, I'm having trouble connecting right now..." with 🔄 emoji

### Lines 102-187: `callGeminiAPI()` — Raw API Call
Builds the Gemini API request JSON manually:

1. **System instruction** (Line 108-117): Sends system prompt as first "user" message
2. **Model acknowledgment** (Line 120-129): Fake model response acknowledging the system prompt
3. **Conversation history** (Line 132-143): Last 10 messages from history
4. **Generation config** (Lines 148-154):
   - `temperature: 0.7` — balanced creativity
   - `topK: 40` — considers top 40 tokens
   - `topP: 0.95` — nucleus sampling
   - `maxOutputTokens: 1024` — ~750 words max response

5. Sends POST request to Gemini API
6. Parses response: `candidates[0].content.parts[0].text`

### Lines 192-194: `clearHistory()`
Resets conversation history for a fresh chat.

### Lines 199-210: `getSuggestedTopics()`
Returns 8 starter questions for new users:
1. "What is an embedded system?"
2. "Explain GPIO in microcontrollers"
3. "How does I2C communication work?"
4. "What is RTOS and when to use it?"
5. "How to debounce a button in C?"
6. "Explain interrupt handling"
7. "What are timers used for?"
8. "PWM basics for motor control"

---

## Dependencies

| Import | Why |
|--------|-----|
| `NetworkModule` | Shared HTTP client and API URL |
| `BuildConfig` | (imported but API key accessed via NetworkModule) |
| `Gson`, `JsonObject`, `JsonArray` | JSON construction and parsing |
| `OkHttp` | HTTP requests |
| `Dispatchers.IO` | Background threading |

---

## Connections to Other Files

```
ChatFragment.kt ──creates──► GeminiChatService
                                    │
                              sendMessage()
                                    │
                                    ▼
                            NetworkModule.standardClient
                                    │
                              Gemini API Call
                                    │
                                    ▼
                              Parse Response
                                    │
                                    ▼
                            Return to ChatFragment
```

---

## Strengths

- ✅ Maintains conversation context (last 10 exchanges)
- ✅ Well-crafted system prompt with clear persona
- ✅ Graceful error handling with user-friendly message
- ✅ Uses shared NetworkModule client

## Weaknesses / Technical Debt

- ⚠️ System prompt sent as "user" message, not using Gemini's `systemInstruction` field
- ⚠️ History trimming removes from index 0 twice — should remove pairs properly
- ⚠️ Not a singleton — multiple instances possible (intentional for separate chat sessions)
- ⚠️ No input sanitization — should use `InputSanitizer.sanitizeForApi()`
- ⚠️ Manual JSON building — error-prone; could use the Gemini SDK
- ⚠️ Synchronous API call (`execute()`) — should use `enqueue()` for better cancellation
