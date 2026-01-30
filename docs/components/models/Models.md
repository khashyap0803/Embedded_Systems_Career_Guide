# Data Models

## LearningStage.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/LearningStage.kt`

### Purpose

Represents a **single learning stage** in the 16-stage curriculum.

### Data Class

```kotlin
data class LearningStage(
    val id: Int,                    // 1-16
    val title: String,              // Stage name
    val description: String,        // Short description
    val difficulty: Difficulty,     // BEGINNER, INTERMEDIATE, ADVANCED
    val xpReward: Int,              // Base XP (50)
    val lessonContent: LessonContent?, // Learning material
    var isCompleted: Boolean = false,
    var isLocked: Boolean = true,
    var starsEarned: Int = 0        // 0-3 stars
)

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

data class LessonContent(
    val introduction: String,
    val keyPoints: List<String>,
    val examples: List<String>,
    val summary: String
)
```

### Usage

```kotlin
val stage = LearningStage(
    id = 1,
    title = "Introduction to Embedded Systems",
    difficulty = Difficulty.BEGINNER,
    xpReward = 50
)
```

### Strengths

- ✅ Clear data structure
- ✅ Difficulty enum for type safety
- ✅ Nested content structure

### Weaknesses

- ⚠️ `var` for mutable state
- ⚠️ `lessonContent` nullable

---

## Question.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/Question.kt`

### Purpose

Simple **quiz question** model.

### Data Class

```kotlin
data class Question(
    val question: String,
    val options: List<String>,
    val correctAnswer: Int
)
```

### Usage

Used by `GeminiQuizService` for generated quiz questions.

---

## AssessmentReport.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/AssessmentReport.kt`

### Purpose

Model for **assessment report metadata**.

### Data Class

```kotlin
data class AssessmentReport(
    val userId: String,
    val userName: String,
    val htmlContent: String,
    val timestamp: Long
)
```

### Storage

Reports are stored as HTML strings in Firestore, not in Firebase Storage.

### Strengths

- ✅ Simple structure
- ✅ Timestamp for versioning

### Weaknesses

- ⚠️ HTML stored as string
- ⚠️ No structured feedback data
