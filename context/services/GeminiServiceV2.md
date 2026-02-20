# GeminiServiceV2.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiServiceV2.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 22,385 bytes (683 lines)

---

## What This File Does (Simple Explanation)

This is the **unified AI backbone** for all V2 features. While `GeminiChatService` and `GeminiQuizService` are V1 services that each build their own API requests, `GeminiServiceV2` provides:

1. **A centralized `generateContent()` method** — one API call function for all V2 services
2. **A prompt template library** (`PromptTemplates` object) — pre-built prompts for every AI feature
3. **Token usage tracking** — counts input/output tokens and estimates cost in INR
4. **Retry logic** — exponential backoff for resilient API calls

Every V2 service (AnalyticsService, DailyTipService, FlashcardService, InterviewPrepService, ProjectSuggestionService) delegates its AI calls to this service.

---

## Why This File Exists

V1 had separate, duplicated Gemini API call logic in each service. V2 centralizes this to:
- Reduce code duplication
- Standardize error handling and retry logic
- Provide a single place to change API settings, model version, or key
- Track token usage and estimate costs

---

## Where This File Is Used

| File | What It Calls |
|------|--------------|
| `AnalyticsService.kt` | `generateContent()` with `analytics()` prompt |
| `DailyTipService.kt` | `generateContent()` with `dailyTips()` prompt |
| `FlashcardService.kt` | `generateContent()` with `flashcards()` prompt |
| `InterviewPrepService.kt` | `generateContent()` with `interviewPrep()` prompt |
| `ProjectSuggestionService.kt` | `generateContent()` with `projectSuggestions()` prompt |
| `FirestoreManager.kt` | `generateContent()` with `personalizedStages()` / `stageContent()` prompts |

---

## Complete Code Walkthrough

### Lines 29-58: Class Setup
- **API Config**: Uses `BuildConfig.GEMINI_API_KEY`, model is `gemini-2.5-flash`
- **Retry Config**: 3 retries, starting at 1 second delay
- **Singleton**: Thread-safe double-checked locking
- **HTTP Client**: Creates its OWN `OkHttpClient` (60s connect, 120s read, 60s write) — does NOT use `NetworkModule`

### Lines 62-64: Token Tracking
```kotlin
private var totalInputTokens = 0L
private var totalOutputTokens = 0L
```
Running totals across all API calls in the app session.

---

## Prompt Templates (Lines 69-578)

The `PromptTemplates` object contains **11 prompt template functions**:

| Method | Purpose | Output |
|--------|---------|--------|
| `personalizedStages()` | Generate 40-stage learning path from assessment | JSON array of stages |
| `stageContent()` | Generate learning content for one stage | Theory, code, tips, challenge |
| `regenerateStagesWithHistory()` | Rebuild path considering past progress | JSON array of stages |
| `flashcards()` | Generate 15 flashcards per stage | JSON array of flashcards |
| `quizWithExplanations()` | Generate 5 MCQ with explanations | JSON array of questions |
| `contextAwareChat()` | Chat response considering user's stage | Contextual prompt string |
| `progressAnalytics()` | Analyze learning progress | Report JSON |
| `interviewQuestions()` | Generate 10 interview questions | JSON array of questions |
| `projectSuggestions()` | Suggest 5 hands-on projects | JSON array of projects |
| `dailyTip()` | Single tip (V1-style) | Tip JSON |
| `dailyTips()` | Batch of 7 tips | JSON array of tips |
| `interviewPrep()` | Interview prep questions with follow-ups | JSON array |
| `codeReview()` | Review embedded C code | JSON analysis |
| `analytics()` | Learning recommendations | JSON recommendations |

Each template:
- Specifies the AI's role (professor, interviewer, curriculum designer)
- Includes strict JSON output format instructions
- Requests "no markdown, no explanation"

### Lines 584-604: `generateContent()` — Main API Method
```kotlin
suspend fun generateContent(prompt: String, maxOutputTokens: Int = 4096): Result<String>
```
1. Runs on `Dispatchers.IO`
2. Retries up to 3 times with exponential backoff
3. Returns `Result<String>` — success with text content or failure with exception

### Lines 609-657: `callGeminiAPI()` — Internal HTTP Call
- Uses `.use { response -> }` for proper resource cleanup
- Tracks token usage from `usageMetadata` in response
- Null-safe response parsing
- `temperature: 0.7`, `topP: 0.95`

### Lines 662-681: Token Tracking Utilities
- `getTokenUsage()` — returns (input, output) pair
- `resetTokenUsage()` — zeroes counters
- `estimateCostINR()` — estimates cost using Gemini 2.5 Flash pricing:
  - ₹27 per million input tokens
  - ₹225 per million output tokens

---

## Dependencies

| Import | Why |
|--------|-----|
| `BuildConfig` | API key access |
| `Gson`, `JsonObject` | JSON construction and parsing |
| `OkHttp` | HTTP requests |
| `Dispatchers.IO` | Background threading |
| `delay` | For exponential backoff |

---

## Connections to Other Files

```
┌─────────────────────────┐
│    GeminiServiceV2      │
│  ┌────────────────────┐ │
│  │ PromptTemplates    │ │     ┌──────────────────┐
│  │  .personalizedStages│─────►│ FirestoreManager │
│  │  .stageContent     │ │     └──────────────────┘
│  │  .flashcards       │─────► FlashcardService
│  │  .dailyTips        │─────► DailyTipService
│  │  .analytics        │─────► AnalyticsService
│  │  .interviewPrep    │─────► InterviewPrepService
│  │  .projectSuggestions│────► ProjectSuggestionService
│  └────────────────────┘ │
│  ┌────────────────────┐ │
│  │ generateContent()  │ │     ┌──────────────────┐
│  │    ↓               │─────►│ Gemini API       │
│  │ callGeminiAPI()    │ │     └──────────────────┘
│  └────────────────────┘ │
└─────────────────────────┘
```

---

## Strengths

- ✅ Single API call method for all services
- ✅ Comprehensive prompt template library
- ✅ Token usage tracking for cost monitoring
- ✅ INR cost estimation (developer-focused)
- ✅ Proper resource cleanup with `.use { }`
- ✅ Exponential backoff retry

## Weaknesses / Technical Debt

- ⚠️ Creates its OWN `OkHttpClient` — bypasses `NetworkModule` and its certificate pinning
- ⚠️ API key and URL hardcoded in companion object — duplicates `NetworkModule.getGeminiApiUrl()`
- ⚠️ Token tracking is session-only (in-memory) — resets on app restart
- ⚠️ No request cancellation support
- ⚠️ Some prompt templates overlap (e.g., `dailyTip` vs `dailyTips`, `interviewQuestions` vs `interviewPrep`)
- ⚠️ `UserPerformanceData` referenced in `regenerateStagesWithHistory()` but defined elsewhere
