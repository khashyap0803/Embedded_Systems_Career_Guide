# AnalyticsService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/AnalyticsService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 16,633 bytes (411 lines)

---

## What This File Does (Simple Explanation)

This service provides **AI-powered learning analytics** — it analyzes a user's learning progress across all stages, identifies strengths and weaknesses, and generates personalized recommendations. Think of it as a "smart report card" that not only shows your grades but also tells you what to study next and how to improve.

It uses a **dual approach**: first tries to generate recommendations using Gemini AI, and if that fails, falls back to rule-based logic.

---

## Why This File Exists

The V2 version of the app adds personalized analytics beyond simple XP/streak tracking. This service:
1. Aggregates data from Firestore (stages, progress, streaks)
2. Identifies strong/weak topics based on star ratings
3. Generates motivational messages and weekly goals
4. Creates AI recommendations via `GeminiServiceV2`
5. Saves analytics reports back to Firestore

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `HomeFragment.kt` | `getQuickStats()` for dashboard metrics |
| `ProfileFragment.kt` | `generateAnalytics()` for detailed analytics view |
| `PracticeFragment.kt` | Recommendations for focused practice |

---

## Complete Code Walkthrough

### Lines 21-34: Singleton Pattern
Thread-safe singleton using double-checked locking. Uses `context.applicationContext` to prevent memory leaks.

### Lines 36-38: Dependencies
- `geminiService` — `GeminiServiceV2` for AI content generation
- `firestoreManager` — for reading/writing Firestore data
- `gson` — for JSON parsing

### Lines 43-56: `LearningAnalytics` Data Class
Contains 12 metrics: `totalXP`, `totalStages`, `completedStages`, `averageStars`, `totalTimeMinutes`, `currentStreak`, `longestStreak`, `strongTopics`, `weakTopics`, `progressPercentage`, `estimatedDaysToComplete`, `learningPace` ("slow"/"steady"/"fast").

### Lines 61-66: `Recommendation` Data Class
AI-generated recommendations with 4 types: "focus", "practice", "review", "rest". Priority 1 = highest.

### Lines 71-75: `AnalyticsCallback` Interface
Callback pattern with 3 methods: `onProgress(message)`, `onSuccess(analytics, recommendations)`, `onError(error)`.

### Lines 80-155: `generateAnalytics()` — Main Entry Point
1. Loads personalized stages + user progress from Firestore
2. Calculates basic metrics (XP, completed count, average stars)
3. Categorizes topics into strong (3 stars) and weak (1 star)
4. Estimates days to completion
5. Generates AI recommendations (with fallback)
6. Saves analytics report to Firestore
7. Returns via callback

### Lines 178-191: `categorizeTopics()`
Simple but effective: stages with ≥3 stars are "strong", stages with 1 star are "weak". Returns top 5 of each.

### Lines 196-211: `estimateCompletionDays()`
Estimates remaining days based on pace: 2 stages/day if streak ≥ 7, otherwise 1/day.

### Lines 227-255: `generateRecommendations()` — AI + Fallback
1. Builds prompt using `GeminiServiceV2.PromptTemplates.analytics()`
2. Calls Gemini AI with 2048 max tokens
3. Parses JSON response into `Recommendation` objects
4. If AI fails → falls back to `generateRuleBasedRecommendations()`

### Lines 260-291: `parseRecommendations()`
Strips ```json wrapper, parses with Gson, extracts `recommendations` array.

### Lines 296-379: `generateRuleBasedRecommendations()` — Fallback
Generates 2-4 recommendations based on rules:
- Progress < 20% → "Build Your Foundation"
- Progress < 50% → "Keep Up the Momentum"
- Progress < 80% → "Time for Deep Review"
- Progress ≥ 80% → "Approaching Mastery"
- Weak topics → "Strengthen Weak Areas"
- Streak = 0 → "Start a New Streak"
- Streak ≥ 7 → "Amazing Streak!"
- Avg stars < 2 → "Aim for 3 Stars"

### Lines 384-409: `getQuickStats()` — Lightweight Dashboard Data
Same data collection as `generateAnalytics()` but without AI recommendations. Used for the home dashboard where speed matters more than depth.

---

## Dependencies

| Import | Why |
|--------|-----|
| `GeminiServiceV2` | AI content generation |
| `FirestoreManager` | Cloud data storage |
| `Gson`, `JsonObject` | JSON parsing |
| `Dispatchers.IO` | Background threading |

---

## Strengths

- ✅ AI + rule-based dual approach with graceful fallback
- ✅ Thread-safe singleton
- ✅ Callback pattern with progress updates
- ✅ Saves analytics reports for historical tracking

## Weaknesses / Technical Debt

- ⚠️ Uses `PersonalizedStage` (V2 model) — may not work with V1's `LearningStage`
- ⚠️ `totalTimeMinutes` is always 0 (not tracked)
- ⚠️ Star-to-percentage conversion is rough (`averageStars * 33`)
- ⚠️ Completion estimation is very simplistic (1-2 stages/day)
