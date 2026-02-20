# LearningStage.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/LearningStage.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.models`  
> **Size**: 2,626 bytes (83 lines)

---

## What This File Does (Simple Explanation)

This is the **most important model file** in the app. It defines the data structures for the entire **learning path system** — the 16-stage journey that users follow to learn embedded systems. It contains:

1. **`LearningStage`** — The blueprint for a single learning stage (like "GPIO and Digital I/O")
2. **`UserProgress`** — The user's overall progress (XP, streak, completed stages)
3. **`StageProgress`** — Detailed progress for a single stage (stars, score, completed topics)
4. **`LearningStageItem`** — A combination class for displaying a stage with its progress in the UI
5. **`StageType`** — An enum categorizing stages into types like Foundation, Programming, IoT, etc.
6. **`GameStage`** — A type alias for backward compatibility

Think of this file as the "backbone" of the entire learning experience — everything from displaying stages, tracking completion, awarding XP, and syncing with the cloud depends on these classes.

---

## Why This File Exists

The app's core experience is a **gamified 16-stage learning path**. Each stage covers a specific embedded systems topic, has XP rewards, star ratings, and unlock requirements. This file provides all the data structures needed to:

1. **Display stages** in the UI (title, subtitle, icon, color)
2. **Track progress** in Firebase (completed stages, stars earned, XP)
3. **Manage unlock logic** (a stage unlocks only when the previous one is completed)
4. **Categorize stages** by type (Foundation, Communication, Advanced, etc.)

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `LearningPathFragment.kt` | Creates and displays `LearningStage` objects; updates `StageProgress` |
| `LearningPathAdapter.kt` | Renders `LearningStageItem` objects in the RecyclerView |
| `GameifiedStagesAdapter.kt` | Alternative adapter that also uses `LearningStage` |
| `HomeFragment.kt` | Reads `UserProgress` to display XP and streak on the dashboard |
| `UserProgressSyncService.kt` | Saves/loads `UserProgress` to/from Firebase Firestore |
| `StageContentService.kt` | Maps stage IDs to learning content |
| `GeminiQuizService.kt` | Uses stage topic information to generate relevant quiz questions |
| `QuizActivity.kt` | Reads stage data to know which topic the quiz covers |
| `PracticeFragment.kt` | Shows stage progress statistics |

---

## Complete Code Walkthrough

### Line 1: Package Declaration
```kotlin
package com.example.embeddedsystemscareerguide.models
```

### Line 3: Import
```kotlin
import com.google.firebase.firestore.PropertyName
```
This import is from the **Firebase Firestore SDK**. The `@PropertyName` annotation lets you control the exact field name used when saving/loading from Firestore. Without it, Kotlin property names are used as-is.

### Lines 5-27: `LearningStage` Data Class

```kotlin
data class LearningStage(
    val id: String = "",
    @get:PropertyName("topicName") @set:PropertyName("topicName") var topicName: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val icon: String = "",
    val iconRes: Int = 0,
    val color: String = "",
    val xpReward: Int = 0,
    val topics: List<String> = emptyList(),
    val unlockRequirement: String = "",
    val estimatedDuration: String = "",
    val order: Int = 0,
    val type: String = "foundation",
    var isUnlocked: Boolean = false,
    var isCompleted: Boolean = false,
    var starsEarned: Int = 0,
    var progress: Int = 0
)
```

#### Fields Explained:

