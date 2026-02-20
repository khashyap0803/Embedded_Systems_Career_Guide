# Question.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/models/Question.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.models`  
> **Size**: 126 bytes (7 lines)

---

## What This File Does (Simple Explanation)

This is the **simplest model file** in the entire app. It defines a single data class called `Question` that holds two pieces of information: an ID number and the question text. This is the basic building block for assessment questions shown to the user during the initial skill assessment.

Think of it as a labelled index card — the `id` is the card number and `question` is what's written on the card.

---

## Why This File Exists

The initial assessment flow (`AssessmentActivity`) displays 50 questions to the user. Each question needs a unique identifier (so the app knows which question it's dealing with) and the question text. This data class provides that minimal structure.

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `AssessmentActivity.kt` | Creates a list of `Question` objects to display during the assessment quiz |
| `GeminiReportService.kt` | Uses `Question` objects (via `AssessmentQuestion` type) when building prompts for the AI |

---

## Complete Code Walkthrough

### Line 1: Package Declaration
```kotlin
package com.example.embeddedsystemscareerguide.models
```
Places this in the `models` package alongside all other data structures.

### Lines 3-6: `Question` Data Class
```kotlin
data class Question(
    val id: Int,
    val question: String
)
```

A minimal data class with just two fields:

| Field | Type | Purpose |
|-------|------|---------|
| `id` | `Int` | A unique integer identifier for the question (1, 2, 3... 50) |
| `question` | `String` | The full text of the question displayed to the user |

> **Important Difference from `QuestionAnswer`**: This class is for **displaying** questions to the user. The `QuestionAnswer` class (in `AssessmentReport.kt`) is for **storing** the user's answers alongside the questions. They serve different purposes in different stages of the assessment flow.

---

## Dependencies

**Zero imports** — this is a pure Kotlin data class. No Firebase annotations, no external libraries.

---

## Connections to Other Files

```
Question.kt ──used by──► AssessmentActivity.kt (displays questions)
                              │
                         user answers
                              │
                              ▼
                    QuestionAnswer (stores Q+A pairs)
                              │
                              ▼
                    GeminiReportService.kt (generates report)
```

---

## Strengths

- ✅ **Extremely simple** — minimal, does exactly what it needs to
- ✅ **Immutable** — both fields are `val` (read-only)

## Weaknesses / Technical Debt

- ⚠️ **No default values** — unlike `AssessmentReport`, this class has no defaults, which means it cannot be deserialized from Firebase without all fields present
- ⚠️ **No answer options** — this only holds the question text, not the answer choices. Answer options are likely managed elsewhere (possibly hardcoded in the assessment UI)
- ⚠️ **Doesn't match the quiz model** — `GeminiQuizService` uses a separate `QuizQuestion` data class with options and explanations. Having two different question models can cause confusion
