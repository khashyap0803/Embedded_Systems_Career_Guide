# ChallengeModels.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/challenge/ChallengeModels.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.models.challenge`  
> **Size**: 11,909 bytes (340 lines)

---

## What This File Does (Simple Explanation)

This file contains **all the data models** for the **Pre-Release Event Challenge System** — a publicity event where participants compete in 3 embedded systems challenges. Think of it as a blueprint collection for everything the challenge system needs to track:

- Who is participating (profiles, statuses)
- What they submitted (answers, code)
- How they were evaluated (scores across 6 criteria)
- Where they rank (universal leaderboard)
- What the rules are (time limits, weights, credentials)

Every single piece of data that flows between the challenge UI, the Gemini AI evaluator, and Firebase Realtime Database is defined here.

---

## Why This File Exists

The pre-release event challenge is a **competition system** separate from the main learning app. It needs its own set of data models because:

1. It uses **Firebase Realtime Database** (not Firestore like the main app) for real-time updates
2. It has a completely different data structure (participants, challenges, rankings)
3. It requires specialized models for AI evaluation (6-parameter scoring)
4. It needs admin control structures (termination, warnings, extra time)

All models use the `@IgnoreExtraProperties` and `@PropertyName` annotations from the Firebase Realtime Database SDK (not Firestore SDK) to ensure proper serialization.

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `Challenge1Activity.kt` | Uses `Challenge1Submission`, `EvaluationResult`, `ChallengeConstants` |
| `Challenge2Activity.kt` | Uses `Challenge2Question`, `EvaluationResult`, `ChallengeConstants` |
| `Challenge3Activity.kt` | Uses `Challenge3Question`, `EvaluationResult`, `ChallengeConstants` |
| `ChallengeLoginActivity.kt` | Uses `ChallengeConstants` (email/password) |
| `RollNumberEntryActivity.kt` | Uses `ChallengeConstants` (roll number regex) |
| `AdminDashboardActivity.kt` | Uses `ParticipantStatus`, `ParticipantDetails`, `UniversalRanking` |
| `RankingDashboardActivity.kt` | Uses `RankingEntry`, `UniversalRanking` |
| `PreReleaseEventService.kt` | Uses ALL models for Firebase CRUD operations |
| `GeminiChallengeService.kt` | Uses `EvaluationResult`, `EvaluationCategory`, `GeneratedQuestion` |

---

## Complete Code Walkthrough

### Lines 1-4: Package & Imports
```kotlin
package com.example.embeddedsystemscareerguide.models.challenge

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
```

