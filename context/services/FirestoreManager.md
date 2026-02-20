# FirestoreManager.kt — Hyper-Detailed Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `FirestoreManager.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 981 |
| **Role** | Central Firestore data access layer — manages ALL user data CRUD operations and defines all shared data classes |

---

## 1. Why Does This File Exist?

This is the **single source of truth** for all Firestore interactions in the app. Every feature that reads or writes user data goes through this manager:
- User profiles
- Personalized learning stages
- Stage content (AI-generated lessons)
- Flashcards
- Quiz history
- Overall progress
- Chat history
- Analytics reports
- Project suggestions
- Daily tips
- Assessment reports
- Performance data for stage regeneration

It also defines **all shared data classes** used across the application (lines 792–977).

---

## 2. Imports (Lines 1–12)

```kotlin
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

- **Firebase Firestore** — Cloud NoSQL database
- **`await()`** — Kotlin extension to convert Firebase `Task` → suspend function
- **`SetOptions.merge()`** — Partial document updates (merge instead of overwrite)

---

## 3. Singleton & Collection Constants (Lines 33–66)

```kotlin
class FirestoreManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirestoreManager"

        // SharedPreferences keys
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_USERNAME = "current_username"

        // Collection names
        const val COLLECTION_USERS = "users"
        const val COLLECTION_DATA = "data"
        const val COLLECTION_STAGES = "stages"
        const val COLLECTION_PERSONALIZED_STAGES = "personalized_stages"
        const val COLLECTION_STAGE_CONTENT = "stage_content"
        const val COLLECTION_FLASHCARDS = "flashcards"
        const val COLLECTION_QUIZ_HISTORY = "quiz_history"
        const val COLLECTION_PROGRESS = "progress"
        const val COLLECTION_CHAT_HISTORY = "chat_history"
        const val COLLECTION_ANALYTICS = "analytics"
        const val COLLECTION_PROJECTS = "projects"
        const val COLLECTION_INTERVIEW_PREP = "interview_prep"
        const val COLLECTION_DAILY_TIPS = "daily_tips"
```

**Thread-safe singleton:**
```kotlin
@Volatile
private var instance: FirestoreManager? = null

fun getInstance(context: Context): FirestoreManager {
    return instance ?: synchronized(this) {
        instance ?: FirestoreManager(context.applicationContext).also { instance = it }
    }
}
```

---

## 4. Firestore Document Hierarchy

```
users/
└── {username}/                          ← getUserDocRef()
    ├── (profile fields: uid, email...)  ← saveUserProfile() / getUserProfile()
    ├── personalized_stages/             ← (direct on user doc)
    │   └── {stageId}/
    ├── quiz_history/
    │   └── {stageId_timestamp}/
    ├── progress/
    │   └── current
    ├── chat_history/
    │   └── {timestamp}/
    ├── analytics/
    │   └── {timestamp}/
    ├── projects/
    │   ├── suggestions
    │   └── status_{id}
    ├── daily_tips/
    │   └── {yyyy-MM-dd}/
    └── data/                            ← getDataDocRef()
        └── main
            ├── report                   ← getAssessmentReport()
            └── stages/                  ← getStagesDocRef()
                └── data/
                    ├── personalized_stages/
                    │   └── {stageId}/
                    ├── stage_content/
                    │   └── {stageId}/
                    └── flashcards/
                        └── {stageId}/
```

### Key Design Decision
**Username as document ID** (not Firebase UID):
```kotlin
private fun getUserDocRef(username: String = getCurrentUsername()) =
    firestore.collection(COLLECTION_USERS).document(username)
