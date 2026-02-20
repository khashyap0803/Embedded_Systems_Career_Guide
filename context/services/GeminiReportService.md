# GeminiReportService.kt — Hyper-Detailed Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `GeminiReportService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 795 |
| **Role** | Generates detailed AI assessment reports and personalized 12-week roadmaps using the Gemini API |

---

## 1. Why Does This File Exist?

After a user completes an assessment (a series of questions), this service takes those answers, sends them to the Gemini AI in multiple phases, and produces a comprehensive HTML report. The report includes:

1. **Detailed feedback** on each answer (chunked to avoid AI token limits)
2. **An overall report shell** (summary, score, recommendations)
3. **A final assembled HTML report** for display in a WebView

The multi-phase approach prevents AI response truncation by splitting a large task into smaller, focused API calls.

---

## 2. Imports (Lines 1–17)

```kotlin
package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
```

- **OkHttp** — Direct HTTP client usage (bypasses `NetworkModule`)
- **Gson** — JSON parsing of Gemini API responses
- **Coroutines** — Async execution on `Dispatchers.IO`
- **BuildConfig** — Accesses `GEMINI_API_KEY`

---

## 3. Class Declaration & Companion Object (Lines 19–55)

```kotlin
class GeminiReportService {

    companion object {
        private const val TAG = "GeminiReportService"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val QUESTIONS_PER_CHUNK = 5
    }
```

### Key Constants

| Constant | Value | Purpose |
|---|---|---|
| `GEMINI_API_URL` | `gemini-2.0-flash` endpoint | Hardcoded API URL (not from `NetworkModule`) |
| `MAX_RETRIES` | `3` | Maximum API call retry attempts |
| `INITIAL_RETRY_DELAY_MS` | `2000` | Starting backoff delay (doubles per retry) |
| `QUESTIONS_PER_CHUNK` | `5` | Number of questions sent per feedback chunk |

### ⚠️ Design Note
This service creates its **own OkHttpClient** rather than using `NetworkModule`'s centralized client. It uses extended timeouts (120s) suited for long AI generation tasks.

---

