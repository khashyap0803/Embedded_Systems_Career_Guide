# Practice, Profile, Quiz & Settings — UI Documentation

> **Packages:** `ui.practice`, `ui.profile`, `ui.quiz`, `ui.settings`  
> **Files:** 7 total — `QuizActivity.kt` (306 lines), `PracticeFragment.kt` (119 lines), `PracticeViewModel.kt` (8 lines), `ProfileFragment.kt` (88 lines), `ProfileViewModel.kt` (66 lines), `SettingsFragment.kt` (72 lines), `SettingsViewModel.kt` (13 lines)

---

## QuizActivity.kt (306 lines)

### Purpose
AI-generated quiz for learning path stages. Generates questions via `GeminiQuizService`, presents 4-option multiple-choice cards, tracks score, and returns results to `LearningPathFragment` via `ActivityResult`.

### Fields

| Field | Type | Role |
|---|---|---|
| `binding` | `ActivityQuizBinding` | View binding |
| `quizService` | `GeminiQuizService` | Generates quiz questions via Gemini |
| `questions` | `List<QuizQuestion>` | Generated quiz questions |
| `currentQuestionIndex` | `Int` | Current question position |
| `score` | `Int` | Correct answer count |
| `selectedAnswer` | `Int` | Current selection (-1 = none) |
| `hasAnswered` | `Boolean` | Whether current question is answered |
| `stageId` / `stageTitle` / `stageTopics` | `String` | Quiz context from intent extras |
| `answerCards` | `List<MaterialCardView>` | The 4 answer option cards |
| `answerTexts` | `List<TextView>` | The 4 answer text views |

### All Functions (11)

- **`onCreate(savedInstanceState)`** (lines 39–73) — Extracts stage extras, inits `GeminiQuizService`, sets toolbar title, registers `OnBackPressedCallback` (confirms exit with dialog), calls `setupClickListeners()` + `generateQuiz()`.
- **`setupClickListeners()`** (lines 75–94) — Sets click listeners on 4 answer cards → `selectAnswer(index)`. Sets "Next" button → `nextQuestion()`. Sets "Retry" button → `retryQuiz()`. Sets "Finish" button → `returnResult()`.
- **`generateQuiz()`** (lines 96–123) — Shows loading. Calls `quizService.generateQuizQuestions(stageTitle, stageTopics, 5)` in coroutine. On success: stores questions, calls `displayQuestion()`. On error: shows toast, finishes.
- **`displayQuestion()`** (lines 125–167) — Shows question layout, hides results. Updates question number, progress bar, question text. Populates 4 answer option texts. Resets card styles. Hides next button. Updates progress text.
- **`selectAnswer(selectedIndex)`** (lines 169–214) — If already answered, returns. Stores selection, sets `hasAnswered`. Highlights selected card. Checks correctness: correct → green stroke + increment score; wrong → red stroke + green on correct card. Shows next button. If last question → button text = "See Results".
- **`resetCardStyle(card)`** (lines 216–220) — Resets card stroke to default grey.
- **`nextQuestion()`** (lines 222–225) — Increments index, calls `displayQuestion()` or `showResults()`.
- **`showResults()`** (lines 227–279) — Hides question layout, shows results. Displays score, total, percentage. Shows emoji + message based on score (≥80% →🏆, ≥60% →👍, ≥40% →📚, else →💪). Makes retry/finish buttons visible.
- **`returnResult()`** (lines 281–287) — Sets `RESULT_OK` with `quiz_score`, `quiz_total`, `stage_id` extras. Finishes Activity.
- **`retryQuiz()`** (lines 289–297) — Resets `currentQuestionIndex`, `score`, `selectedAnswer`, `hasAnswered`. Calls `generateQuiz()` to regenerate fresh questions.
- **`showLoading(show)`** (lines 299–304) — Toggles loading overlay visibility.

---

## PracticeFragment.kt (119 lines)

### Purpose
Practice mode placeholder screen (Cloud-only). Loads user progress from Firestore and displays 4 practice options (all currently "Coming Soon").

### Fields
- `binding` — `FragmentPracticeBinding`
- `viewModel` — `PracticeViewModel` (empty stub)
- `auth` — `FirebaseAuth`
- `progressSyncService` — `UserProgressSyncService`

