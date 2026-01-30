# GeminiChatService.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiChatService.kt`

## Purpose

Powers the **AI Chat Tutor** feature, allowing users to have conversational interactions about embedded systems topics. Maintains context across messages for coherent conversations.

## Functionality

### Core Methods

```kotlin
suspend fun sendMessage(message: String): String
fun clearConversation()
```

### Conversation Management

```kotlin
private val conversationHistory = mutableListOf<Message>()

data class Message(
    val role: String,      // "user" or "model"
    val content: String
)
```

## System Prompt

```kotlin
"""
You are an expert embedded systems tutor. You help students learn about:
- Microcontrollers and microprocessors
- C/C++ programming for embedded systems
- RTOS concepts
- Hardware interfaces (SPI, I2C, UART)
- Memory management
- Debugging techniques

Be encouraging, clear, and provide practical examples.
Keep responses concise but informative.
"""
```

## Why It's Important

1. **Interactive Learning**: Real-time Q&A with AI
2. **Context Awareness**: Remembers conversation history
3. **Expert Knowledge**: Specialized for embedded systems
4. **24/7 Availability**: Always available to help

## Where It's Used

| Component | Usage |
|-----------|-------|
| ChatFragment | Main chat interface |

## Message Flow

```
┌──────────┐    ┌─────────────────┐    ┌────────────┐
│   User   │───►│ GeminiChatSvc   │───►│ Gemini API │
│  Input   │    │ + History       │    │            │
└──────────┘    └─────────────────┘    └────────────┘
                        │                      │
                        ◄──────────────────────┘
                        AI Response
```

## Strengths

- ✅ Maintains conversation context
- ✅ Specialized for embedded systems
- ✅ Clean API interface
- ✅ Error handling with user-friendly messages

## Weaknesses

- ⚠️ No conversation persistence
- ⚠️ Limited to ~10 messages before truncation
- ⚠️ No typing indicators
- ⚠️ No message history save

## Potential Improvements

1. **Persist conversations** to Firestore
2. **Add streaming responses** for better UX
3. **Implement message reactions** (helpful/not helpful)
4. **Add code syntax highlighting** in responses
