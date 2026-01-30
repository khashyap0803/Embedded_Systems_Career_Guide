# ChatFragment.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/ui/chat/ChatFragment.kt`

## Purpose

**AI Chat Tutor** interface for conversational learning about embedded systems. Uses `GeminiChatService` for AI responses.

## Features

| Feature | Description |
|---------|-------------|
| Text Input | Send messages to AI |
| Message History | Scrollable conversation |
| Real-time Response | Streaming-like response display |
| Context Memory | AI remembers conversation context |

## Message Flow

```kotlin
// Send message
val response = chatService.sendMessage(userMessage)

// Add to adapter
adapter.addMessage(ChatMessage("user", userMessage))
adapter.addMessage(ChatMessage("assistant", response))
```

## UI Components

- RecyclerView for messages
- EditText for input
- Send button
- Typing indicator (placeholder)

## Strengths

- ✅ Clean chat interface
- ✅ Context-aware responses
- ✅ Specialized for embedded systems
- ✅ Error handling with retry

## Weaknesses

- ⚠️ No message persistence
- ⚠️ No voice input
- ⚠️ No code formatting

---

# PracticeFragment.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/ui/practice/PracticeFragment.kt`

## Purpose

**Practice mode** for quick quiz sessions outside the learning path. Currently shows "Coming Soon" for unimplemented features.

## Current State

```kotlin
binding.cardQuickPractice.setOnClickListener {
    showComingSoonToast("Quick Practice")
}
```

## Planned Features

- Quick 5-minute quizzes
- Topic-specific practice
- Challenge mode
- Review mistakes

## Data Loading

Uses `UserProgressSyncService.loadProgressFromCloud()` for XP/stage display.

---

# ProfileFragment.kt

User profile display with:
- Username and email
- Total XP and level
- Best streak
- Logout option

---

# SettingsFragment.kt

App settings with:
- Theme toggle
- Notification preferences
- Cache clearing
- About section