| Field | Type | Default | Mutable? | Purpose |
|-------|------|---------|----------|---------|
| `id` | `String` | `""` | No (`val`) | Firebase document ID, e.g., "stage_1" |
| `topicName` | `String` | `""` | Yes (`var`) | The topic name stored in Firestore. Has `@PropertyName` annotation to ensure the Firestore field is always named "topicName" regardless of Kotlin naming conventions |
| `title` | `String` | `""` | No | Display title in the UI, e.g., "GPIO and Digital I/O" |
| `subtitle` | `String` | `""` | No | Short subtitle shown below the title |
| `description` | `String` | `""` | No | Full description of the stage content |
| `icon` | `String` | `""` | No | Icon identifier (could be an emoji or icon name) |
| `iconRes` | `Int` | `0` | No | Android resource ID for a local drawable icon |
| `color` | `String` | `""` | No | Hex color code for the stage's visual theme, e.g., "#3B82F6" |
| `xpReward` | `Int` | `0` | No | Base XP awarded when this stage is completed |
| `topics` | `List<String>` | `emptyList()` | No | List of sub-topics covered in this stage |
| `unlockRequirement` | `String` | `""` | No | Human-readable requirement to unlock this stage |
| `estimatedDuration` | `String` | `""` | No | Estimated time to complete, e.g., "2 hours" |
| `order` | `Int` | `0` | No | Sort order in the learning path (1-16) |
| `type` | `String` | `"foundation"` | No | Category type — maps to `StageType` enum values |
| `isUnlocked` | `Boolean` | `false` | **Yes** (`var`) | Whether the user has unlocked this stage |
| `isCompleted` | `Boolean` | `false` | **Yes** (`var`) | Whether the user has completed this stage |
| `starsEarned` | `Int` | `0` | **Yes** (`var`) | Stars earned from the quiz (0-3) |
| `progress` | `Int` | `0` | **Yes** (`var`) | Percentage progress through the stage (0-100) |

> **Important**: The last 4 fields (`isUnlocked`, `isCompleted`, `starsEarned`, `progress`) are `var` (mutable) while all others are `val` (immutable). This is because these fields change at runtime as the user progresses, while the stage definition itself doesn't change.

> **The `@PropertyName` annotation** on `topicName` ensures that when this object is serialized to/from Firestore, the field is always called "topicName" in the database, even if Kotlin's serialization might mangle the name (e.g., Kotlin generates `getTopicName()` and `setTopicName()` getters/setters, and Firestore uses Java reflection to determine field names).

### Lines 29-38: `UserProgress` Data Class

```kotlin
data class UserProgress(
    val totalXP: Int = 0,
    val currentStage: Int = 1,
    val streakDays: Int = 0,
    val completedStages: List<Int> = emptyList(),
    val stageProgress: Map<Int, StageProgress> = emptyMap()
)
```

This represents the user's **overall progress** across the entire learning path.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `totalXP` | `Int` | `0` | Cumulative XP earned from all stages and quizzes |
| `currentStage` | `Int` | `1` | The stage the user is currently working on (1-indexed) |
| `streakDays` | `Int` | `0` | Number of consecutive days the user has studied |
| `completedStages` | `List<Int>` | `emptyList()` | List of stage numbers that are completed, e.g., `[1, 2, 3]` |
| `stageProgress` | `Map<Int, StageProgress>` | `emptyMap()` | Detailed progress for each stage, keyed by stage number |

### Lines 40-55: `StageProgress` Data Class

```kotlin
data class StageProgress(
    val status: String = "locked",
    val stars: Int = 0,
    val score: Int = 0,
    val stageId: Int = 0,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val starsEarned: Int = 0,
    val progress: Int = 0,
    val completedTopics: List<String> = emptyList(),
    val quizScores: List<Int> = emptyList(),
    val timeSpent: Long = 0L
)
```

This holds detailed progress for **one specific stage**.

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `status` | `String` | `"locked"` | Current status: "locked", "unlocked", or "completed" |
| `stars` | `Int` | `0` | Stars earned (0-3) |
| `score` | `Int` | `0` | Quiz score (0-100%) |
| `stageId` | `Int` | `0` | Which stage this progress belongs to |
| `isUnlocked` | `Boolean` | `false` | Whether the stage is unlocked |
| `isCompleted` | `Boolean` | `false` | Whether the stage is completed |
| `starsEarned` | `Int` | `0` | Stars earned (duplicate of `stars` — see note below) |
| `progress` | `Int` | `0` | Percentage progress (0-100) |
| `completedTopics` | `List<String>` | `emptyList()` | Which sub-topics within the stage are done |
| `quizScores` | `List<Int>` | `emptyList()` | History of quiz scores (for retakes) |
| `timeSpent` | `Long` | `0L` | Total time spent in this stage in milliseconds |

