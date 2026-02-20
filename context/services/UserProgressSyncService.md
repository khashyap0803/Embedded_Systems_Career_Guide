# UserProgressSyncService.kt — Hyper-Detailed Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `UserProgressSyncService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 578 |
| **Role** | Synchronizes user learning progress between local SharedPreferences and Firebase Firestore |

---

## 1. Why Does This File Exist?

The app tracks user progress (XP, completed stages, streaks, stars) in two places:
1. **Local** — `SharedPreferences` (fast, offline-capable)
2. **Cloud** — Firebase Firestore (persistent, cross-device)

This service keeps them in sync. It implements a **cloud-first** strategy: cloud data takes priority when merging, but local data is used as fallback when offline.

---

## 2. Imports (Lines 1–11)

```kotlin
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
```

- **SharedPreferences** — Local progress storage
- **Firebase Firestore** — Cloud progress storage
- **Coroutines** — Async execution on `Dispatchers.IO`

---

## 3. Class Structure (Lines 13–60)

```kotlin
class UserProgressSyncService(private val context: Context) {

    companion object {
        private const val TAG = "UserProgressSync"
        private const val PREFS_NAME = "user_progress"
        private const val PREFS_USER = "user_prefs"
        private const val KEY_USERNAME = "current_username"
```

### SharedPreferences Keys
| Key | Type | Purpose |
|---|---|---|
| `total_xp` | Int | Total experience points |
| `completed_stages` | Int | Number of completed stages |
| `current_streak` | Int | Current daily streak |
| `longest_streak` | Int | All-time best streak |
| `last_active_date` | String | Last date user was active |
| `stage_stars_{id}` | Int | Stars earned per stage |
| `stage_completed_{id}` | Boolean | Whether a stage is completed |

### Username Resolution
```kotlin
private fun getCurrentUsername(): String {
    val prefs = context.getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
    val username = prefs.getString(KEY_USERNAME, null)
    if (username.isNullOrBlank()) {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not logged in")
    }
    return username
}
```
Falls back to Firebase UID if username is not stored locally.

### Firestore Path
```kotlin
private fun getProgressDocRef(username: String) =
    firestore.collection("users").document(username)
        .collection("data").document("main")
        .collection("progress").document("current")
```
Path: `users/{username}/data/main/progress/current`

---

## 4. Save Progress to Cloud (Lines 80–140)

### `saveProgressToCloud()`
```kotlin
suspend fun saveProgressToCloud(): Boolean = withContext(Dispatchers.IO) {
    try {
        val localProgress = loadLocalProgress()
        val username = getCurrentUsername()
        getProgressDocRef(username).set(localProgress, SetOptions.merge()).await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save progress to cloud", e)
        false
    }
}
```

Reads local SharedPreferences data and pushes it to Firestore with `SetOptions.merge()` (preserves existing fields).

### `loadLocalProgress()` — Reads SharedPreferences

Builds a `Map<String, Any>` from all progress-related SharedPreferences keys:
```kotlin
mapOf(
    "totalXp" to prefs.getInt("total_xp", 0),
    "completedStages" to prefs.getInt("completed_stages", 0),
    "currentStreak" to prefs.getInt("current_streak", 0),
    ...
)
```

---

## 5. Load Progress from Cloud (Lines 140–220)

### `loadProgressFromCloud()`
```kotlin
suspend fun loadProgressFromCloud(): Boolean = withContext(Dispatchers.IO) {
    try {
        val username = getCurrentUsername()
        val doc = getProgressDocRef(username).get().await()

        if (doc.exists()) {
            applyCloudProgressLocally(doc.data ?: emptyMap())
            true
        } else {
            // First launch — save local to cloud
            saveProgressToCloud()
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load progress from cloud", e)
        false
    }
}
```

### `applyCloudProgressLocally()`
Writes cloud data into SharedPreferences:
```kotlin
prefs.edit().apply {
    putInt("total_xp", (data["totalXp"] as? Number)?.toInt() ?: 0)
    putInt("completed_stages", (data["completedStages"] as? Number)?.toInt() ?: 0)
    // ... etc
    apply()
}
```

---

## 6. Merge Strategy (Lines 220–320)

### `mergeProgress()`
```kotlin
suspend fun mergeProgress(): Boolean
```

The **cloud-first merge** algorithm:

