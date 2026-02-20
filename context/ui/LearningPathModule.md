# Learning Path Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.learningpath`  
> **Files:** 10 total — `LearningPathFragment.kt` (1098 lines), `LearningPathViewModel.kt` (46 lines), `LearningPathAdapter.kt` (189 lines), `GameifiedStagesAdapter.kt` (103 lines), `GamePathItemDecoration.kt` (56 lines), `CircularProgressView.kt`, `GamePathView.kt`, `ParticleAnimationView.kt`, `PathView.kt`, `SparkleView.kt`

---

## LearningPathFragment.kt

### Purpose
Main gamified learning journey screen. Displays a vertical scrollable game path of 16 AI-personalized learning stages with star ratings, XP rewards, streak tracking, and cloud-synced progress via Firestore. Stages are loaded from Firestore (personalized by assessment report) — **no hardcoded fallback**.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `LearningPathFragment` | `Fragment` | Bottom-nav hosted learning path |
| `binding` | `FragmentLearningPathBinding` | View binding |
| `viewModel` | `LearningPathViewModel` | LiveData holder |
| `stages` | `MutableList<LearningStage>` | All loaded learning stages |
| `prefs` | `SharedPreferences` | `PREFS_LEARNING` storage |
| `progressSyncService` | `UserProgressSyncService` | Cloud sync |
| `cloudProgress` | `UserProgress?` | Current cloud state |
| `stageCompletionInProgress` | `Boolean` | Prevents `onResume` from overwriting optimistic UI |
| `isLoadingStages` | `Boolean` | Prevents duplicate Firestore stage loads |
| `currentQuizStage` | `LearningStage?` | Stage being quizzed |
| `quizLauncher` | `ActivityResultLauncher` | Handles quiz result (score, stars, completion) |

### Companion Constants
All delegated to `PrefsKeys`: `PREFS_LEARNING`, `TOTAL_XP`, `CURRENT_STAGE`, `STREAK`, `LAST_ACTIVE_DATE`, `STAGE_STARS_PREFIX`, plus local `KEY_COMPLETED_STAGES` (StringSet) and `KEY_FIRST_LAUNCH`.

### All Functions

#### Lifecycle

- **`onCreateView(inflater, container, savedInstanceState)`** (lines 78–89) — Inflates binding, initialises ViewModel and SharedPreferences, creates `UserProgressSyncService`.
- **`onViewCreated(view, savedInstanceState)`** (lines 91–99) — Calls `loadStagesFromFirestore()` + `startBackgroundAnimation()`.
- **`onResume()`** (lines 101–126) — Skips if `stageCompletionInProgress` or `isLoadingStages`. If stages empty → `loadStagesFromFirestore()`, else → `loadProgressFromCloud()`.
- **`onDestroyView()`** (lines 1093–1096) — Nulls binding.

#### Stage Loading

- **`loadStagesFromFirestore()`** (lines 136–221) — Guards against duplicate loads. Checks `FirestoreManager.hasPersonalizedStages()`:
  - Found → converts `PersonalizedStage` list to `LearningStage` list, calls `loadProgressFromCloud()`.
  - Not found → checks `hasAssessmentReport()` → `regenerateStagesFromReport()` or `redirectToAssessment()`.
  - On error: same fallback chain.

- **`regenerateStagesFromReport(firestoreManager)`** (lines 226–290) — Gets report data, converts to `AssessmentResult` via `convertReportToAssessmentResult()`, calls `StageGeneratorService.generatePersonalizedStages()` with progress/success/error callbacks. On success: resets `isLoadingStages`, reloads from Firestore.

- **`convertReportToAssessmentResult(reportData)`** (lines 295–341) — Extracts `topicScores` map from report. Falls back to 5 default topics (Microcontrollers, GPIO, etc.) using `totalScore` if no per-topic data exists. Returns `AssessmentResult(totalScore, maxScore, topicScores, timestamp)`.

- **`redirectToAssessment()`** (lines 346–355) — Shows toast, launches `AssessmentActivity` with `CLEAR_TASK` flags.

#### Progress Management

- **`loadProgressFromCloud()`** (lines 373–404) — Loads `UserProgress` from cloud. On success: stores in `cloudProgress`, calls `applyProgressToStages()`, `updateStreakSystem()`, `createGamePath()`, `updateHomePageProgress()`. On error: uses empty defaults, shows toast.

- **`applyProgressToStages(progress)`** (lines 409–437) — For each stage: sets `isCompleted` from `completedStages`, enforces **strict sequential unlocking** (stage N unlocked only if stage N-1 completed, except stage 1), loads `starsEarned` from cloud. Calls `updateUserStats()`.

- **`initializeProgressForNewUsers()`** (lines 445–461) — On first launch: sets `is_first_launch = false`, writes zero defaults to home display prefs.

