# Home Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.home`  
> **Files:** `HomeFragment.kt` (447 lines), `HomeViewModel.kt` (41 lines)

---

## HomeFragment.kt

### Purpose
Main dashboard fragment displaying the user's welcome message, daily inspiration, progress statistics (XP, streak, level, completion), quick action cards, and assessment status. Data is loaded cloud-first from Firestore.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `HomeFragment` | `Fragment` | Bottom-nav hosted dashboard |
| `binding` | `FragmentHomeBinding` | View binding |
| `homeViewModel` | `HomeViewModel` | Holds `LiveData` for UI state |
| `firestore` | `FirebaseFirestore` | Reads user progress |
| `auth` | `FirebaseAuth` | Gets current user |

### All Functions

#### `onCreateView(inflater, container, savedInstanceState)` (lines ~35–44)
- Inflates `FragmentHomeBinding`, initialises `HomeViewModel` via `ViewModelProvider`.

#### `onViewCreated(view, savedInstanceState)` (lines ~46–60)
- Calls `setupSwipeRefresh()`, `setupUserWelcome()`, `loadProgressFromCloud()`, `setupQuickActions()`, `checkAssessmentStatusFromCloud()`.

#### `onResume()` (lines ~62–66)
- Refreshes data by calling `loadProgressFromCloud()` and `checkAssessmentStatusFromCloud()`.

#### `setupSwipeRefresh()` (lines ~68–76)
- Configures `SwipeRefreshLayout` colour scheme (indigo).
- On refresh: reloads progress, checks assessment status, stops the spinner after a 1.5s delay.

#### `loadProgressFromCloud()` (lines ~78–130)
- Creates `UserProgressSyncService`.
- Reads progress from Firestore using `loadProgressFromCloud()`.
- Extracts `totalXP`, `currentStage`, `streak`, `completionPercentage` from the cloud data.
- Calls `homeViewModel.updateUserProgress()` and `updateProgressDashboard()`.
- Calls `updateStudyStreak()` and `updateStreakVisualIndicators()`.
- On failure: logs error, uses defaults (0s).

#### `setupUserWelcome()` (lines ~132–148)
- Gets username from SharedPreferences `"current_username"`.
- Sets welcome text: `"Welcome back, @{username}! 👋"`.
- Uses `Dispatchers.Main` for Firebase display name fallback.
- Sets a randomised daily insight/quote.

#### `updateProgressDashboard(progress: Map<String, Any>?)` (lines ~150–184)
- Updates four stat cards from progress data:
  - **Total XP** — with animated counter and suffix `" XP"`.
  - **Current Stage** — with formatter `"Stage {n}"`.
  - **Streak** — with suffix `" days"`.
  - **Completion** — with animated progress bar and suffix `"%"`.
- Calls `startAnimations()`.

#### `setupQuickActions()` (lines ~186–230)
- Configures five quick action cards:
  - **Learning Path** → navigates to Learning Path fragment.
  - **Assessment** → launches `AssessmentActivity`.
  - **Practice** → navigates to Practice fragment.
  - **Profile** → navigates to Profile fragment.
  - **AI Tutor** → navigates to Chat fragment.

#### `checkAssessmentStatusFromCloud()` (lines ~232–270)
- Gets username from SharedPreferences.
- Queries Firestore `users/{username}/data/report`.
- If report exists: calls `showAssessmentOptions(hasReport = true)`.
- If not: calls `showAssessmentOptions(hasReport = false)`.
- Uses the legacy `assessment_reports/{uid}` path as fallback.

#### `showAssessmentOptions(hasReport: Boolean)` (lines ~272–300)
- If `hasReport`:
  - Shows "View Report" button → launches `ReportViewerActivity`.
  - Shows "Retake Assessment" button → calls `showRetakeConfirmationDialog()`.
- If not:
  - Shows "Take Assessment" button → launches `AssessmentActivity`.

#### `showRetakeConfirmationDialog()` (lines ~302–320)
- Shows a `MaterialAlertDialogBuilder` confirmation dialog.
- Warns user that retaking will generate a new report and reset learning stages.
- On confirm: launches `AssessmentActivity` with `is_retake = true` extra.

#### `updateStudyStreak(streak: Int)` (lines ~322–340)
- Updates the streak counter text.
- Displays streak-related motivational message based on streak value:
  - 0 days: "Start your streak today!"
  - 1–6 days: "Keep going!"
  - 7+ days: "Amazing streak! 🔥"

#### `setupAchievements()` (lines ~342–358)
- Placeholder / minimal implementation for future achievement badges.

#### `updateStreakVisualIndicators(streak: Int)` (lines ~360–378)
- Updates streak-related visual elements:
  - Flame emoji visibility (shows 🔥 for streaks ≥ 3).
  - Streak badge colour (green for active, grey for inactive).

#### `startAnimations()` (lines ~380–396)
- Triggers entrance animations for stat cards using scale and fade-in effects with staggered delays.

#### `animateCounter(textView: TextView, targetValue: Int, suffix: String, duration: Long, formatter: ((Int) -> String)?)` (lines ~398–420)
- Uses `ValueAnimator` to count from 0 to `targetValue` over `duration` ms.
- Applies optional `formatter` (e.g., `"Stage {n}"`) or appends `suffix`.
- Default duration: 1200ms.

#### `animateProgressBar(progressBar: ProgressBar, targetProgress: Int, duration: Long)` (lines ~422–434)
- Uses `ObjectAnimator` on the `"progress"` property.
- Animates from 0 to `targetProgress` over `duration` ms.
- Default duration: 1500ms.

#### `onDestroyView()` (lines ~436–440)
- Nulls out `_binding` to prevent memory leaks.

---

## HomeViewModel.kt

### Purpose
Simple `ViewModel` holding `LiveData` for the dashboard statistics. Acts as a data bridge between the fragment and the UI.

### LiveData Properties

| LiveData | Type | Default | Exposed As |
|---|---|---|---|
| `_text` | `MutableLiveData<String>` | `"Welcome to your Embedded Systems Journey!"` | `text` |
| `_totalXP` | `MutableLiveData<Int>` | `1250` | `totalXP` |
| `_currentStage` | `MutableLiveData<Int>` | `5` | `currentStage` |
| `_streakDays` | `MutableLiveData<Int>` | `15` | `streakDays` |
| `_completionPercentage` | `MutableLiveData<Int>` | `33` | `completionPercentage` |

### Function

#### `updateUserProgress(xp: Int, stage: Int, streak: Int, completion: Int)` (lines 34–39)
- Updates all four progress `LiveData` values at once.
- Called by `HomeFragment.loadProgressFromCloud()` after Firestore data is received.

### Design Decisions
- **Cloud-first data loading:** Progress is always read from Firestore, not SharedPreferences. This ensures multi-device consistency.
- **Animated counters:** `ValueAnimator`-based counters create visual interest and draw attention to stats.
- **Retake flow:** Users must confirm before retaking the assessment, since it replaces the existing report and regenerates learning stages.
- **SwipeRefreshLayout:** Pull-to-refresh pattern ensures users can manually sync their data.
- **ViewModel defaults:** The defaults (1250 XP, Stage 5, etc.) are placeholder values that are immediately overwritten by cloud data in `loadProgressFromCloud()`.