### All Functions (7)

- **`onCreateView(inflater, container, savedInstanceState)`** — Inits ViewModel, binding, Firebase Auth, `UserProgressSyncService`.
- **`onViewCreated(view, savedInstanceState)`** — Calls `setupUI()`, `setupPracticeOptions()`, `loadUserProgressFromCloud()`.
- **`setupUI()`** — Gets username from SharedPreferences (login session). Displays welcome message with name.
- **`setupPracticeOptions()`** — Sets card click listeners for Quick Practice, Topic Practice, Challenge Mode, Review Mistakes — all route to `showComingSoonToast()`.
- **`showComingSoonToast(featureName)`** — Displays "🚧 {name} coming soon!" toast.
- **`loadUserProgressFromCloud()`** — Loads `UserProgress` from Firestore. Displays total XP and completed stages count. Falls back to "0 XP" / "0 Stages" on error.
- **`onDestroyView()`** — Nulls binding.

### PracticeViewModel.kt (8 lines)
Empty ViewModel stub. No LiveData, no functions (TODO placeholder).

---

## ProfileFragment.kt (88 lines)

### Purpose
User profile screen displaying email, username, and a logout button. Clears all local data on logout.

### Fields
- `binding` — `FragmentProfileBinding`

### All Functions (5)

- **`onCreateView(inflater, container, savedInstanceState)`** — Inflates binding.
- **`onViewCreated(view, savedInstanceState)`** — Calls `setupUserInfo()`. Finds logout button by ID, sets click → `performLogout()`.
- **`setupUserInfo()`** — Gets current Firebase user. Loads username from `user_prefs` SharedPreferences. Displays email and username (fallback chain: username → displayName → "User").
- **`performLogout()`** — Clears `UserProgressSyncService` local progress, clears `user_prefs`, signs out Firebase Auth, navigates to `LoginActivity` with `CLEAR_TASK` flags and finishes activity. On exception: still navigates to login.
- **`onDestroyView()`** — Nulls binding.

### ProfileViewModel.kt (66 lines)
ViewModel with LiveData for user stats, assessment progress, and learning progress.

**LiveData:**

| LiveData | Type | Description |
|---|---|---|
| `userStats` | `UserStats` | XP, level, streak, completion % |
| `assessmentProgress` | `AssessmentProgress` | Initial/final assessment flags + scores |
| `learningProgress` | `LearningProgress` | Stages completed, total, current stage name |

**Functions:**
- `loadUserProfile()` — Sets default values (TODO: Firebase integration).

**Data Classes:**
- `UserStats(totalXP, currentLevel, dailyStreak, completionPercentage)`
- `AssessmentProgress(initialAssessmentCompleted, initialAssessmentScore, finalAssessmentCompleted, finalAssessmentScore)`
- `LearningProgress(stagesCompleted, totalStages, currentStage)`

---

## SettingsFragment.kt (72 lines)

### Purpose
App settings screen with a logout button. Nearly identical logout flow to `ProfileFragment`.

### Fields
- `binding` — `FragmentSettingsBinding`

### All Functions (4)

- **`onCreateView(inflater, container, savedInstanceState)`** — Inflates binding.
- **`onViewCreated(view, savedInstanceState)`** — Finds logout button by ID (`button_logout`), sets click → `performLogout()`.
- **`performLogout()`** — Same as `ProfileFragment.performLogout()`: clears `UserProgressSyncService`, clears `user_prefs`, Firebase sign out, navigates to `LoginActivity`, finishes.
- **`onDestroyView()`** — Nulls binding.

### SettingsViewModel.kt (13 lines)
Minimal ViewModel stub. Holds a single `MutableLiveData<String>` initialised to `"This is settings Fragment"`. No functions.

---

## Design Notes

- **Quiz retry:** Generates completely new questions each retry (not the same set reshuffled).
- **Practice is placeholder:** All 4 practice options show "Coming Soon" — users are directed to Learning Path quizzes for practice.
- **Duplicate logout code:** `ProfileFragment` and `SettingsFragment` share nearly identical `performLogout()` implementations. A potential refactor candidate.
- **Cloud-only pattern:** PracticeFragment follows the same cloud-only progress loading pattern as LearningPathFragment.
