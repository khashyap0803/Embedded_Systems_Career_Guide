# UserProgressSyncService.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/UserProgressSyncService.kt`

## Purpose

The **single source of truth** for all user progress operations. Handles loading, saving, and synchronizing user progress data with Firebase Firestore.

## Functionality

### Core Methods

| Method | Description | Usage |
|--------|-------------|-------|
| `loadProgressFromCloud()` | Loads user progress from Firestore | HomeFragment, LearningPathFragment, PracticeFragment |
| `saveProgress(progress)` | Saves progress object to cloud | LearningPathFragment (stage completion) |
| `completeStageInCloud()` | Marks stage complete with XP/stars | LearningPathFragment (quiz completion) |
| `updateStarsInCloud()` | Updates stars for retakes | LearningPathFragment (quiz retake) |
| `updateStreakInCloud()` | Updates daily streak | LearningPathFragment (streak system) |

### Data Model

```kotlin
data class UserProgress(
    val totalXP: Int = 0,
    val currentStage: Int = 1,
    val streak: Int = 1,
    val bestStreak: Int = 1,
    val lastVisitDate: String = "",
    val completedStages: List<String> = emptyList(),
    val stageStars: Map<String, Int> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### Cloud Path Structure

```
Firestore:
└── users/
    └── {username}/
        └── data/
            └── progress (document)
                ├── totalXP: 370
                ├── currentStage: 4
                ├── streak: 3
                ├── completedStages: ["1", "2", "3"]
                └── stageStars: {1: 3, 2: 3, 3: 1}
```

## Why It's Important

1. **Cloud-Only Architecture**: All progress reads/writes go through this service
2. **Consistency**: Single point of control for progress data
3. **Cross-Device Sync**: Same progress on any device
4. **Streak Bug Fix**: Previous local storage caused streak resets

## Where It's Used

| Component | Methods Used |
|-----------|--------------|
| HomeFragment | `loadProgressFromCloud()` |
| LearningPathFragment | `loadProgressFromCloud()`, `saveProgress()`, `completeStageInCloud()` |
| PracticeFragment | `loadProgressFromCloud()` |
| LoginActivity | `loadProgressFromCloud()` |

## Strengths

- ✅ Clean API for cloud operations
- ✅ Handles Firestore serialization automatically
- ✅ Includes error handling with logging
- ✅ Supports username-based user identification

## Weaknesses

- ⚠️ Deprecated local storage methods still present
- ⚠️ No offline caching (requires network)
- ⚠️ No retry mechanism for failed saves

## Potential Improvements

1. **Add offline support** with Firestore persistence
2. **Remove deprecated methods** (`getLocalProgress`, `saveLocalProgress`)
3. **Add retry logic** for transient network failures
4. **Implement caching** to reduce Firestore reads