- **`updateStreakSystem()`** (lines 469–530) — Calculates streak from `cloudProgress.lastVisitDate`:
  - Same day → keep streak.
  - Yesterday → increment streak.
  - Older → reset to 1.
  - Updates `bestStreak`, saves to cloud. Shows milestone toasts at 7, 14, 30 days and every 10th day.

- **`isYesterday(dateString): Boolean`** (lines 532–544) — Parses `yyyy-MM-dd` string, compares with yesterday's date.

#### UI Rendering

- **`createGamePath()`** (lines 552–578) — Clears container, sorts stages by order **descending** (16→1 for bottom-to-top display). Creates stage nodes with staggered entrance animations (fade in + translate). Auto-scrolls to bottom (stage 1).

- **`createStageNode(stage, isFirstStage): View`** (lines 580–656) — Inflates `item_stage_node`. Configures:
  - **Locked:** grey, 50% alpha, lock overlay visible, 4dp elevation.
  - **Completed:** green stroke, full alpha, 16dp elevation, stars + sparkles visible.
  - **Available:** indigo stroke, 12dp elevation, extra stroke width for current stage.
  - Hides connection lines at top/bottom edges. Sets click → `onStageClicked()`.

- **`onStageClicked(stage)`** (lines 658–673) — Locked → toast. Completed → `showStageOptionsDialog()`. Available → `showNewStageOptionsDialog()`.

- **`showNewStageOptionsDialog(stage)`** (lines 679–691) — Dialog: "Read Content" → `launchContentReading()`, "Take Quiz" → `launchQuizForStage()`.

- **`showStageOptionsDialog(stage)`** (lines 696–708) — Dialog for completed stages: "Review Content" or "Retake Quiz".

- **`showProgressDetailsDialog(stage)`** (lines 941–963) — Shows stage stats: stars, XP, duration, topics. Offers "Retake Quiz".

- **`showHelpDialog()`** (lines 985–989) — Toast with learning path instructions.

- **`updateUserStats(totalXP, currentStage, streak)`** (lines 805–825) — Updates stat text views, calculates completion percentage from `SharedPreferences` completed stages set, updates progress bar. Calls `updateHomePageProgress()`.

- **`showStars(starsContainer, starsEarned)`** (lines 1011–1020) — Sets star icons: filled for earned, outline for remaining.

- **`startBackgroundAnimation()`** (lines 1022–1031) — Infinite horizontal pan animation (±50px over 10s) on background view.

#### Navigation/Launch

- **`launchContentReading(stage)`** (lines 713–726) — Launches `ContentReadingActivity` with stage ID, title, and topics.

- **`launchQuizForStage(stage)`** (lines 731–749) — Launches `QuizActivity` via `quizLauncher` with stage data. Stores `currentQuizStage`.

- **`quizLauncher` (ActivityResultLauncher)** (lines 753–803) — On `RESULT_OK`: calculates stars from score (≥80%→3⭐, ≥60%→2⭐, ≥40%→1⭐). Routes to:
  - First completion → `completeStage()`.
  - Improvement → `updateStageStars()`.
  - Same/worse → encouragement toast.
  - Failed (0 stars) → study suggestion toast.

#### Stage Completion

- **`completeStage(stageId, starsEarned)`** (lines 831–898) — Sets `stageCompletionInProgress`. Optimistic UI: marks stage completed, unlocks next stage, rebuilds path. Cloud: calls `progressSyncService.completeStageInCloud()`. On cloud confirm: updates stats and path again. Clears flag in `finally`.

- **`updateStageStars(stageId, newStars, oldStars)`** (lines 904–936) — Only if `newStars > oldStars`. Optimistic UI update, cloud save via `progressSyncService.updateStarsInCloud()`. Shows improvement toast.

#### Progress Sync

- **`updateHomePageProgress()`** (lines 969–983) — Writes cloud progress metrics to SharedPreferences for home page consumption.

- **`syncProgressToCloud()`** (lines 1079–1091) — Saves entire `cloudProgress` to cloud.

#### Helpers

- **`getColorForDifficulty(difficulty): String`** (lines 360–367) — `"beginner"→#10B981`, `"intermediate"→#3B82F6`, `"advanced"→#EF4444`, default→`#818CF8`.
- **`getIconResourceId(iconName): Int`** (lines 991–1009) — Maps icon name strings to drawable resource IDs.
- **`getIconResourceForStage(stageId): Int`** (lines 1036–1056) — Maps stage number (1–16) to drawable resource IDs.
- **`getStageType(stageId): String`** (lines 1061–1073) — Maps stage ranges to type strings (`"foundation"`, `"microcontroller"`, etc.).

---

## LearningPathViewModel.kt (46 lines)

Simple ViewModel holding `LiveData` for UI state.

