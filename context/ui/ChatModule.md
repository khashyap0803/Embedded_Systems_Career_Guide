# Chat Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.chat`  
> **Files:** `ChatFragment.kt` (165 lines), `ChatAdapter.kt` (47 lines)

---

## ChatFragment.kt

### Purpose
AI tutor chat interface where users ask embedded-systems questions. Powered by `GeminiChatService` with input sanitisation and message-count limiting.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `ChatFragment` | `Fragment` | Chat UI host |
| `chatService` | `GeminiChatService` | Sends messages to Gemini API |
| `chatAdapter` | `ChatAdapter` | Renders messages in RecyclerView |
| `messages` | `MutableList<ChatMessage>` | In-memory chat history |
| `MAX_MESSAGES` | `Int` (companion, 100) | Memory-leak guard cap |

### Data Class
- **`ChatMessage(content: String, isUser: Boolean)`** — Single chat bubble.

### All Functions (8)

- **`onCreateView(inflater, container, savedInstanceState)`** — Inflates binding, creates `GeminiChatService`.
- **`onViewCreated(view, savedInstanceState)`** — Calls `setupRecyclerView()`, `setupInputHandlers()`, `setupSuggestionChips()`, `setupClearButton()`.
- **`setupRecyclerView()`** — Creates `ChatAdapter` with `LinearLayoutManager(stackFromEnd=true)`.
- **`setupInputHandlers()`** — Send button click → `sendMessage()`. Enter key (IME_ACTION_SEND) → `sendMessage()`.
- **`setupSuggestionChips()`** — 4 chips (`chipSuggestion1`–`chipSuggestion4`). Each sets its text into the input field and calls `sendMessage()`.
- **`setupClearButton()`** — Clears `messages` list, notifies adapter, calls `chatService.clearHistory()`, shows suggestion chips again.
- **`sendMessage()`** — Trims input, returns if empty. Sanitises via `InputSanitizer.sanitizeForApi(maxLength=2000)`. Hides suggestion chips. Adds user message (original, not sanitised). Shows loading. Launches coroutine → `chatService.sendMessage(sanitisedMessage)`. On success: adds AI response. On error: adds error message. Scrolls to bottom.
- **`addMessageWithLimit(message)`** — If messages ≥ `MAX_MESSAGES` (100), removes oldest message. Adds new message. Notifies adapter per-item.
- **`onDestroyView()`** — Nulls binding.

---

## ChatAdapter.kt (47 lines)

### Purpose
`RecyclerView.Adapter` rendering chat bubbles with different alignment for user vs. AI messages.

### Binding Logic

| Aspect | User Message | AI Message |
|---|---|---|
| Alignment | Right-aligned | Left-aligned |
| Background | Primary colour | Surface colour |
| Text colour | On-primary | On-surface |

### Design Decisions
- **Single ViewHolder:** Both message types use `item_chat_message.xml` with dynamic gravity/colour changes.
- **100-message hard cap:** Prevents unbounded memory growth (H2 security fix).
- **Input sanitisation:** `InputSanitizer.sanitizeForApi()` strips injection vectors before sending to Gemini.