## 4. OkHttpClient Configuration (Lines 57–63)

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()
```

- **120-second timeouts**: Report generation is a multi-step AI process that can take significant time
- **No certificate pinning**: Unlike `NetworkModule`, this client has no pinning configured

---

## 5. Data Classes (Lines 65–85)

### QuestionAnswer
```kotlin
data class QuestionAnswer(
    val question: String,
    val userAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val topic: String
)
```
Represents one answered assessment question with its metadata.

### ProgressCallback Interface
```kotlin
interface ProgressCallback {
    fun onProgress(phase: Int, totalPhases: Int, message: String)
}
```
Notifies the UI about generation progress across phases.

---

## 6. Main Entry Point: `generateReport()` (Lines 87–150)

```kotlin
suspend fun generateReport(
    userName: String,
    userEmail: String,
    questions: List<QuestionAnswer>,
    progressCallback: ProgressCallback? = null
): String = withContext(Dispatchers.IO) { ... }
```

### Execution Flow

```
┌─────────────────────────────────────────┐
│ Phase 1: Generate Detailed Feedback     │
│  → Split questions into chunks of 5     │
│  → Call AI for each chunk               │
│  → Collect HTML feedback snippets       │
├─────────────────────────────────────────┤
│ Phase 2: Generate Overall Report Shell  │
│  → Summary, score, career suggestions   │
│  → 12-week roadmap                      │
├─────────────────────────────────────────┤
│ Phase 3: Assemble Final Report          │
│  → Combine shell + feedback chunks      │
│  → Wrap in mobile-optimized HTML        │
│  → Add CSS styling                      │
└─────────────────────────────────────────┘
```

### Phase 1: Chunked Feedback Generation
- Divides questions into groups of `QUESTIONS_PER_CHUNK` (5)
- Calls `generateDetailedFeedbackWithProgress()` for each chunk
- **Fallback**: If chunk generation fails, calls `generateMinimalFeedback()` which creates basic feedback without AI

### Phase 2: Report Shell
- Calls `generateOverallReport()` with user info and all questions
- **Fallback**: Returns empty string if generation fails, triggering a template-based fallback in assembly

### Phase 3: Assembly
- `assembleReport()` combines the shell and feedback
- Applies the mobile-optimized HTML template with CSS

---

## 7. Chunked Feedback Generation (Lines 152–220)

```kotlin
private suspend fun generateDetailedFeedbackWithProgress(
    questions: List<QuestionAnswer>,
    totalPhases: Int,
    callback: ProgressCallback?
): List<String>
```

### Process
1. Splits questions into chunks of 5
2. For each chunk, builds a prompt asking AI to evaluate each answer
3. Calls `callGeminiAPI()` with the prompt
4. Returns a list of HTML feedback strings

### Prompt Structure
The prompt instructs Gemini to:
- Evaluate whether each answer is correct/incorrect
- Provide detailed explanations for wrong answers
- Give hints for improvement
- Output mobile-friendly HTML

---

## 8. Overall Report Generation (Lines 222–300)

```kotlin
private fun generateOverallReport(
    userName: String,
    userEmail: String,
    questions: List<QuestionAnswer>
): String
```

Builds a prompt asking Gemini to generate:
- **Overall score and grade** (A/B/C/D/F)
- **Strength and weakness analysis** by topic
- **Career path recommendations** based on performance
- **Personalized 12-week roadmap** with weekly goals
- **Recommended resources** (books, courses, tools)

---

## 9. Report Assembly (Lines 302–450)

```kotlin
private fun assembleReport(
    reportShell: String,
    feedbackChunks: List<String>
): String
```

### Assembly Logic
1. If `reportShell` is empty → uses a template-based fallback
2. Injects feedback chunks into the report at the `<!-- DETAILED_FEEDBACK -->` marker
3. Wraps everything in a mobile-optimized HTML template

### HTML Template Features
- **Responsive design**: CSS media queries for mobile
- **Dark mode support**: Adapts to system theme
- **Print-friendly styles**: Optimized for sharing/printing
- **Embedded CSS**: No external dependencies
- **Professional styling**: Score cards, progress bars, topic badges

---

## 10. Core API Call: `callGeminiAPI()` (Lines 452–570)

```kotlin
private fun callGeminiAPI(prompt: String): String
```

### Request Construction
```json
{
  "contents": [{"parts": [{"text": "<prompt>"}]}],
  "generationConfig": {
    "maxOutputTokens": 8192,
    "temperature": 0.7,
    "topP": 0.95
  }
}
```

### Retry Strategy
```
Attempt 1 → Fail → Wait 2s
Attempt 2 → Fail → Wait 4s (+ jitter)
Attempt 3 → Fail → Wait 8s (+ jitter)
→ Throw exception
```

### Error Handling
| HTTP Code | Action |
|---|---|
| `429` | Rate limited → retry with backoff |
| `5xx` | Server error → retry with backoff |
| `4xx` (not 429) | Client error → throw immediately |
| Empty response | Throw `Exception("Empty response")` |
| Truncated response | Log warning, return what we have |

### Response Parsing
1. Parse JSON body
2. Navigate to `candidates[0].content.parts[0].text`
3. Check `finishReason` for potential truncation
4. Return extracted text

---

## 11. Minimal Feedback Fallback (Lines 572–620)

```kotlin
private fun generateMinimalFeedback(questions: List<QuestionAnswer>): String
```

When AI fails entirely, generates basic HTML feedback from the raw question/answer data:
- ✅ / ❌ icons for correct/incorrect
- Shows the user's answer vs. correct answer
- No AI-powered explanations

---

## 12. HTML Styling Constants (Lines 622–795)

The file contains extensive CSS embedded as Kotlin string constants:

### Color Scheme
- Primary: `#2196F3` (Material blue)
- Success: `#4CAF50` (Green)
- Error: `#f44336` (Red)
- Background: `#f5f5f5` (Light gray)

### CSS Features
- Responsive font sizing
- Card-based layout
- Shadow effects for depth
- Score visualization (circular badge)
- Topic tag pills
- Roadmap timeline styling

---

## 13. Connection Map

```
┌──────────────────┐        ┌─────────────────┐
│ Assessment UI    │───────▶│ GeminiReport    │
│ (Fragment/       │        │ Service         │
│  Activity)       │◀───────│                 │
└──────────────────┘        └────────┬────────┘
  ProgressCallback                   │
                                     │ HTTP POST
                                     ▼
                              ┌─────────────┐
                              │ Gemini API  │
                              │ (2.0-flash) │
                              └─────────────┘
```

### Dependencies
| Dependency | Used For |
|---|---|
| `OkHttp` | Direct HTTP API calls |
| `Gson` | JSON request/response handling |
| `BuildConfig` | API key access |
| `Dispatchers.IO` | Background thread execution |

### Used By
- Assessment completion UI (likely `AssessmentResultFragment` or similar)
- Report viewing screen (WebView-based display)

---

## 14. Potential Issues & Notes

| Issue | Details |
|---|---|
| **Bypasses NetworkModule** | Creates own OkHttpClient, missing certificate pinning |
| **Hardcoded API URL** | API URL is in companion object, not configurable |
| **Large HTML output** | Reports can be very large; no size limit enforcement |
| **No caching** | Generated reports aren't cached by this service (caller may cache) |
| **Token limit risk** | 8192 output tokens might truncate very long reports |
| **No input validation** | Trusts that `QuestionAnswer` list is valid |

---

## 15. Summary

`GeminiReportService` is the **assessment report generator**. It takes completed quiz answers, feeds them to Gemini AI in **three phases** (chunked feedback → overall report → final assembly), and produces a **mobile-optimized HTML report** with detailed feedback, career recommendations, and a 12-week learning roadmap. It includes robust retry logic with exponential backoff and fallback mechanisms for when AI generation fails.