```
This provides human-readable paths and consistent hierarchy. Falls back to UID when username is unavailable.

---

## 5. Reference Helpers (Lines 73–112)

| Method | Path | Purpose |
|---|---|---|
| `getCurrentUserId()` | — | Firebase Auth UID |
| `getCurrentUsername()` | SharedPrefs → UID fallback | Username for doc paths |
| `getUserDocRef()` | `users/{username}` | User's root document |
| `getDataDocRef()` | `users/{username}/data/main` | Main data document |
| `getStagesDocRef()` | `.../data/main/stages/data` | Stage-related data root |

---

## 6. CRUD Operations by Feature

### User Profile (Lines 116–144)
| Method | Operation |
|---|---|
| `saveUserProfile(profile)` | Create/update with merge |
| `getUserProfile()` | Read → `UserProfile?` |

### Personalized Stages (Lines 148–218)
| Method | Operation |
|---|---|
| `savePersonalizedStages(stages)` | Batch: delete old + write new |
| `getPersonalizedStages()` | Read ordered by ID → `List<PersonalizedStage>` |
| `hasPersonalizedStages()` | Quick existence check (limit 1) |

**Batch save pattern:**
```kotlin
val batch = firestore.batch()
// Delete existing docs
existingDocs.documents.forEach { batch.delete(it.reference) }
// Add new docs
stages.forEach { batch.set(collectionRef.document(it.id.toString()), it) }
batch.commit().await()
```

### Assessment Report (Lines 220–265)
| Method | Operation |
|---|---|
| `getAssessmentReport()` | Read → `Map<String, Any>?` |
| `hasAssessmentReport()` | Existence check |

### Performance Data Collection (Lines 267–381)
```kotlin
suspend fun collectUserPerformanceData(): UserPerformanceData
```
**Complex aggregation** across multiple collections:
1. Reads all personalized stages → finds completed ones
2. Queries quiz history per stage → finds best scores
3. Collects wrong answers from most recent quiz
4. Categorizes topics as weak (≤1 star) or strong (≥3 stars)
5. Calculates total XP and average quiz score

Used by `StageGeneratorService` when regenerating stages after re-assessment.

### Delete Stages (Lines 383–405)
```kotlin
suspend fun deleteAllPersonalizedStages(): Boolean
```
Batch deletes all stage documents. Used before regeneration.

### Stage Content (Lines 409–445)
| Method | Operation |
|---|---|
| `saveStageContent(stageId, content)` | Write AI-generated lesson |
| `getStageContent(stageId)` | Read → `StageContent?` |

### Flashcards (Lines 449–517)
| Method | Operation |
|---|---|
| `saveFlashcards(stageId, flashcards)` | Write flashcard list |
| `getFlashcards(stageId)` | Read with manual deserialization |
| `updateFlashcardReview(stageId, id, needsReview)` | Update review flag |

**Manual deserialization** in `getFlashcards()`:
```kotlin
val flashcardsList = doc.get("flashcards") as? List<Map<String, Any>> ?: emptyList()
val flashcards = flashcardsList.map { map ->
    Flashcard(
        id = (map["id"] as? Number)?.toInt() ?: 0,
        front = map["front"] as? String ?: "",
        // ...
    )
}
```
Firestore stores lists as `List<Map>`, requiring manual field extraction.

### Quiz History (Lines 521–561)
| Method | Operation |
|---|---|
| `saveQuizResult(stageId, result)` | Write with composite key `{stageId}_{timestamp}` |
| `getQuizHistory(stageId)` | Read filtered by stageId, ordered by timestamp |

### Progress Tracking (Lines 565–601)
| Method | Operation |
|---|---|
| `saveProgress(progress)` | Write to `progress/current` |
| `getProgress()` | Read → `UserProgress?` |

### Chat History (Lines 605–668)
| Method | Operation |
|---|---|
| `saveChatMessage(message)` | Write with timestamp as doc ID |
| `getChatHistory(limit)` | Read last N messages, ordered by timestamp |
| `clearChatHistory()` | Batch delete all messages |

### Analytics (Lines 672–709)
| Method | Operation |
|---|---|
| `saveAnalyticsReport(report)` | Write with timestamp as doc ID |
| `getLatestAnalytics()` | Read most recent report |

### Projects (Lines 713–748)
| Method | Operation |
|---|---|
| `saveProjects(projects)` | Write project list + generatedAt |
| `updateProjectStatus(id, status)` | Write status to `status_{id}` doc |

### Daily Tips (Lines 752–789)
| Method | Operation |
|---|---|
| `saveDailyTip(tip)` | Write with date as doc ID |
| `getTodaysTip()` | Read today's doc by `yyyy-MM-dd` key |

---

## 7. Shared Data Classes (Lines 792–977)

This file defines **all domain models** used across the app:

| Data Class | Fields | Used By |
|---|---|---|
| `UserProfile` | uid, displayName, email, photoUrl, createdAt, lastActiveAt, hasCompletedAssessment, assessmentScore | Profile screen, auth flow |
| `PersonalizedStage` | id, title, subtitle, description, topics, difficulty, estimatedMinutes, type, xpReward, isCompleted, starsEarned, isUnlocked | Stage list, content generation |
| `StageContent` | stageId, theory, keyPoints, codeExample, commonMistakes, proTips, miniChallenge, generatedAt | Reading view |
| `CodeExample` | language, code, explanation | Code display |
| `Mistake` | mistake, solution | Tips section |
| `Challenge` | task, hint | Mini challenge |
| `Flashcard` | id, front, back, difficulty, category, needsReview | Flashcard screen |
| `QuizResult` | stageId, score, totalQuestions, correctAnswers, starsEarned, timestamp, timeSpentSeconds | Quiz history |
| `UserProgress` | totalXp, completedStages, totalStages, currentStreak, longestStreak, lastActiveDate, stageProgress, totalQuizzesTaken, averageQuizScore | Progress tracking |
| `StageProgress` | isCompleted, starsEarned, contentRead, flashcardsReviewed, quizPassed | Per-stage progress |
| `ChatMessage` | role ("user"/"assistant"), content, timestamp | Chat screen |
| `AnalyticsReport` | overallAssessment, strengthsAnalysis, improvementAreas, recommendations, motivationalMessage, predictedCompletionDays, weeklyGoal, timestamp | Analytics |
| `Project` | id, title, description, difficulty, estimatedHours, skills, components, learningOutcomes, steps, status | Projects screen |
| `DailyTip` | date, tip, category, actionItem | Daily tip |
| `UserPerformanceData` | completedStageIds, stageScores, stageStars, weakTopics, strongTopics, wrongQuestions, totalXpEarned, averageQuizScore | Stage regeneration |
| `WrongQuestionRecord` | stageId, topic, question, userAnswer, correctAnswer | Performance analysis |

---

## 8. Error Handling Pattern

Every method follows the same pattern:
```kotlin
suspend fun operation(): Result<T> = withContext(Dispatchers.IO) {
    try {
        // Firestore operation
        val doc = ...get().await()
        Result.success(parsed)
    } catch (e: Exception) {
        Log.e(TAG, "Error description", e)
        Result.failure(e)  // or false/null for simpler returns
    }
}
```

- All operations run on `Dispatchers.IO`
- All use `Result<T>` or simple Boolean/nullable returns
- All log errors with the `TAG`
- No retry logic (unlike AI services)

---

## 9. Connection Map

```
                    ┌───────────────────────────────┐
                    │      FirestoreManager         │
                    │      (Singleton)               │
                    │                               │
 ┌────────────┐     │  saveUserProfile()            │
 │ AuthFlow   │────▶│  getUserProfile()             │
 └────────────┘     │                               │
                    │  savePersonalizedStages()      │     ┌──────────────┐
 ┌────────────┐     │  getPersonalizedStages()      │────▶│   Firebase   │
 │ StageGen   │────▶│  deleteAllPersonalizedStages() │     │   Firestore  │
 └────────────┘     │  collectUserPerformanceData()  │◀────│              │
                    │                               │     └──────────────┘
 ┌────────────┐     │  saveStageContent()           │
 │ Content    │────▶│  getStageContent()            │
 │ Service    │     │                               │
 └────────────┘     │  saveFlashcards()             │
                    │  getFlashcards()              │
 ┌────────────┐     │  updateFlashcardReview()      │
 │ Quiz/Chat/ │────▶│                               │
 │ Analytics  │     │  saveQuizResult()             │
 └────────────┘     │  saveChatMessage()            │
                    │  saveAnalyticsReport()         │
                    └───────────────────────────────┘
