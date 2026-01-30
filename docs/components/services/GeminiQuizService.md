# GeminiQuizService.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiQuizService.kt`

## Purpose

Generates **dynamic quiz questions** for each learning stage using Gemini AI. Creates unique, topic-specific questions that test understanding of embedded systems concepts.

## Functionality

### Core Method

```kotlin
suspend fun generateQuizQuestions(
    topic: String,
    difficulty: String,
    questionCount: Int = 5
): List<QuizQuestion>
```

### Question Structure

```kotlin
data class QuizQuestion(
    val question: String,
    val options: List<String>,      // 4 options (A, B, C, D)
    val correctAnswer: Int,         // Index of correct option
    val explanation: String         // Why answer is correct
)
```

### Difficulty Levels

| Level | Description | XP Multiplier |
|-------|-------------|---------------|
| beginner | Basic concepts | 1x |
| intermediate | Applied knowledge | 1.5x |
| advanced | Expert topics | 2x |

## Why It's Important

1. **Dynamic Content**: Fresh questions on every attempt
2. **Topic-Specific**: Questions match stage content
3. **Educational**: Explanations teach why answers are correct
4. **Gamification**: Stars based on quiz performance

## Where It's Used

| Component | Usage |
|-----------|-------|
| QuizActivity | Generates stage quiz questions |
| LearningPathFragment | Quiz completion tracking |

## API Prompt Engineering

```kotlin
"""
Generate $questionCount multiple choice questions about $topic 
for embedded systems at $difficulty level.

Return JSON format:
{
  "questions": [
    {
      "question": "...",
      "options": ["A", "B", "C", "D"],
      "correctAnswer": 0,
      "explanation": "..."
    }
  ]
}
"""
```

## Scoring System

| Correct Answers | Stars | Performance |
|-----------------|-------|-------------|
| 80-100% | ⭐⭐⭐ | Excellent |
| 50-79% | ⭐⭐ | Good |
| 1-49% | ⭐ | Pass |
| 0% | ❌ | Fail (retry) |

## Strengths

- ✅ AI-generated variety
- ✅ Topic-specific questions
- ✅ Difficulty scaling
- ✅ Educational explanations

## Weaknesses

- ⚠️ Occasional JSON parsing failures
- ⚠️ No question caching
- ⚠️ Depends on API availability
- ⚠️ Quality varies with prompt

## Potential Improvements

1. **Add question bank** as fallback for API failures
2. **Implement caching** to reduce API calls
3. **Add question validation** before display
4. **Track question difficulty** based on user performance
