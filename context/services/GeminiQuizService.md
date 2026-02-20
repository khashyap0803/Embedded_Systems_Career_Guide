# GeminiQuizService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiQuizService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 20,873 bytes (488 lines)

---

## What This File Does (Simple Explanation)

This service **generates multiple-choice quizzes** for each learning stage using the Gemini AI. Each quiz has 10 questions. To avoid the AI truncating its response, it splits the work into **2 API calls of 5 questions each**, then combines them. If the AI fails, it provides 10 hardcoded fallback questions about embedded systems fundamentals.

---

## Why This File Exists

Every learning stage ends with a quiz to test understanding. This service:
1. Builds a carefully crafted prompt requesting short MCQ questions
2. Handles API failures with exponential backoff retry (3 attempts)
3. Parses Gemini's JSON response with 2 fallback parsing methods
4. Provides 10 hardcoded questions if all else fails

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `QuizFragment.kt` | `generateQuiz()` when user starts a stage quiz |
| `LearningPathFragment.kt` | `getStageTopics()` for topic display |

---

## Complete Code Walkthrough

### Lines 20-32: Class Setup
Not a singleton — uses `NetworkModule.standardClient` directly. No constructor context needed.

### Lines 34-39: `QuizQuestion` Data Class
Each question has: `question` text, 4 `options`, `correctAnswerIndex` (0-3), and `explanation`.

### Lines 45-122: `generateQuiz()` — Main Entry Point
**Strategy**: Split 10 questions into 2 batches of 5.
1. Loop `callsNeeded` times (typically 2)
2. Build prompt with strict formatting requirements (no code snippets, short questions)
3. Call `callGeminiAPI()` for each batch
4. 500ms delay between API calls
5. If got ≥10 → return first 10
6. If got some but not 10 → fill remaining with fallbacks
7. If got none → return all 10 fallbacks

### Lines 127-151: `callGeminiAPI()` — Retry with Exponential Backoff (N4 fix)
- 3 max retries
- Starts at 1 second delay, doubles each retry (1s, 2s, 4s)
- Does NOT retry on 401/403 (auth errors)

### Lines 153-216: `executeApiCall()` — Raw HTTP Call
Builds Gemini API request manually with:
- `temperature: 0.8` (slightly creative for question variety)
- `maxOutputTokens: 2048`
- Robust null-safety checks on response (H5 fix): validates `candidates`, `content`, `parts`, `text`

### Lines 218-264: `parseQuizResponse()` — Primary Parser
1. Strips ```json markdown wrappers
2. Extracts JSON array by finding `[` and `]` positions
3. Deserializes using `TypeToken<List<QuizQuestion>>`
4. If initial parsing fails → tries `parseQuizQuestionByQuestion()`
5. If still short → fills with fallback questions

### Lines 270-344: `parseQuizQuestionByQuestion()` — Regex Fallback Parser
When JSON is malformed (common with AI), this uses regex to extract:
- `"question"`: text content
- `"options"`: array content
- `"correctAnswerIndex"`: integer
- `"explanation"`: text content

Finds individual `{...}` blocks containing all required fields, then extracts each field with named capture groups. Handles escaped quotes and multi-line text.

### Lines 349-462: `getFallbackQuestions()` — 10 Hardcoded Questions
Covers: GPIO, SPI, Interrupts, RAM, I2C, PWM, Watchdog, Cortex-M0+, ADC, RTOS. Each has 4 options and a one-sentence explanation.

### Lines 467-486: `getStageTopics()` — Topic Map
Maps stage IDs (1-15) to relevant topics. Example:
- Stage 1 → ["embedded systems basics", "microprocessors", "system components"]
- Stage 8 → ["UART", "SPI", "I2C communication"]

---

## Dependencies

| Import | Why |
|--------|-----|
| `NetworkModule` | Shared HTTP client and API URL |
| `Gson`, `JsonObject`, `TypeToken` | JSON parsing (direct and type-safe) |
| `OkHttp` | HTTP requests |

---

## Strengths

- ✅ Batch API calls avoid truncation
- ✅ Exponential backoff with auth-error awareness
- ✅ Dual parsing strategy (Gson + regex fallback)
- ✅ Null-safe response parsing (H5 fix)
- ✅ Partial success handling (fills gaps with fallbacks)

## Weaknesses / Technical Debt

- ⚠️ Creates its own `OkHttpClient` instead of using `NetworkModule` consistently (Line 23 uses NetworkModule but has unused import for manual client)
- ⚠️ Stage topics hardcoded to 15 stages — doesn't match personalized V2 stages
- ⚠️ No input sanitization on `stageTitle`
- ⚠️ Fallback questions are always the same regardless of stage
