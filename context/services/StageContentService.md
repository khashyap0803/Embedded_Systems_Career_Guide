# StageContentService.kt — Hyper-Detailed Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `StageContentService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 1616 |
| **Role** | AI-powered learning content generator — creates comprehensive stage lessons via 4 separate Gemini API calls |

---

## 1. Why Does This File Exist?

Each personalized learning stage needs rich educational content: theory, key points, code examples, tips, and challenges. This service generates all of that using AI, split across **four separate API calls** to avoid token limit truncation. Content is:
- **Generated on-demand** (first access triggers generation)
- **Cached in Firestore** (subsequent accesses load from cache)
- **Fallback-protected** (every component has hardcoded defaults if AI fails)

The service provides a "Kindle-style reading experience" with university-level (Stanford/MIT/IIT) quality content.

---

## 2. Imports (Lines 1–8)

```kotlin
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Minimal imports — uses `GeminiServiceV2` for API calls (no direct OkHttp usage).

---

## 3. Class & Singleton (Lines 20–37)

```kotlin
class StageContentService(private val context: Context) {

    companion object {
        private const val TAG = "StageContentService"

        @Volatile
        private var instance: StageContentService? = null

        fun getInstance(context: Context): StageContentService {
            return instance ?: synchronized(this) {
                instance ?: StageContentService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()
```

### Dependencies
| Dependency | Role |
|---|---|
| `GeminiServiceV2` | AI content generation |
| `FirestoreManager` | Caching generated content |
| `Gson` | JSON parsing of AI responses |

---

## 4. Content Callback (Lines 43–47)

```kotlin
interface ContentCallback {
    fun onProgress(message: String)
    fun onSuccess(content: StageContent)
    fun onError(error: String)
}
```
All callbacks dispatched on `Dispatchers.Main` for direct UI updates.

---

## 5. Main Entry Point: `getStageContent()` (Lines 55–92)

```kotlin
suspend fun getStageContent(
    stage: PersonalizedStage,
    callback: ContentCallback
)
```

### Flow
```
1. callback.onProgress("Loading content...")
2. Check Firestore cache
   ├─ Found → callback.onSuccess(cached)  ← FAST PATH
   └─ Not found ↓
3. callback.onProgress("Generating learning content with AI...")
4. generateAndCacheContent(stage, callback)  ← 4-part AI generation
```

---

## 6. Generic Retry Helper (Lines 96–143)

```kotlin
private suspend fun <T> generateWithRetry(
    promptGenerator: () -> String,
    parser: (String) -> T?,
    validator: (T?) -> Boolean,
    maxRetries: Int = 3,
    onRetry: (Int) -> Unit = {}
): T?
```

A **generic** retry wrapper used internally:
- Generates a prompt, calls AI, parses result
- Re-tries up to `maxRetries` times if parsing/validation fails
- Exponential backoff: 1s, 2s, 4s
- Returns last result even if invalid (fallbacks handle the rest)

---

## 7. The 4-Part Generation System (Lines 202–377)

### `generateAndCacheContent()` — Orchestrator

```
┌─────────────────────────────────────────────────────────────┐
│ Part 1/4: Detailed Theory (1500–2000 words)                │
│  → buildTheoryOnlyPrompt()                                  │
│  → maxOutputTokens: 8192                                    │
│  → extractTheoryText()                                      │
│  → Fallback: createFallbackContent() theory                 │
├─────────────────────────────────────────────────────────────┤
│ Part 2/4: Key Points (8 detailed points)                    │
│  → buildKeyPointsPrompt()                                   │
│  → maxOutputTokens: 4096                                    │
│  → parseKeyPointsResponse()                                 │
│  → Retry: createSimplifiedKeyPointsPrompt() (up to 2x)     │
│  → Fallback: generateFallbackKeyPoints()                    │
├─────────────────────────────────────────────────────────────┤
│ Part 3/4: Code Example with Explanation                     │
│  → buildCodeOnlyPrompt() (difficulty-adaptive)              │
│  → maxOutputTokens: 8192                                    │
│  → parseCodeExampleResponse()                               │
│  → Retry: createSimplifiedCodePrompt() (up to 2x)          │
│  → Fallback: generateFallbackCodeExample()                  │
├─────────────────────────────────────────────────────────────┤
│ Part 4/4: Tips, Mistakes, & Mini Challenge                  │
│  → buildTipsAndChallengePrompt()                            │
│  → maxOutputTokens: 8192                                    │
│  → parseTipsAndChallengeResponse()                          │
│  → Retry: createSimplifiedTipsPrompt() (up to 2x)          │
│  → Fallback: generateFallbackTipsData()                     │
├─────────────────────────────────────────────────────────────┤
│ Assembly: createFullContent()                               │
│  → Merge 4 parts with fallbacks for any nulls               │
│  → Save to Firestore via firestoreManager.saveStageContent()│
│  → callback.onSuccess(content)                              │
└─────────────────────────────────────────────────────────────┘
```

### Retry Pattern per Part
Each part follows this pattern:
```kotlin
// 1. Try main prompt
val result = geminiService.generateContent(mainPrompt, maxOutputTokens)
parsed = parseResponse(result)

// 2. If failed, retry with simplified prompt (up to 2 times)
while (parsed == null && retryAttempt < 2) {
    delay(1000)
    val retryResult = geminiService.generateContent(simplifiedPrompt, smallerTokens)
    parsed = parseResponse(retryResult)
}

// 3. If still null, use hardcoded fallback
if (parsed == null) {
    parsed = generateFallback()
}
```

---

## 8. Prompt Engineering (Lines 379–624)

### Part 1: Theory Prompt (Lines 385–432)
Instructs the AI to act as a "world-class Embedded Systems professor from Stanford/MIT/IIT" and generate a 1500–2000 word theory section with:
1. Foundational Concepts (math, theory, history)
2. Hardware Architecture Deep Dive (registers, memory, timing)
3. Software-Hardware Interface (MMIO, volatile, interrupts, DMA)
4. Industry Applications & Standards (AUTOSAR, IEC 62304, IoT)
5. Advanced Considerations (security, performance, power)

**Output**: Raw markdown text (no JSON wrapping needed).

### Part 2: Key Points Prompt (Lines 439–467)
Requests exactly 8 key points as a **JSON array of strings**.

Includes strict formatting rules with ✅ correct and ❌ wrong examples to prevent common AI mistakes like markdown wrappers and trailing commas.

### Part 3: Code Example Prompt (Lines 478–574)
**Difficulty-adaptive prompt** — different requirements based on level:

| Level | Lines | Complexity | Explanation Style |
|---|---|---|---|
| Beginner | 15–25 | Simple, lots of comments | "Explain like teaching a complete beginner" with everyday analogies |
| Intermediate | 25–40 | Error handling, register-level | Clear concepts with debugging tips |
| Advanced | 40–80 | Production-quality, hardware addresses | Line-by-line breakdown with performance implications |

**Output**: JSON object `{"language": "c", "code": "...", "explanation": "..."}`.

### Part 4: Tips Prompt (Lines 582–624)
Requests:
- `commonMistakes`: Array of `{mistake, solution}` objects
- `proTips`: Array of practical advice strings
- `miniChallenge`: Object with `{task, hint}`

### Simplified Retry Prompts (Lines 148–191)
Shorter, more constrained prompts to try when main prompts produce unparseable output:
```kotlin
private fun createSimplifiedKeyPointsPrompt(stageName, topics) = """
Create 8 key learning points about "$stageName".
Return ONLY a simple JSON array of strings.
Example: ["Point 1 here", "Point 2 here"]
Your response must: Start with [ and end with ]
"""
```

---

## 9. Parsing System (Lines 626–893)

### `extractTheoryText()` (Lines 631–652)
- Removes JSON wrapping if present
- Removes markdown code blocks
- Validates minimum 500 characters (returns `null` if too short)

### `parseKeyPointsResponse()` (Lines 658–717)
**4-strategy parser:**

| Strategy | What it tries |
|---|---|
| 1 | Extract JSON → check if wrapped in `{"keyPoints": [...]}` |
| 2 | Direct `Array<String>` parsing with Gson |
| 3 | Regex extraction of quoted strings > 20 chars |
| 4 | Line-based extraction (bullets, numbered lists) |

### `parseCodeExampleResponse()` (Lines 724–792)
**3-strategy parser:**

| Strategy | What it tries |
|---|---|
| 1 | Standard JSON extraction → `CodeExample` |
| 2 | Extract code from markdown code blocks (` ```c ... ``` `) |
| 3 | Heuristic: find lines containing `#include`, `void`, `//`, `;`, `{`, `}` |

### `parseTipsAndChallengeResponse()` (Lines 799–893)
**2-strategy parser:**

| Strategy | What it tries |
|---|---|
| 1 | Standard JSON → extract `commonMistakes`, `proTips`, `miniChallenge` |
| 2 | Line-based: scan for keywords ("mistake", "tip:", "challenge") |

---

## 10. Fallback Content Generators (Lines 895–1099)

### `generateFallbackKeyPoints()` (Lines 901–918)
Creates 8 generic points from topic names + embedded systems best practices.

### `generateFallbackCodeExample()` (Lines 924–1063)
Returns topic-appropriate code:
- **GPIO topics** → LED control with register manipulation
- **Interrupt topics** → Button ISR with NVIC configuration
- **Default** → Basic peripheral read/write pattern

Each fallback includes proper comments and a markdown explanation.

### `generateFallbackTipsData()` (Lines 1070–1099)
Returns generic but useful embedded systems tips:
- 3 common mistakes (volatile, status checking, blocking delays)
- 4 pro tips (logic analyzer, errata sheets, header files, const for Flash)
- 1 mini challenge (add timeout mechanism)

---

## 11. JSON Repair Utilities (Lines 1101–1307)

### `fixMalformedJson()` (Lines 1107–1131)
Master repair function — calls 5 sub-functions in sequence:
1. Fix control characters (`\t`, `\r`)
2. `fixNewlinesInStrings()` — escape `\n` inside JSON strings
3. `fixUnescapedQuotes()` — escape `"` inside strings
4. `balanceBracketsAndBraces()` — add missing closers
5. Remove trailing commas (regex-based)
6. `fixUnterminatedStrings()` — close unclosed strings

### `fixNewlinesInStrings()` (Lines 1136–1160)
Character-by-character scan. Tracks `inString` state. Converts actual newlines inside strings to `\\n`.

### `fixUnescapedQuotes()` (Lines 1165–1203)
Detects quotes inside strings by checking if the next character is a JSON structural character (`,`, `:`, `}`, `]`). If not → it's an unescaped inner quote → adds backslash.

### `balanceBracketsAndBraces()` (Lines 1208–1239)
Counts all `{`, `}`, `[`, `]` outside strings. Appends missing closers. Strips trailing incomplete content.

### `fixUnterminatedStrings()` (Lines 1244–1265)
If we end inside a string (odd number of unescaped quotes), appends a closing `"`.

### `extractJsonObject()` / `extractJsonArray()` (Lines 1270–1307)
Standard extraction: remove markdown → find `{}`/`[]` boundaries → apply `fixMalformedJson()`.

---

## 12. Content Assembly (Lines 1309–1341)

### `createFullContent()` (Lines 1314–1334)
```kotlin
private fun createFullContent(
    stageId: Int,
    theory: String?,
    keyPoints: List<String>,
    codeExample: CodeExample?,
    tipsData: TipsData?,
    stage: PersonalizedStage
): StageContent
```

Uses `createFallbackContent()` as the **safety net** — each field is replaced with fallback if null/empty/too short:
```kotlin
theory = theory?.takeIf { it.length > 500 } ?: fallback.theory
keyPoints = keyPoints.takeIf { it.isNotEmpty() } ?: fallback.keyPoints
codeExample = codeExample?.takeIf { it.code.isNotBlank() } ?: fallback.codeExample
```

### `TipsData` Internal Class
```kotlin
private data class TipsData(
    val commonMistakes: List<Mistake>,
    val proTips: List<String>,
    val miniChallenge: Challenge
)
```

---

## 13. Legacy Single-Call Parser (Lines 1347–1445)

### `parseContentFromResponse()` — Unused in Current 4-Part System
```kotlin
private fun parseContentFromResponse(response: String, stageId: Int): StageContent?
```
This was the original single-JSON-object parser from before the 4-part split. It parses all fields from one response. Still present in the code but **not called** by the current generation flow.

---

## 14. Full Fallback Content (Lines 1450–1569)

### `createFallbackContent()`
A comprehensive, hardcoded `StageContent` that covers:
- **Theory**: ~600 words of generic embedded systems overview (uses stage title/description)
- **Key Points**: 6 fundamental embedded systems points
- **Code Example**: 30-line GPIO LED toggle example with volatile registers
- **Common Mistakes**: 3 practical errors (volatile, delays, init order)
- **Pro Tips**: 4 practical tips (datasheets, header files, abstraction, JTAG)
- **Mini Challenge**: "Blink LED at 1 Hz using timer interrupt"

This ensures the user **always** sees useful content even if all 4 AI calls fail completely.

---

## 15. Utility Methods (Lines 1574–1616)

### `regenerateContent()` (Lines 1574–1582)
Force re-generates content for a stage (bypasses cache check):
```kotlin
suspend fun regenerateContent(stage: PersonalizedStage, callback: ContentCallback)
```

### `preloadNextStages()` (Lines 1587–1616)
Background preloading of upcoming stages:
```kotlin
suspend fun preloadNextStages(currentStageId: Int, count: Int = 2)
```
- Gets all stages from Firestore
- Filters to next `count` stages after current
- Generates content for any that aren't cached yet
- Runs silently in the background (no callbacks)

---

## 16. Connection Map

```
┌──────────────────┐     ┌────────────────────────────────┐
│ Stage Reading    │     │      StageContentService       │
│ Screen (UI)     │────▶│      (Singleton)                │
│                  │     │                                │
│ • Shows theory   │     │  ┌───────────────────────────┐ │
│ • Shows code     │◀────│  │ 4 API calls per stage:   │ │
│ • Shows tips     │     │  │ 1. Theory (8K tokens)    │ │
└──────────────────┘     │  │ 2. Key Points (4K)       │ │
                         │  │ 3. Code Example (8K)     │ │
                         │  │ 4. Tips/Challenge (8K)   │ │
                         │  └────────────┬──────────────┘ │
                         │               │                │
                         │  ┌────────────▼──────────────┐ │
                         │  │ GeminiServiceV2           │ │
                         │  │ (API calls)               │ │
                         │  └───────────────────────────┘ │
                         │                                │
                         │  ┌───────────────────────────┐ │
                         │  │ FirestoreManager          │ │
                         │  │ (cache read/write)        │ │
                         │  └───────────────────────────┘ │
                         └────────────────────────────────┘
```

---

## 17. Potential Issues & Notes

| Issue | Details |
|---|---|
| **Largest service file** | 1616 lines; heavy with prompts and fallbacks |
| **4 API calls per stage** | Significant cost and latency; not parallelized |
| **Legacy parser unused** | `parseContentFromResponse()` is dead code |
| **Fallback quality** | Hardcoded content is generic; not topic-specific beyond title |
| **No content versioning** | Re-generating overwrites; no history kept |
| **Preloading cost** | `preloadNextStages()` silently consumes API quota |
| **JSON repair complexity** | 5 repair functions may over-correct valid but unusual JSON |
| **No content validation** | A theory text of 501 chars passes but may be low quality |

---

## 18. Summary

`StageContentService` is the **largest service in the app** (1616 lines) and the **learning content engine**. It generates rich educational material for each personalized stage using a **4-part AI generation system** (theory, key points, code examples, tips) — each with its own prompt, parser, retry mechanism, and hardcoded fallback. Content is cached in Firestore after first generation. The service includes extensive JSON repair utilities for handling malformed AI output and difficulty-adaptive code generation that adjusts complexity for beginner/intermediate/advanced learners.
