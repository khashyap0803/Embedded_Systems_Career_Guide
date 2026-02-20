# StageGeneratorService.kt — Hyper-Detailed Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `StageGeneratorService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 562 |
| **Role** | AI-powered personalized stage generator — creates 40 learning stages tailored to assessment results |

---

## 1. Why Does This File Exist?

After a user completes the initial assessment, the app needs to create a **personalized learning curriculum**. This service:

1. Analyzes assessment results to identify weak and strong areas
2. Generates a prompt for Gemini AI with those areas
3. Parses the AI response into `PersonalizedStage` objects
4. Saves them to Firestore
5. Handles regeneration (re-assessment) with learning history consideration

---

## 2. Imports (Lines 1–10)

```kotlin
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
```

---

## 3. Class Structure (Lines 22–42)

```kotlin
class StageGeneratorService(private val context: Context) {

    companion object {
        private const val TAG = "StageGeneratorService"
        private const val TARGET_STAGES = 40     // Target number of stages
        private const val MAX_RETRIES = 3        // Maximum API retry attempts
        private const val INITIAL_RETRY_DELAY_MS = 1000L  // 1 second
```

### Singleton
```kotlin
@Volatile
private var instance: StageGeneratorService? = null

fun getInstance(context: Context): StageGeneratorService {
    return instance ?: synchronized(this) {
        instance ?: StageGeneratorService(context.applicationContext).also { instance = it }
    }
}
```

### Dependencies
```kotlin
private val geminiService = GeminiServiceV2.getInstance(context)
private val firestoreManager = FirestoreManager.getInstance(context)
private val gson = Gson()
```

Uses `GeminiServiceV2` (not direct HTTP), unlike `GeminiChallengeService` and `GeminiReportService`.

---

## 4. Data Classes (Lines 47–67)

### AssessmentResult
```kotlin
data class AssessmentResult(
    val totalScore: Int = 0,
    val maxScore: Int = 100,
    val topicScores: Map<String, TopicScore> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TopicScore(
    val score: Int = 0,
    val maxScore: Int = 0,
    val percentage: Int = 0
)
```

### GenerationCallback
```kotlin
interface GenerationCallback {
    fun onProgress(phase: Int, message: String)
    fun onSuccess(stages: List<PersonalizedStage>)
    fun onError(error: String)
}
```

---

## 5. Stage Generation: `generatePersonalizedStages()` (Lines 76–184)

```kotlin
suspend fun generatePersonalizedStages(
    userName: String,
    assessmentResult: AssessmentResult,
    callback: GenerationCallback
) = withContext(Dispatchers.IO) { ... }
```

### Execution Flow

```
┌────────────────────────────────┐
│ Phase 1: Analyze Assessment    │
│  → categorizeTopics()          │
│  → Weak areas (<60%) vs       │
│    Strong areas (≥60%)         │
├────────────────────────────────┤
│ Phase 2: Generate Prompt       │
│  → PromptTemplates             │
│    .personalizedStages()       │
│  → Request 40 stages           │
├────────────────────────────────┤
│ Phase 3: Call Gemini API       │
│  → max 8192 output tokens      │
│  → Retry up to 3 times         │
│  → Exponential backoff         │
├────────────────────────────────┤
│ Phase 4: Parse JSON Response   │
│  → parseStagesFromResponse()   │
│  → fixMalformedJson()          │
│  → Create PersonalizedStage    │
│    objects                     │
├────────────────────────────────┤
│ Phase 5: Save to Firestore     │
│  → Unlock first stage          │
│  → firestoreManager            │
│    .savePersonalizedStages()   │
└────────────────────────────────┘
```

### Retry Logic
```kotlin
for (attempt in 1..MAX_RETRIES) {
    val prompt = if (attempt == 1) {
        GeminiServiceV2.PromptTemplates.personalizedStages(...)
    } else {
        // Add stricter JSON instruction on retry
        prompt + "\n\nIMPORTANT: Ensure valid, complete JSON."
    }

    val result = geminiService.generateContent(prompt, maxOutputTokens = 8192)
    // ... parse and validate ...

    if (stages.isNotEmpty()) break // Success

    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl (attempt - 1))
    delay(delayMs)  // 1s, 2s, 4s
}
```

### First Stage Unlocking
```kotlin
val unlockedStages = stages.mapIndexed { index, stage ->
    stage.copy(isUnlocked = index == 0)  // Only first stage unlocked
}
```

---

## 6. Topic Categorization: `categorizeTopics()` (Lines 189–239)

```kotlin
private fun categorizeTopics(assessment: AssessmentResult): Pair<List<String>, List<String>>
```

### With Detailed Scores
```kotlin
assessment.topicScores.forEach { (topic, scoreData) ->
    if (scoreData.percentage < 60) weakAreas.add(topic)
    else strongAreas.add(topic)
}
```

### Without Detailed Scores (Fallback)
Uses overall percentage to assign default topic lists:

| Score Range | Weak Areas | Strong Areas |
|---|---|---|
| < 50% | C Basics, MCU Fundamentals, Protocols, RTOS, Debugging | (none) |
| 50–74% | Advanced C, RTOS, IoT | C Basics, MCU Fundamentals |
| ≥ 75% | Advanced RTOS, Optimization, Industry Practices | C Basics, MCU, Protocols |

---

## 7. JSON Parsing: `parseStagesFromResponse()` (Lines 245–301)

```kotlin
private fun parseStagesFromResponse(response: String): List<PersonalizedStage>
```

### Parsing Steps
1. Remove markdown code blocks (`\`\`\`json ... \`\`\``)
2. Extract JSON object from surrounding text
3. Call `fixMalformedJson()` to repair common AI issues
4. Parse as `JsonObject` with `"stages"` root array
5. Map each element to `PersonalizedStage` with field-level null safety