| LiveData | Type | Default |
|---|---|---|
| `currentStage` | `Int` | 3 |
| `userXP` | `Int` | 1250 |
| `userStreak` | `Int` | 15 |
| `userLevel` | `Int` | 8 |

**Functions:**
- `completeStage(stageId: Int)` — Stub (logic handled in Fragment).
- `updateUserStats(xp, streak, level)` — Updates all LiveData.
- `loadLearningStages(onResult)` — Stub, returns empty list.

---

## LearningPathAdapter.kt (189 lines)

RecyclerView adapter for the game path with `ViewStageNodeBinding`.

### Data Classes
- **`GameStage`** — `id`, `title`, `subtitle`, `iconRes`, `isUnlocked`, `progress`, `type: StageType`, `xpReward`, `description`, `isCompleted` (derived: `progress == 100`).
- **`StageType` (enum)** — `FOUNDATION`, `HARDWARE`, `PROGRAMMING`, `COMMUNICATION`, `SYSTEM`, `IOT`, `PROJECT`, `CAREER`.

### Functions
- `onCreateViewHolder`, `onBindViewHolder`, `getItemCount` — Standard adapter methods.

### StageViewHolder Functions
- `bind(stage, position)` — Delegates to sub-functions.
- `setupStageContent(stage)` — Sets title, subtitle, XP, icon.
- `configureStageState(stage)` — Colors: emerald (completed), indigo (available), slate (locked). Shows/hides lock and completion icons.
- `setupClickListener(stage)` — Touch scale animation (0.95×→1.0×), calls `onStageClick`.
- `startContinuousAnimations(stage)` — Routes to glow (completed) or pulse (in-progress).
- `startCompletionGlow()` — Infinite alpha oscillation (1→0.7→1, 2s).
- `startProgressPulse()` — Infinite scale oscillation (1→1.1→1, 1.5s) on icon.
- `setPosition(x, y)` — Custom margin positioning for map layout.
- `animateEntrance(delay)` — Scale+fade entrance with `OvershootInterpolator(1.2)`.

---

## GameifiedStagesAdapter.kt (103 lines)

Alternative RecyclerView adapter using `item_learning_stage` layout and `LearningStage` model.

### StageViewHolder
Holds: `stageCard`, `stageIcon`, `stageTitle`, `stageDescription`, `stageProgress`, `progressText`, `lockIcon`, `completionIcon`.

### Functions
- `onCreateViewHolder`, `onBindViewHolder`, `getItemCount`.
- `onBindViewHolder` logic: Locked (slate, 60% alpha, "🔒 Locked"), Completed (emerald, "✅ Complete", 100% progress), Available (indigo, shows actual progress %). Background colour varies by `stage.type` — 8 type colours.

---

## GamePathItemDecoration.kt (56 lines)

`RecyclerView.ItemDecoration` that draws curved dashed connecting lines between stage nodes.

- **`completedPaint`** / **`incompletePaint`** — Two `Paint` styles: green (completed) and grey (incomplete), both anti-aliased with 3dp stroke and `DashPathEffect(10, 5)`.
- **`onDraw(canvas, parent, state)`** — Iterates child pairs, draws a Bézier curve (`cubicTo`) between each pair's bottom and the next's top. Uses dots along the path. Colour depends on the stage's completed status.

---

## Custom Views (5 files)

### CircularProgressView
Custom `View` drawing a circular progress ring with percentage text. Uses `Canvas.drawArc()` for the progress arc and `Paint.setShader()` for gradient effects.

### GamePathView
Custom `View` rendering the visual game path background — a winding road/path graphic drawn with bezier curves connecting stage positions.

### ParticleAnimationView
Custom `View` rendering floating animated particles in the background. Uses a `Handler` loop to update particle positions and redraw. Each particle has random velocity, size, and alpha.

### PathView
Custom `View` drawing path segments between stage nodes. Simpler than `GamePathView`, used for straight connecting lines.

### SparkleView
Custom `View` rendering sparkle/star animations around completed stages. Uses `ObjectAnimator` for scale and alpha effects on small star drawables.

---

## Design Decisions

- **Cloud-only progress:** No local SharedPreferences fallback for progress data. Firestore is the single source of truth. Home page prefs are only for display caching.
- **Strict sequential unlocking:** Only stage 1 is unlocked by default. Each subsequent stage requires the previous stage to be completed.
- **Optimistic UI updates:** Stage completion updates the UI immediately before the cloud save completes, with the `stageCompletionInProgress` flag preventing `onResume` from reverting the change.
- **No hardcoded stages:** If no personalized stages exist in Firestore, the app regenerates them from the assessment report or redirects to the assessment. The old 16-stage fallback has been removed.
- **Star improvement system:** Retaking a quiz only updates stars if the new score is higher — no duplicate XP awards.
- **Bottom-to-top rendering:** Stages are sorted descending (16→1) so the earliest stages appear at the bottom, matching a "climbing" metaphor.