- `IgnoreExtraProperties`: Tells Firebase to silently ignore any fields in the database that don't exist in the data class (prevents crashes if the database has extra fields)
- `PropertyName`: Controls the exact field name used in Firebase (similar to Firestore's version but from the Realtime Database SDK)

---

### Lines 13-23: `ChallengeConfig`

```kotlin
@IgnoreExtraProperties
data class ChallengeConfig(
    @get:PropertyName("eventActive") @set:PropertyName("eventActive")
    var eventActive: Boolean = false,
    
    @get:PropertyName("eventStartTime") @set:PropertyName("eventStartTime")
    var eventStartTime: Long = 0,
    
    @get:PropertyName("eventEndTime") @set:PropertyName("eventEndTime")
    var eventEndTime: Long = 0
)
```

**Purpose**: Stores the global event configuration. The admin can activate/deactivate the event and set time boundaries.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `eventActive` | `Boolean` | `false` | Whether the challenge event is currently running |
| `eventStartTime` | `Long` | `0` | Unix timestamp when the event starts |
| `eventEndTime` | `Long` | `0` | Unix timestamp when the event ends |

**Firebase Path**: `preReleaseEvent/config/`

---

### Lines 27-37: `ParticipantProfile`

```kotlin
@IgnoreExtraProperties
data class ParticipantProfile(
    @get:PropertyName("rollNumber") @set:PropertyName("rollNumber")
    var rollNumber: String = "",
    
    @get:PropertyName("registeredAt") @set:PropertyName("registeredAt")
    var registeredAt: Long = 0,
    
    @get:PropertyName("lastActiveAt") @set:PropertyName("lastActiveAt")
    var lastActiveAt: Long = 0
)
```

**Purpose**: Basic profile info for a participant. Created when they register with their roll number.

| Field | Type | Purpose |
|-------|------|---------|
| `rollNumber` | `String` | The student's 12-digit roll number (e.g., "160112345678") |
| `registeredAt` | `Long` | Timestamp when they first registered |
| `lastActiveAt` | `Long` | Timestamp of their last activity (updated periodically) |

**Firebase Path**: `preReleaseEvent/participants/{rollNumber}/profile/`

---

### Lines 39-79: `ParticipantStatus`

```kotlin
@IgnoreExtraProperties
data class ParticipantStatus(
    var currentStatus: String = "waiting",
    var terminationReason: String? = null,
    var warningCount: Int = 0,
    var canAccessChallenge2: Boolean = false,
    var canAccessChallenge3: Boolean = false,
    var isTerminated: Boolean = false,
    // Admin dashboard fields (populated dynamically)
    var rollNumber: String = "",
    var totalScore: Int = 0,
    var challenge1Score: Int = 0,
    var challenge2Score: Int = 0,
    var challenge3Score: Int = 0
) {
    companion object {
        const val STATUS_WAITING = "waiting"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_TIMEOUT = "timeout"
        const val STATUS_TERMINATED = "terminated"
        const val STATUS_RESUMABLE = "resumable"
        
        const val TERMINATION_CHEATING = "cheating"
        const val TERMINATION_PHONE_CALL = "phone_call"
        const val TERMINATION_EXIT_CHOICE = "exit_choice"
        const val TERMINATION_NETWORK = "network"
    }
}
```

**Purpose**: Tracks a participant's current state throughout the event. This is the most complex participant model.

#### Core Fields:

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `currentStatus` | `String` | `"waiting"` | Current state — see status constants below |
| `terminationReason` | `String?` | `null` | Why they were terminated (nullable — only set if terminated) |
| `warningCount` | `Int` | `0` | Number of anti-cheating warnings received (3 = auto-terminate) |
| `canAccessChallenge2` | `Boolean` | `false` | Admin must unlock Challenge 2 for each participant |
| `canAccessChallenge3` | `Boolean` | `false` | Admin must unlock Challenge 3 for each participant |
| `isTerminated` | `Boolean` | `false` | Whether the participant has been terminated |

#### Admin Dashboard Fields (not stored in Firebase, populated at runtime):

| Field | Type | Purpose |
|-------|------|---------|
| `rollNumber` | `String` | Copied from profile for display convenience |
| `totalScore` | `Int` | Sum of all challenge scores |
| `challenge1Score` | `Int` | Score from Challenge 1 |
| `challenge2Score` | `Int` | Score from Challenge 2 |
| `challenge3Score` | `Int` | Score from Challenge 3 |

#### Status Constants (Companion Object):

| Constant | Value | Meaning |
|----------|-------|---------|
| `STATUS_WAITING` | `"waiting"` | Participant registered but hasn't started |
| `STATUS_IN_PROGRESS` | `"in_progress"` | Currently working on a challenge |
| `STATUS_COMPLETED` | `"completed"` | Finished all challenges |
| `STATUS_TIMEOUT` | `"timeout"` | Ran out of time on a challenge |
| `STATUS_TERMINATED` | `"terminated"` | Forcibly removed by admin or anti-cheating |
| `STATUS_RESUMABLE` | `"resumable"` | Session can be resumed (e.g., network issue) |

#### Termination Reason Constants:

| Constant | Value | Meaning |
|----------|-------|---------|
| `TERMINATION_CHEATING` | `"cheating"` | Caught switching apps / using other resources |
| `TERMINATION_PHONE_CALL` | `"phone_call"` | Received a phone call during challenge |
| `TERMINATION_EXIT_CHOICE` | `"exit_choice"` | Voluntarily exited |
| `TERMINATION_NETWORK` | `"network"` | Lost network connectivity |

---

### Lines 82-89: `ParticipantDetails`

```kotlin
data class ParticipantDetails(
    val rollNumber: String = "",
    val challenge1Score: Int = 0,
    val challenge2Score: Int = 0,
    val challenge3Score: Int = 0,
    val totalScore: Int = 0,
    val rank: Int = 0
)
```

**Purpose**: A lightweight summary view for the admin dashboard. Contains just the essential info an admin needs to see at a glance.

---

### Lines 93-106: `Challenge1Submission`

```kotlin
@IgnoreExtraProperties
data class Challenge1Submission(
    @get:PropertyName("selectedMcu") @set:PropertyName("selectedMcu")
    var selectedMcu: String = "",
    
    @get:PropertyName("selectedComponents") @set:PropertyName("selectedComponents")
    var selectedComponents: List<String> = emptyList(),
    
    @get:PropertyName("codeBlocks") @set:PropertyName("codeBlocks")
    var codeBlocks: Map<String, String> = emptyMap(),
    
    @get:PropertyName("submittedAt") @set:PropertyName("submittedAt")
    var submittedAt: Long = 0
)
```

**Purpose**: What the user submitted for Challenge 1 (Hardware Selection). Challenge 1 asks users to select an MCU, choose components, and arrange code blocks in the correct order.

| Field | Type | Purpose |
|-------|------|---------|
| `selectedMcu` | `String` | The microcontroller unit they chose (e.g., "Arduino Uno", "STM32") |
| `selectedComponents` | `List<String>` | Hardware components selected (e.g., ["LED", "Resistor", "Buzzer"]) |
| `codeBlocks` | `Map<String, String>` | Code blocks keyed by block ID, value is block text content — preserves ordering and identity |
| `submittedAt` | `Long` | Timestamp of submission |

---

### Lines 108-124: `Challenge2Question`

```kotlin
@IgnoreExtraProperties
data class Challenge2Question(
    var questionId: String = "",
    var scenario: String = "",
    var partialCode: String = "",
    var missingLineCount: Int = 0,
    var userAnswer: String = ""
)
```

**Purpose**: A Challenge 2 question (Code Completion). The user sees partial code with missing lines and must fill them in.

| Field | Type | Purpose |
|-------|------|---------|
| `questionId` | `String` | Unique identifier for the question |
| `scenario` | `String` | The scenario description (e.g., "Write a timer interrupt handler") |
| `partialCode` | `String` | The code with missing sections marked |
| `missingLineCount` | `Int` | How many lines the user needs to fill in (2-4) |
| `userAnswer` | `String` | The code the user wrote to fill the gaps |

---

### Lines 126-139: `Challenge3Question`

```kotlin
@IgnoreExtraProperties
data class Challenge3Question(
    var questionId: String = "",
    var scenario: String = "",
    var requirements: List<String> = emptyList(),
    var userCode: String = ""
)
```

**Purpose**: A Challenge 3 question (Complete Code Writing). The user must write an entire program from scratch.

| Field | Type | Purpose |
|-------|------|---------|
| `questionId` | `String` | Unique identifier |
| `scenario` | `String` | What the code should do |
| `requirements` | `List<String>` | Specific requirements the code must meet |
| `userCode` | `String` | The complete code the user wrote |

---

### Lines 141-157: `ChallengeData`

```kotlin
@IgnoreExtraProperties
data class ChallengeData(
    var status: String = "not_started",
    var startTime: Long? = null,
    var endTime: Long? = null,
    var timeTakenMs: Long? = null,
    var extraTimeGrantedMs: Long = 0
)
```

**Purpose**: Metadata about a single challenge attempt — when it started, ended, how long it took, and any extra time granted by the admin.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `status` | `String` | `"not_started"` | Challenge state: "not_started", "in_progress", "completed", "timeout", "terminated" |
| `startTime` | `Long?` | `null` | When the challenge started (nullable — null if not started) |
| `endTime` | `Long?` | `null` | When the challenge ended |
| `timeTakenMs` | `Long?` | `null` | Total time spent in milliseconds |
| `extraTimeGrantedMs` | `Long` | `0` | Additional time granted by admin in milliseconds |

---

### Lines 161-180: `EvaluationCategory`

```kotlin
@IgnoreExtraProperties
data class EvaluationCategory(
    var score: Int = 0,
    var maxScore: Int = 0,
    var details: String = "",
    var errors: List<String> = emptyList(),
    var missing: List<String> = emptyList(),
    var notes: String = ""
)
```

**Purpose**: One of the **6 evaluation criteria** used to grade a submission. The Gemini AI scores each category independently.

| Field | Type | Purpose |
|-------|------|---------|
| `score` | `Int` | Points earned in this category |
| `maxScore` | `Int` | Maximum possible points |
| `details` | `String` | AI-generated explanation of the score |
| `errors` | `List<String>` | Specific errors found |
| `missing` | `List<String>` | Required elements that were missing |
| `notes` | `String` | Additional AI feedback |

---

### Lines 182-219: `EvaluationResult`

```kotlin
@IgnoreExtraProperties
data class EvaluationResult(
    var attemptCompleteness: EvaluationCategory = EvaluationCategory(),
    var syntaxCorrectness: EvaluationCategory = EvaluationCategory(),
    var logicAccuracy: EvaluationCategory = EvaluationCategory(),
    var criticalElements: EvaluationCategory = EvaluationCategory(),
    var codeQuality: EvaluationCategory = EvaluationCategory(),
    var errorCount: EvaluationCategory = EvaluationCategory(),
    var totalScore: Int = 0,
    var maxScore: Int = 100,
    var percentage: Double = 0.0,
    var weightedScore: Int = 0,
    var feedback: String = "",
    var evaluatedAt: Long = 0
)
```

**Purpose**: The **complete evaluation** of a challenge submission across all 6 criteria. This is what the Gemini AI returns after analyzing the user's work.

#### The 6 Evaluation Categories:

| Category | What It Measures |
|----------|-----------------|
| `attemptCompleteness` | Did they attempt all parts? Are all required sections present? |
| `syntaxCorrectness` | Is the code syntactically valid? Will it compile? |
| `logicAccuracy` | Does the logic correctly solve the problem? |
| `criticalElements` | Are critical elements present (e.g., ISR setup, pin configuration)? |
| `codeQuality` | Code organization, naming conventions, comments, efficiency |
| `errorCount` | Number of bugs, off-by-one errors, memory leaks, etc. |

#### Summary Fields:

| Field | Type | Purpose |
|-------|------|---------|
| `totalScore` | `Int` | Sum of all category scores |
| `maxScore` | `Int` | Maximum possible (always 100) |
| `percentage` | `Double` | totalScore / maxScore * 100 |
| `weightedScore` | `Int` | Score after applying challenge weight (1.0x, 1.5x, 2.0x) |
| `feedback` | `String` | Overall AI feedback message |
| `evaluatedAt` | `Long` | Timestamp of evaluation |

---

### Lines 221-228: `ChallengeScores`

```kotlin
@IgnoreExtraProperties
data class ChallengeScores(
    var rawScore: Int = 0,
    var weightedScore: Int = 0
)
```

**Purpose**: Simple pair of raw and weighted scores for a single challenge.

---

### Lines 232-260: `UniversalRanking`

```kotlin
@IgnoreExtraProperties
data class UniversalRanking(
    var challenge1Score: Int = 0,
    var challenge2Score: Int = 0,
    var challenge3Score: Int = 0,
    var totalWeightedScore: Int = 0,
    var maxPossibleScore: Int = 450,
    var percentage: Double = 0.0,
    var totalTimeTakenMs: Long = 0,
    var rank: Int = 0,
    var lastUpdatedAt: Long = 0
)
```

**Purpose**: A participant's position in the universal ranking. Combines scores from all 3 challenges with weights.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `challenge1Score` | `Int` | `0` | Weighted score for Challenge 1 (max 100) |
| `challenge2Score` | `Int` | `0` | Weighted score for Challenge 2 (max 150) |
| `challenge3Score` | `Int` | `0` | Weighted score for Challenge 3 (max 200) |
| `totalWeightedScore` | `Int` | `0` | Sum of all weighted scores |
| `maxPossibleScore` | `Int` | `450` | 100 + 150 + 200 = 450 (the theoretical maximum) |
| `percentage` | `Double` | `0.0` | Total / Max * 100 |
| `totalTimeTakenMs` | `Long` | `0` | Time across all challenges (tiebreaker) |
| `rank` | `Int` | `0` | Current rank position (1 = best) |
| `lastUpdatedAt` | `Long` | `0` | When this ranking was last recalculated |

> **Scoring Math**: Max total = (100 × 1.0) + (100 × 1.5) + (100 × 2.0) = 100 + 150 + 200 = **450 points**

---

### Lines 264-283: `RankingEntry`

```kotlin
@IgnoreExtraProperties
data class RankingEntry(
    var rollNumber: String = "",
    var totalScore: Int = 0,
    var ch1: Int = 0,
    var ch2: Int = 0,
    var ch3: Int = 0,
    var totalTimeMs: Long = 0
)
```

**Purpose**: A lightweight version of `UniversalRanking` optimized for the **leaderboard UI**. Uses short field names (`ch1`, `ch2`, `ch3`) to minimize database read costs.

---

### Lines 287-299: `GeneratedQuestion`

```kotlin
data class GeneratedQuestion(
    val questionId: String,
    val scenario: String,
    val components: List<String>,
    val problemStatement: String,
    val partialCode: String = "",
    val missingLineCount: Int = 0,
    val requirements: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val correctAnswer: String = "",
    val referenceCode: String = "",
    val evaluationCriteria: List<String> = emptyList()
)
```

**Purpose**: Output from the Gemini AI when generating challenge questions. This is a **unified question model** that works for all 3 challenge types — some fields are only used by specific challenges.

| Field | Used By | Purpose |
|-------|---------|---------|
| `questionId` | All | Unique ID |
| `scenario` | All | Problem description |
| `components` | Ch1 | Hardware components involved |
| `problemStatement` | All | What the student needs to do |
| `partialCode` | Ch2 only | Code with missing lines |
| `missingLineCount` | Ch2 only | How many lines are missing |
| `requirements` | Ch3 only | What the code must accomplish |
| `hints` | All | Optional hints |
| `correctAnswer` | Ch2 | The expected missing code |
| `referenceCode` | Ch3 | A reference implementation for evaluation |
| `evaluationCriteria` | All | What the AI should focus on when grading |

---

### Lines 303-339: `ChallengeConstants` Object

```kotlin
object ChallengeConstants {
    const val CHALLENGE_1_TIME_MS = 20 * 60 * 1000L  // 20 minutes
    const val CHALLENGE_2_TIME_MS = 20 * 60 * 1000L  // 20 minutes
    const val CHALLENGE_3_TIME_MS = 30 * 60 * 1000L  // 30 minutes
    
    const val CHALLENGE_1_WEIGHT = 1.0
    const val CHALLENGE_2_WEIGHT = 1.5
    const val CHALLENGE_3_WEIGHT = 2.0
    
    const val MAX_RAW_SCORE = 100
    const val MAX_WEIGHTED_SCORE_CH1 = 100
    const val MAX_WEIGHTED_SCORE_CH2 = 150
    const val MAX_WEIGHTED_SCORE_CH3 = 200
    const val MAX_TOTAL_SCORE = 450
    
    const val QUESTIONS_PER_CHALLENGE = 3
    
    const val PATH_PRE_RELEASE_EVENT = "preReleaseEvent"
    const val PATH_CONFIG = "config"
    const val PATH_PARTICIPANTS = "participants"
    const val PATH_RANKINGS = "rankings"
    const val PATH_UNIVERSAL = "universal"
    
    const val USER_EMAIL = "exam1234@challenge.app"
    const val USER_PASSWORD = "exam1234"
    const val ADMIN_EMAIL = "admin@challenge.app"
    const val ADMIN_PASSWORD = "admin1234"
    
    val ROLL_NUMBER_REGEX = Regex("^1601[0-9]{8}$")
}
```

**Purpose**: A **singleton object** containing all constants used across the challenge system.

#### Time Limits:

| Constant | Value | Human Readable |
|----------|-------|---------------|
| `CHALLENGE_1_TIME_MS` | `1,200,000` | 20 minutes |
| `CHALLENGE_2_TIME_MS` | `1,200,000` | 20 minutes |
| `CHALLENGE_3_TIME_MS` | `1,800,000` | 30 minutes |

#### Score Weights:

| Challenge | Weight | Max Raw → Max Weighted |
|-----------|--------|----------------------|
| Challenge 1 (Easy) | 1.0x | 100 → 100 |
| Challenge 2 (Medium) | 1.5x | 100 → 150 |
| Challenge 3 (Hard) | 2.0x | 100 → 200 |

#### Firebase Paths:

| Constant | Value | Points To |
|----------|-------|-----------|
| `PATH_PRE_RELEASE_EVENT` | `"preReleaseEvent"` | Root node in Realtime Database |
| `PATH_CONFIG` | `"config"` | Event configuration |
| `PATH_PARTICIPANTS` | `"participants"` | All participant data |
| `PATH_RANKINGS` | `"rankings"` | Universal rankings |
| `PATH_UNIVERSAL` | `"universal"` | Universal rankings sub-node |

#### Credentials:

| Constant | Value | Used For |
|----------|-------|----------|
| `USER_EMAIL` | `"exam1234@challenge.app"` | Participant login |
| `USER_PASSWORD` | `"exam1234"` | Participant password |
| `ADMIN_EMAIL` | `"admin@challenge.app"` | Admin login |
| `ADMIN_PASSWORD` | `"admin1234"` | Admin password |

> ⚠️ **Security Warning**: These credentials are hardcoded in the source code and will be compiled into the APK. Anyone who decompiles the APK can extract them.

#### Roll Number Validation:

```kotlin
val ROLL_NUMBER_REGEX = Regex("^1601[0-9]{8}$")
```
Validates that a roll number:
- Starts with `1601`
- Followed by exactly 8 digits
- Total length: 12 characters
- Example valid input: `160112345678`

---

## Dependencies

| Import | Why |
|--------|-----|
| `com.google.firebase.database.IgnoreExtraProperties` | Prevents crashes when database has fields not in the model |
| `com.google.firebase.database.PropertyName` | Controls exact field names in Firebase Realtime Database |

---

## Connections to Other Files

```
ChallengeModels.kt (THIS FILE)
         │
         ├──► ChallengeLoginActivity.kt (reads credentials)
         ├──► RollNumberEntryActivity.kt (validates roll numbers)
         ├──► Challenge1Activity.kt (uses submission + evaluation models)
         ├──► Challenge2Activity.kt (uses question + evaluation models)
         ├──► Challenge3Activity.kt (uses question + evaluation models)
         ├──► AdminDashboardActivity.kt (uses participant + ranking models)
         ├──► RankingDashboardActivity.kt (uses ranking models)
         ├──► PreReleaseEventService.kt (uses ALL models for Firebase ops)
         └──► GeminiChallengeService.kt (uses evaluation + question models)
```

---

## Strengths

- ✅ **Comprehensive** — covers every data need of the challenge system in one file
- ✅ **Firebase-compatible** — proper annotations for Realtime Database serialization
- ✅ **Well-organized** — sections clearly separated with comments
- ✅ **Constants centralized** — all magic numbers in `ChallengeConstants`
- ✅ **Status constants** — uses companion object constants instead of raw strings

## Weaknesses / Technical Debt

- ⚠️ **Hardcoded credentials** — `USER_EMAIL`, `USER_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` are in source code
- ⚠️ **Large file** — 340 lines with 23+ data classes could be split into multiple files
- ⚠️ **String-based status** — `ChallengeData.status` and `ParticipantStatus.currentStatus` use strings instead of enums
- ⚠️ **Mixed mutability** — some fields are `var` (needed for Firebase) but this makes the models less safe
- ⚠️ **Redundant models** — `ParticipantDetails` overlaps significantly with `ParticipantStatus`
- ⚠️ **`@PropertyName` on every field** — verbose; could use `@field:PropertyName` or configure Firebase to use Kotlin naming