```

### Used By (Every service that touches Firestore)
- `StageGeneratorService`
- `StageContentService`
- `FlashcardService`
- `GeminiQuizService`
- `GeminiChatService`
- `AnalyticsService`
- `ProjectSuggestionService`
- `DailyTipService`
- `InterviewPrepService`
- `UserProgressSyncService`

---

## 10. Potential Issues & Notes

| Issue | Details |
|---|---|
| **God object tendency** | 981 lines, 30+ methods — could be split by feature |
| **Data classes in same file** | All 16 data classes live here; better as separate files |
| **No offline handling** | Relies on Firestore's built-in offline; no explicit offline-first logic |
| **No pagination** | `getChatHistory(limit=50)` uses `limitToLast` but no cursor-based paging |
| **Batch size limit** | Firestore batches limited to 500 operations; large stage sets could exceed |
| **Username-based paths** | Username changes would orphan all data |
| **No data validation** | Accepts any data shape; corrupt documents could crash parsing |
| **Manual flashcard deserialization** | Inconsistent with `toObject()` pattern used elsewhere |

---

## 11. Summary

`FirestoreManager` is the **central data access layer** for the entire application. It provides CRUD operations for every user-facing feature (profiles, stages, content, flashcards, quizzes, chat, analytics, projects, tips) and defines all shared data models. It uses username-based document paths, the `await()` extension for coroutine-friendly Firestore operations, and a consistent `Result<T>` error handling pattern. It serves as the single point of contact between the app and Firebase Firestore.
