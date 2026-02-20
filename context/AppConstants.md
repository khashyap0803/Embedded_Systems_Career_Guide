# AppConstants.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/AppConstants.kt`  
> **Package**: `com.example.embeddedsystemscareerguide`  
> **Size**: 4,568 bytes (128 lines)

---

## What This File Does (Simple Explanation)

This file is the **central configuration hub** for the entire app. Instead of scattering "magic numbers" (like `16`, `500`, `80`) throughout the codebase, all important constant values and SharedPreferences keys are collected here in two singleton objects:

1. **`AppConstants`** — Numeric constants (stage count, XP values, timeouts, thresholds)
2. **`PrefsKeys`** — All SharedPreferences key strings and preference file names

Think of it as the app's "settings dictionary" — if you need to change the number of quiz questions from 10 to 15, you change it once here instead of hunting through 20 files.

---

## Why This File Exists

The comments at the top say "M2-M3 fix" which means this was created during a **maintenance/refactoring phase** to fix issues with hardcoded values scattered across the codebase. Before this file existed, the same numbers appeared in multiple places, leading to inconsistencies when one was updated but others were missed.

---

## Where This File Is Used

| File | Constants Used |
|------|---------------|
| `LearningPathFragment.kt` | `TOTAL_LEARNING_STAGES`, `STAR_*_THRESHOLD`, `DEFAULT_STAGE_XP`, `ANIM_DURATION_*` |
| `HomeFragment.kt` | `TOTAL_LEARNING_STAGES`, `XP_PER_LEVEL`, `PrefsKeys.CURRENT_USERNAME` |
| `GeminiQuizService.kt` | `QUESTIONS_PER_QUIZ`, `QUESTIONS_PER_API_BATCH` |
| `GeminiChatService.kt` | `MAX_CHAT_MESSAGES` |
| `GeminiReportService.kt` | `REPORT_CHUNK_SIZE`, `API_READ_TIMEOUT_LONG` |
| `NetworkModule.kt` | `API_CONNECT_TIMEOUT`, `API_READ_TIMEOUT`, `API_WRITE_TIMEOUT` |
| `LoginActivity.kt` | `PrefsKeys.CURRENT_USERNAME`, `PrefsKeys.USER_ID` |
| `UserProgressSyncService.kt` | `PrefsKeys.TOTAL_XP`, `PrefsKeys.COMPLETED_STAGES`, `PrefsKeys.STREAK` |
| `QuizActivity.kt` | `QUESTIONS_PER_QUIZ`, `STAR_*_THRESHOLD` |
| `ProfileFragment.kt` | `PrefsKeys.CURRENT_USERNAME`, `PrefsKeys.USER_EMAIL` |
| `SettingsFragment.kt` | `PrefsKeys.PREFS_USER`, `PrefsKeys.PREFS_APP` |

---

## Complete Code Walkthrough

### Lines 1-7: Package & Object Declaration
```kotlin
package com.example.embeddedsystemscareerguide

/**
 * M2-M3 fix: Centralized constants for the application
 * Consolidates magic numbers and SharedPreferences keys for maintainability
 */
object AppConstants {
```
`object` in Kotlin creates a **singleton** — there's only ever one instance of `AppConstants` in the entire app. You access values directly like `AppConstants.TOTAL_LEARNING_STAGES`.

### Lines 9-18: Learning Path Constants

| Constant | Value | Used For |
|----------|-------|----------|
| `TOTAL_LEARNING_STAGES` | `16` | Total stages in V1 learning path |
| `QUESTIONS_PER_QUIZ` | `10` | Questions shown per quiz session |
| `QUESTIONS_PER_API_BATCH` | `5` | Questions requested per Gemini API call (requests 2 batches of 5 = 10 total, to avoid token truncation) |
| `REPORT_CHUNK_SIZE` | `15` | How many questions to process at once during report generation |
| `MAX_CHAT_MESSAGES` | `100` | Maximum chat messages kept in memory before old ones are dropped |

### Lines 26-33: XP & Level Constants

| Constant | Value | Meaning |
|----------|-------|---------|
| `XP_PER_LEVEL` | `500` | Every 500 XP = 1 level (Level = totalXP / 500) |
| `DEFAULT_STAGE_XP` | `100` | Base XP award for completing a stage |

### Lines 34-43: Star Rating Thresholds

| Constant | Value | Meaning |
|----------|-------|---------|
| `STAR_1_THRESHOLD` | `40` | Score ≥ 40% → ⭐ (1 star) |
| `STAR_2_THRESHOLD` | `60` | Score ≥ 60% → ⭐⭐ (2 stars) |
| `STAR_3_THRESHOLD` | `80` | Score ≥ 80% → ⭐⭐⭐ (3 stars) |

> **Note**: The existing docs say star thresholds are 1-49% (1 star), 50-79% (2 stars), 80-100% (3 stars). But the code says 40%, 60%, 80%. The code is the source of truth.

