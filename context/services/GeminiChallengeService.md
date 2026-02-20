# GeminiChallengeService.kt — Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `GeminiChallengeService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 1,294 |
| **Role** | AI-powered challenge generation and evaluation for three distinct challenge types in the pre-release event |

---

## 1. Why Does This File Exist?

The app features a **pre-release challenge event** with three tiers of difficulty. This service uses the Gemini API to:

1. **Generate** problems for each challenge tier
2. **Evaluate** user submissions against expected solutions
3. **Score** submissions on 6 criteria with server-side enforcement

Each challenge type tests a different embedded systems skill:
- **Challenge 1**: Hardware selection (pick MCU + components + arrange code blocks)
- **Challenge 2**: Code completion (fill in missing code lines)
- **Challenge 3**: Full code writing (write complete solutions)

---

## 2. Imports (Lines 1–26)

```kotlin
import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.example.embeddedsystemscareerguide.models.challenge.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.util.concurrent.TimeUnit
```

Direct OkHttp + Gson stack. Bypasses `NetworkModule` with its own HTTP client.

---

## 3. Class & Companion Object (Lines 27–70)

```kotlin
class GeminiChallengeService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GeminiChallengeService"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
```

### Singleton Pattern
```kotlin
fun getInstance(context: Context): GeminiChallengeService
```
Thread-safe double-checked locking singleton. Requires `Context` for BuildConfig access.

### Hardware Component Lists
```kotlin
val AVAILABLE_MCUS = listOf("Arduino UNO", "ESP32", "STM32 Blue Pill", ...)
val AVAILABLE_SENSORS = listOf("DHT11 Temperature Sensor", "PIR Motion Sensor", ...)
val AVAILABLE_MODULES = listOf("LED Module (RGB)", "LCD Display 16x2", ...)
```
These lists constrain Challenge 1 problem generation to well-known, beginner-friendly hardware.

---