> **Note**: There's redundancy between `stars` and `starsEarned`. Both seem to track the same thing. This is likely technical debt from refactoring.

### Lines 57-63: `LearningStageItem` Data Class

```kotlin
data class LearningStageItem(
    val stage: LearningStage,
    val progress: StageProgress
)
```

A simple **wrapper class** that combines a stage definition with its progress data. This is what the UI adapters receive — it has everything needed to render a stage card (the static stage info + the dynamic user progress).

### Lines 65-77: `StageType` Enum

```kotlin
enum class StageType(val displayName: String, val colorHex: String) {
    FOUNDATION("Foundation", "#10B981"),
    MICROCONTROLLER("Microcontroller", "#3B82F6"),
    PROGRAMMING("Programming", "#8B5CF6"),
    COMMUNICATION("Communication", "#F59E0B"),
    REALTIME("Real-Time", "#EF4444"),
    IOT("IoT", "#06B6D4"),
    ADVANCED("Advanced", "#F97316"),
    INDUSTRY("Industry", "#84CC16")
}
```

An enum that categorizes the 16 stages into 8 types, each with:
- **`displayName`**: A human-readable label like "Foundation"
- **`colorHex`**: A hex color code used for visual styling in the UI

| Type | Color | Stages It Covers |
|------|-------|-----------------|
| FOUNDATION | Green (#10B981) | Introduction to Embedded Systems |
| MICROCONTROLLER | Blue (#3B82F6) | Microcontroller Architecture |
| PROGRAMMING | Purple (#8B5CF6) | C Programming Basics |
| COMMUNICATION | Amber (#F59E0B) | UART, SPI, I2C |
| REALTIME | Red (#EF4444) | RTOS Fundamentals |
| IOT | Cyan (#06B6D4) | IoT topics |
| ADVANCED | Orange (#F97316) | Low Power Design, Debugging |
| INDUSTRY | Lime (#84CC16) | Career Preparation |

### Lines 79-82: Type Alias

```kotlin
typealias GameStage = LearningStage
```

This creates an **alias** so that `GameStage` and `LearningStage` refer to the exact same class. This exists for **backward compatibility** — older parts of the code referred to stages as "GameStages" (the gamified UI context), and instead of renaming everything, this alias lets both names work.

---

## Dependencies

| Import | Why |
|--------|-----|
| `com.google.firebase.firestore.PropertyName` | To control Firestore field naming for the `topicName` property |

---

## Connections to Other Files

```
                    LearningStage.kt (THIS FILE)
                           │
         ┌─────────────────┼─────────────────────┐
         ▼                 ▼                      ▼
  LearningPathFragment  HomeFragment         UserProgressSyncService
  (displays stages)     (shows XP/streak)    (saves to Firestore)
         │                                         │
         ▼                                         ▼
  LearningPathAdapter                        FirestoreManager
  GameifiedStagesAdapter                     (cloud storage)
  (render stage cards)
```

---

## Strengths

- ✅ **Comprehensive modeling** — covers all aspects of the learning system
- ✅ **Firebase-compatible** — all default values, `@PropertyName` annotations
- ✅ **Type safety** — `StageType` enum prevents invalid type strings
- ✅ **Backward compatibility** — `GameStage` typealias avoids breaking old code

## Weaknesses / Technical Debt

- ⚠️ **Redundant fields** — `StageProgress.stars` and `StageProgress.starsEarned` track the same thing
- ⚠️ **Mutable data class fields** — `isUnlocked`, `isCompleted`, `starsEarned`, `progress` are `var` which makes the data class partially mutable, complicating state management
- ⚠️ **String-based status** — `StageProgress.status` uses strings ("locked", "unlocked") instead of an enum
- ⚠️ **`StageType` disconnect** — `LearningStage.type` is a `String`, not the `StageType` enum directly, so there's no compile-time type safety
- ⚠️ **Hardcoded to 16 stages** — the V1 system works with 16 stages; V2 plans to expand to 30-50 personalized stages, requiring a different model (`PersonalizedStage` exists separately)