### Lines 45-54: Animation Durations

| Constant | Value | Used For |
|----------|-------|----------|
| `ANIM_DURATION_SHORT` | `300ms` | Quick transitions, button presses |
| `ANIM_DURATION_MEDIUM` | `600ms` | Card animations, reveals |
| `ANIM_DURATION_LONG` | `1000ms` | Celebration animations, sparkle effects |

### Lines 56-68: API Timeouts

| Constant | Value | Used For |
|----------|-------|----------|
| `API_CONNECT_TIMEOUT` | `30s` | Maximum time to establish connection to Gemini API |
| `API_READ_TIMEOUT` | `60s` | Maximum time to wait for API response |
| `API_READ_TIMEOUT_LONG` | `300s` (5 min) | Extended timeout for report generation (Gemini processes 50 questions) |
| `API_WRITE_TIMEOUT` | `30s` | Maximum time to send request data |

### Lines 70-73: Debounce Delays

| Constant | Value | Used For |
|----------|-------|----------|
| `USERNAME_CHECK_DEBOUNCE_MS` | `500ms` | Delay before checking username availability (avoids spamming Firebase with every keystroke) |

---

### Lines 76-127: `PrefsKeys` Object

This second object centralizes all **SharedPreferences** key strings.

#### Preference File Names (Lines 84-87):

| Constant | Value | Stores |
|----------|-------|--------|
| `PREFS_USER` | `"user_prefs"` | User identity (username, email) |
| `PREFS_APP` | `"app_prefs"` | App settings (theme, notifications) |
| `PREFS_LEARNING` | `"learning_progress"` | Learning progress (legacy local storage) |
| `PREFS_SECURE` | `"secure_user_prefs"` | Encrypted sensitive data |

#### User Preference Keys (Lines 91-93):

| Constant | Value | Stores |
|----------|-------|--------|
| `CURRENT_USERNAME` | `"current_username"` | The logged-in user's username |
| `USER_ID` | `"user_id"` | Firebase Auth UID |
| `USER_EMAIL` | `"user_email"` | User's email address |

#### Learning Progress Keys (Lines 97-103):

| Constant | Value | Stores |
|----------|-------|--------|
| `TOTAL_XP` | `"home_total_xp"` | Cached total XP for quick HomeFragment display |
| `CURRENT_LEVEL` | `"home_current_level"` | Cached current level |
| `COMPLETED_STAGES` | `"home_completed_stages"` | Integer count of completed stages |
| `COMPLETED_STAGES_SET` | `"completed_stages"` | StringSet of completed stage IDs |
| `CURRENT_STAGE` | `"current_stage"` | Current stage number |
| `STREAK` | `"home_streak"` | Current study streak count |
| `LAST_ACTIVE_DATE` | `"last_visit_date"` | Date of last app visit (for streak calculation) |

#### Stage-Specific Key Prefixes (Lines 108-114):

| Constant | Example Key | Purpose |
|----------|-------------|---------|
| `STAGE_STARS_PREFIX` | `"stage_stars_5"` | Stars earned for stage 5 |
| `STAGE_COMPLETED_PREFIX` | `"stage_completed_5"` | Whether stage 5 is completed |
| `STAGE_UNLOCKED_PREFIX` | `"stage_unlocked_5"` | Whether stage 5 is unlocked |

#### Helper Functions (Lines 123-126):

```kotlin
fun stageStarsKey(stageId: Int) = "${STAGE_STARS_PREFIX}$stageId"
fun stageCompletedKey(stageId: Int) = "${STAGE_COMPLETED_PREFIX}$stageId"
fun stageUnlockedKey(stageId: Int) = "${STAGE_UNLOCKED_PREFIX}$stageId"
fun assessmentCompletedKey(userId: String) = "${ASSESSMENT_COMPLETED_PREFIX}$userId"
```

These helper functions generate the correct SharedPreferences key for a specific stage or user, e.g., `stageStarsKey(5)` returns `"stage_stars_5"`.

---

## Dependencies

**Zero imports** — both objects are pure Kotlin with no external dependencies.

---

## Strengths

- ✅ **Single source of truth** — all constants in one place
- ✅ **Well-documented** — every constant has a KDoc comment
- ✅ **Organized** — clear sections with separators
- ✅ **Helper functions** — prevent string concatenation errors

## Weaknesses / Technical Debt

- ⚠️ `TOTAL_LEARNING_STAGES = 16` is hardcoded — V2 plans 30-50 stages
- ⚠️ `PREFS_LEARNING` keys suggest legacy local storage is still partially used despite the cloud-only architecture
- ⚠️ `COMPLETED_STAGES` (Int) and `COMPLETED_STAGES_SET` (StringSet) are redundant — two different representations of the same data
- ⚠️ Star thresholds (40/60/80) don't match documentation (50/80) — needs sync