```
┌─────────────────────┐     ┌─────────────────────┐
│ Local Progress      │     │ Cloud Progress       │
│ (SharedPreferences) │     │ (Firestore)          │
└────────┬────────────┘     └────────┬────────────┘
         │                           │
         └─────────┐   ┌────────────┘
                   ▼   ▼
            ┌──────────────────────┐
            │ Merge Rules:         │
            │ • XP = max(local,    │
            │     cloud)           │
            │ • Stages = max       │
            │ • Streak = cloud     │
            │     (authoritative)  │
            │ • Stars = max per    │
            │     stage            │
            └──────────────────────┘
                       │
                       ▼
            ┌──────────────────────┐
            │ Save merged to both  │
            │ local + cloud        │
            └──────────────────────┘
```

Key merge rules:
- **XP**: Takes the higher value (user can't lose XP)
- **Completed stages**: Takes the higher count
- **Streak**: Cloud is authoritative (prevents offline manipulation)
- **Stars per stage**: Takes the higher value per stage

---

## 7. Stage-Specific Operations (Lines 320–450)

### `completeStageInCloud()`
```kotlin
suspend fun completeStageInCloud(stageId: Int, stars: Int, xpEarned: Int): Boolean
```
Updates cloud data atomically:
- Marks `stage_completed_{stageId}` = true
- Sets `stage_stars_{stageId}` = stars
- Increments `totalXp` by `xpEarned`
- Increments `completedStages` count

### `updateStageStarsInCloud()`
```kotlin
suspend fun updateStageStarsInCloud(stageId: Int, stars: Int): Boolean
```
Updates only the star count for a specific stage (e.g., after a quiz retry).

### `resetStageProgressInCloud()`
```kotlin
suspend fun resetStageProgressInCloud(): Boolean
```
Resets all stage-specific progress in cloud (used when regenerating personalized stages):
- Sets `completedStages` = 0
- Removes all `stage_completed_{id}` and `stage_stars_{id}` entries

---

## 8. Streak Management (Lines 450–520)

### `updateStreak()`
```kotlin
suspend fun updateStreak(): Boolean
```
- Reads `last_active_date` from cloud
- If today ≠ last active date → increment streak
- If gap > 1 day → reset streak to 1
- Updates `longest_streak` if current exceeds it
- Saves updated values to both local and cloud

---

## 9. Legacy Method (Lines 520–578)

### `loadLocalProgressLegacy()` (Deprecated)
```kotlin
@Deprecated("Use loadLocalProgress() instead")
fun loadLocalProgressLegacy(): Map<String, Any>
```
Older version that reads from a different SharedPreferences file. Kept for backward compatibility during migration.

---

## 10. Connection Map

```
┌──────────────────┐        ┌───────────────────────────┐
│ Stage Complete   │───────▶│                           │
│ (Quiz passed)    │        │ UserProgressSyncService   │
├──────────────────┤        │                           │
│ App Launch       │───────▶│  ┌─────────┐ ┌─────────┐ │
│ (merge on start) │        │  │ Local   │ │ Cloud   │ │
├──────────────────┤        │  │ (Prefs) │◀│(Fire-   │ │
│ StageGenerator   │───────▶│  │         │▶│ store)  │ │
│ (reset progress) │        │  └─────────┘ └─────────┘ │
└──────────────────┘        └───────────────────────────┘
```

### Dependencies
| Dependency | Used For |
|---|---|
| `SharedPreferences` | Local progress storage |
| `Firebase Firestore` | Cloud progress persistence |
| `FirebaseAuth` | User ID fallback |
| `Dispatchers.IO` | Background execution |

### Used By
| Consumer | Operation |
|---|---|
| `StageGeneratorService` | `resetStageProgressInCloud()` |
| App launch flow | `mergeProgress()` |
| Quiz completion | `completeStageInCloud()` |
| Daily streak logic | `updateStreak()` |

---

## 11. Potential Issues & Notes

| Issue | Details |
|---|---|
| **No conflict resolution** | Merge uses simple max() — can't handle complex conflicts |
| **Streak manipulation** | Local streak can be manipulated by changing device date |
| **No batched write** | Multiple Firestore writes in `completeStageInCloud()` aren't atomic |
| **SharedPreferences limits** | Large number of stages = many SP entries; no cleanup |
| **Legacy method** | Deprecated `loadLocalProgressLegacy()` should be removed |
| **No offline queue** | If cloud write fails, no retry queue; data can be lost |

---

## 12. Summary

`UserProgressSyncService` is the **progress synchronization layer** between local `SharedPreferences` and Firebase Firestore. It implements a cloud-first merge strategy that preserves the highest values (XP, stars, stages) to prevent data loss. It provides stage-specific operations (complete, update stars, reset) and streak management, serving as the bridge between the app's offline-capable local storage and its persistent cloud backend.