## 4. OkHttpClient (Lines 72–78)

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
```

60s timeouts — shorter than `GeminiReportService` (120s) since challenges are simpler generation tasks.

---

## 5. Challenge 1: Hardware Selection (Lines 73–265)

### Problem Generation: `generateChallenge1Problems()` (Lines 73–164)

```kotlin
suspend fun generateChallenge1Problems(): Result<List<Challenge1Problem>>
```

- Requests exactly 3 beginner-level embedded systems problems
- Specifies available MCUs, sensors, and modules from companion object lists
- Enforces strict rules: 1 MCU + 1–2 sensors + 1–2 modules (max 4 total)
- Returns `Challenge1Problem` objects with sequential IDs

### Code Block Generation: `generateCodeBlocksForChallenge1()` (Lines 166–259)

```kotlin
suspend fun generateCodeBlocksForChallenge1(
    problemStatement: String,
    mcu: String,
    components: List<String>
): Result<List<CodeBlock>>
```

- Generates problem-specific code blocks using Gemini API
- Returns shuffled `CodeBlock` list with categories: `INCLUDE`, `DEFINE`, `SETUP`, `LOOP`
- Parses via helper `CodeBlockResponse` data class (Lines 262–265)

### Evaluation: `evaluateChallenge1()` (Lines 441–616)

```kotlin
suspend fun evaluateChallenge1(
    problemStatement: String,
    selectedMcu: String,
    selectedComponents: List<String>,
    codeBlocks: List<String>,
    codeModified: Boolean = false    // Server-side enforcement parameter
): Result<EvaluationResult>
```

**Server-side deterministic enforcement** (overrides AI inconsistency):
- **Rule 1**: `codeModified == false` → force `syntaxCorrectness=0`, `codeQuality=0`, `errorCount=0`, cap `attemptCompleteness≤12`, `logicAccuracy≤10`
- **Rule 2**: No MCU selected → cap `attemptCompleteness≤14`, `logicAccuracy≤15`
- **Rule 3**: No components selected → cap `attemptCompleteness≤14`, `criticalElements≤8`
- **Rule 4**: Completely unattempted (no MCU + no components + no code) → force all scores to 0
- Retries up to 3 times on JSON parse failures

---

## 6. Challenge 2: Code Completion (Lines 269–784)

### Problem Generation: `generateChallenge2Questions()` (Lines 269–346)

```kotlin
suspend fun generateChallenge2Questions(): Result<List<Challenge2QuestionInternal>>
```

Generates **3 code completion questions** with pre-code, post-code, missing line count, and expected solution.

### Evaluation: `evaluateChallenge2()` (Lines 618–784)

```kotlin
suspend fun evaluateChallenge2(
    questions: List<Challenge2QuestionInternal>
): Result<EvaluationResult>
```

Takes the **entire question list** (not individual params). Builds evaluation prompt with all 3 questions' scenarios, code templates, and user answers. Applies same 6-category rubric with server-side enforcement for empty/unattempted answers. Retries up to 3 times.

---

## 7. Challenge 3: Full Code Writing (Lines 350–959)

### Problem Generation: `generateChallenge3Questions()` (Lines 350–437)

```kotlin
suspend fun generateChallenge3Questions(): Result<List<Challenge3QuestionInternal>>
```

Generates **3 full coding challenges** with scenario, constraints, hints, and expected solution.

### Evaluation: `evaluateChallenge3()` (Lines 786–959)

```kotlin
suspend fun evaluateChallenge3(
    questions: List<Challenge3QuestionInternal>
): Result<EvaluationResult>
```

Takes the **entire question list**. Most stringent evaluation — checks complete code against requirements and expected elements. Server-side enforcement for empty answers. Retries up to 3 times.

---

## 8. Core API Layer (Lines 963–1175)

### `callGeminiAPI()` (Lines 963–1072)

```kotlin
private fun callGeminiAPI(prompt: String, temperature: Double = 0.7): String
```

- Constructs JSON request body with configurable temperature
- Uses API key from `BuildConfig.GEMINI_API_KEY`
- **5 retry attempts** with exponential backoff: 1s, 2s, 4s, 8s, 16s + random jitter
- Custom exceptions: `RateLimitException` (429), `ServerException` (5xx), `ClientException` (4xx)
- Parses `candidates[0].content.parts[0].text` from response

### JSON Extraction Utilities (Lines 1080–1175)

#### `extractJsonArray()` (Lines 1080–1149)
Robust multi-strategy extraction:
1. Remove markdown code blocks (`` ```json ... ``` ``)
2. Try both normalized (CRLF→LF) and original string
3. Find `[` and `]` boundaries
4. Return extracted JSON array string

#### `extractJsonObject()` (Lines 1151–1175)
Similar extraction for JSON objects — finds `{` and `}` boundaries.

---

## 9. Response Data Classes (Lines 1177–1221)

| Class | Lines | Purpose |
|---|---|---|
| `Challenge1ProblemResponse` | 1180–1186 | Raw Gemini response for hardware selection problems |
| `Challenge2QuestionResponse` | 1188–1196 | Raw response for code completion questions |
| `Challenge3QuestionResponse` | 1198–1204 | Raw response for full coding challenges |
| `EvaluationCategoryResponse` | 1206–1210 | One scoring category (score, maxScore, details) |
| `EvaluationResponse` | 1212–1221 | Complete evaluation with all 6 categories + total + feedback |

---

## 10. Internal Data Classes (Lines 1222–1294)

| Class | Lines | Purpose |
|---|---|---|
| `Challenge1Problem` | 1230–1241 | Enriched problem with MCU options, components, code blocks, problem statement |
| `CodeBlock` | 1243–1247 | A single code snippet with `id`, `text`, `category`, `isDistractor` |
| `CodeBlockCategory` | 1249–1249 | Enum: `INCLUDE`, `DEFINE`, `DECLARATION`, `SETUP`, `LOOP`, `FUNCTION` |
| `Challenge1ProblemAnswer` | 1251–1255 | Tracks user's MCU, components, code block order |
| `Challenge2QuestionInternal` | 1257–1265 | Full question with mutable `userAnswer` field |
| `Challenge3QuestionInternal` | 1267–1294 | Full question with mutable `userCode` field |

---

## 11. Connection Map

```
┌─────────────────────┐     ┌──────────────────────────┐
│ Challenge1Activity  │────▶│                          │
├─────────────────────┤     │ GeminiChallengeService   │
│ Challenge2Activity  │────▶│                          │──── HTTP ───▶ Gemini API
├─────────────────────┤     │ (Singleton)              │
│ Challenge3Activity  │────▶│                          │
└─────────────────────┘     └──────────────────────────┘
                                      │
                                      │ Scores stored via
                                      ▼
                            ┌──────────────────────┐
                            │ PreReleaseEvent      │
                            │ Service              │
                            │ (saves to Firebase   │
                            │  Realtime DB)        │
                            └──────────────────────┘
```

---

## 12. Scoring Rubric (All Challenges)

| Category | Max Score | What It Measures |
|---|---|---|
| Attempt Completeness | 20 | All parts attempted, required sections present |
| Syntax Correctness | 20 | Code syntactically valid, will compile |
| Logic Accuracy | 25 | Logic correctly solves the problem |
| Critical Elements | 15 | Essential components present (ISR, pin config) |
| Code Quality | 10 | Organization, naming, comments, efficiency |
| Error Count | 10 | 0 errors=10, 1-2=7, 3-4=4, 5+=0 |
| **Total** | **100** | |

---

## 13. Error Handling & Fallbacks

| Scenario | Handling |
|---|---|
| API call fails all 5 retries | Returns `Result.failure(e)` |
| JSON parsing fails | Retries up to 3 times with fresh API call |
| Rate limited (429) | Exponential backoff via `RateLimitException` |
| Server error (5xx) | Exponential backoff via `ServerException` |
| Client error (4xx) | Throws `ClientException` |
| Response truncated / malformed JSON | Multi-strategy extraction (`extractJsonArray`/`extractJsonObject`) |
| Empty candidates array | Throws `Exception("No response candidates")` |
| Server-side score validation fails | Clamps individual category scores to valid ranges |

---

## 14. Summary

`GeminiChallengeService` (1,294 lines) is the **AI engine for the pre-release challenge event**. It generates three types of embedded systems challenges (hardware selection, code completion, full code writing) and evaluates user submissions on a 100-point rubric across 6 scoring categories. Key features include: server-side deterministic score enforcement to override AI inconsistency, per-question retry logic, configurable temperature for API calls, and robust JSON extraction utilities. Operates as a singleton with direct OkHttp calls to the Gemini API.