### Per-element parsing:
```kotlin
PersonalizedStage(
    id = obj.get("id")?.asInt ?: (stages.size + 1),
    title = obj.get("title")?.asString ?: "Stage ${stages.size + 1}",
    topics = obj.getAsJsonArray("topics")?.mapNotNull { it.asString?.trim() } ?: emptyList(),
    difficulty = obj.get("difficulty")?.asString ?: "beginner",
    // ...
    isCompleted = false,
    starsEarned = 0,
    isUnlocked = false
)
```

---

## 8. JSON Repair: `fixMalformedJson()` (Lines 307–377)

```kotlin
private fun fixMalformedJson(json: String): String
```

Handles common AI response issues:

### 1. Remove Trailing Commas
Uses `removeTrailingCommas()` — a character-by-character approach (not regex) for Android ICU compatibility.

### 2. Smart Truncation Repair
Detects truncated stage objects mid-JSON:
```kotlin
// Find last complete stage object
for ((i, char) in stagesSection.withIndex()) {
    if (!inString && char == '}') {
        depth--
        if (depth == 0) lastCompleteObjectEnd = i
    }
}
// If remainder has unclosed objects, truncate
if (remainder.contains('{') && !remainder.contains('}')) {
    fixed = fixed.substring(0, lastCompleteObjectEnd + 1) + "]}"
}
```
This is critical because Gemini may truncate mid-object when generating 40 stages.

### 3. Balance Brackets/Braces
Counts open/close pairs and appends missing closers.

### `removeTrailingCommas()` (Lines 383–403)
Manual character scanning (avoids regex for Android compatibility):
```kotlin
private fun removeTrailingCommas(json: String, closingChar: Char): String {
    // Scans for ',' followed by whitespace + closingChar
    // Skips the comma when found
}
```

---

## 9. Stage Regeneration: `regenerateStages()` (Lines 434–559)

```kotlin
suspend fun regenerateStages(
    userName: String,
    assessmentResult: AssessmentResult,
    callback: GenerationCallback
) = withContext(Dispatchers.IO) { ... }
```

### Additional Steps vs Initial Generation

1. **Collects performance history** via `firestoreManager.collectUserPerformanceData()`
2. **Uses enhanced prompt** via `PromptTemplates.regenerateStagesWithHistory()` that includes:
   - Previously completed stages
   - Quiz scores and stars
   - Weak/strong topics from history
   - Wrong answer records
3. **Deletes old stages** from Firestore
4. **Resets stage progress** via `UserProgressSyncService.resetStageProgressInCloud()`
5. **Saves new stages** with first stage unlocked

### Key Difference from Initial Generation
```kotlin
// Collect performance history (regeneration only)
val performanceData = firestoreManager.collectUserPerformanceData()

// Use history-aware prompt
val prompt = GeminiServiceV2.PromptTemplates.regenerateStagesWithHistory(
    userName, weakAreas, strongAreas, performanceData, TARGET_STAGES
)
```

---

## 10. Utility Methods (Lines 408–420)

```kotlin
suspend fun hasPersonalizedStages(): Boolean =
    firestoreManager.hasPersonalizedStages()

suspend fun getPersonalizedStages(): Result<List<PersonalizedStage>> =
    firestoreManager.getPersonalizedStages()
```

Thin wrappers around `FirestoreManager` for convenience.

### No Fallback Stages
```kotlin
// NOTE: Fallback stages removed - app is 100% cloud-only
// All stage generation must succeed via AI or return an error
```
The app deliberately has **no hardcoded fallback stages** — everything is AI-generated.

---

## 11. Connection Map

```
┌──────────────────┐     ┌──────────────────────────┐
│ Assessment       │     │                          │
│ Complete UI      │────▶│ StageGeneratorService    │
│                  │     │  (Singleton)             │
│ Retake           │     │                          │
│ Assessment UI    │────▶│  ┌────────────────────┐  │
└──────────────────┘     │  │ GeminiServiceV2    │  │
                         │  │ (AI content gen)   │  │
                         │  └─────────┬──────────┘  │
                         │            │              │
                         │  ┌─────────▼──────────┐  │
                         │  │ FirestoreManager   │  │
                         │  │ (save/load stages) │  │
                         │  └─────────┬──────────┘  │
                         │            │              │     ┌──────────────────┐
                         │  ┌─────────▼──────────┐  │────▶│ UserProgress     │
                         │  │ UserProgressSync   │  │     │ SyncService      │
                         │  │ (reset on regen)   │  │     │ (reset progress) │
                         │  └────────────────────┘  │     └──────────────────┘
                         └──────────────────────────┘
```

---

## 12. Potential Issues & Notes

| Issue | Details |
|---|---|
| **No fallback stages** | If AI fails all retries, user sees error with no content |
| **40-stage token risk** | 8192 output tokens may not fit 40 detailed stage objects |
| **Topic categorization** | Hardcoded default topics if detailed scores unavailable |
| **No incremental save** | All stages saved in one batch; failure = all lost |
| **JSON repair heuristics** | May incorrectly truncate valid but unusual JSON |
| **Progress reset** | Regeneration permanently deletes old progress |

---

## 13. Summary

`StageGeneratorService` is the **AI curriculum builder**. It takes assessment results, categorizes topics by strength/weakness, generates a personalized 40-stage learning path via Gemini AI, repairs potentially malformed JSON responses, and saves the stages to Firestore. For re-assessments, it additionally considers the user's learning history (completed stages, quiz scores, weak topics) to create a smarter curriculum, resets prior progress, and saves the new stages. It is 100% cloud-dependent with no hardcoded fallback content.
