# Assessment Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.assessment`  
> **Files:** `AssessmentActivity.kt` (420 lines), `ReportViewerActivity.kt` (353 lines)

---

## AssessmentActivity.kt

### Purpose
Guides users through a multi-question embedded-systems assessment. Questions are loaded from a local JSON asset, answers are collected (typed or via speech-to-text), and the complete set is submitted to **GeminiReportService** which generates an HTML report stored in Firestore. After the report is saved, personalised learning stages are generated.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `AssessmentActivity` | `AppCompatActivity` | Hosts the entire assessment flow |
| `binding` | `ActivityAssessmentBinding` | View binding for the layout |
| `questions` | `List<Question>` | All questions parsed from `assets/initial_assessment_questions.json` |
| `currentQuestionIndex` | `Int` | Tracks which question is active |
| `answers` | `MutableMap<String, String>` | Maps question ID → user answer |
| `auth` | `FirebaseAuth` | Firebase Authentication instance |
| `firestore` | `FirebaseFirestore` | Firestore database instance |
| `geminiService` | `GeminiReportService` | Submits answers & retrieves the AI report |
| `isRetake` | `Boolean` | Whether this is a retake assessment (from intent extra `"is_retake"`) |
| `speechRecognizerLauncher` | `ActivityResultLauncher` | Handles speech-to-text result |

### Lifecycle Flow

```
onCreate(savedInstanceState)
  ├─ Inflate binding, setContentView
  ├─ Read isRetake from intent extra
  ├─ Register OnBackPressedCallback → handleOnBackPressed()
  ├─ loadQuestions()
  ├─ setupUI()
  └─ displayCurrentQuestion()
```

### All Functions

#### `onCreate(savedInstanceState: Bundle?)` (lines 55–75)
- Inflates the layout via `ActivityAssessmentBinding`.
- Checks `intent.getBooleanExtra("is_retake", false)` to determine if this is a retake.
- Registers a back-press callback that saves the current answer before finishing.
- Calls `loadQuestions()`, `setupUI()`, and `displayCurrentQuestion()`.

#### `handleOnBackPressed()` (lines 66–69, nested inside `onCreate`)
- Saves the current answer via `saveCurrentAnswer()`.
- Calls `finish()` to close the activity.

#### `loadQuestions()` (lines 77–88)
- Opens `initial_assessment_questions.json` from the assets folder.
- Uses `Gson` with `TypeToken<List<Question>>` to deserialize the JSON.
- On exception: logs the error, shows a toast, and calls `finish()`.

#### `setupUI()` (lines 90–113)
- Sets up three click listeners:
  - **Back button:** Saves current answer, decrements `currentQuestionIndex`, displays previous question.
  - **Next button:** Saves current answer, increments index. If at the last question, calls `submitAssessment()`.
  - **Mic button:** Calls `startVoiceInput()`.

#### `displayCurrentQuestion()` (lines 115–142)
- Guards: returns if `questions` is empty.
- Updates the `progressIndicator` (percentage: `(index+1) * 100 / total`).
- Updates the question counter text (`"X of Y"`).
- Updates the question text.
- Pre-fills the answer `EditText` if the user has already answered this question.
- Hides the Back button on the first question.
- Changes Next button text to `"Submit"` on the last question.

#### `saveCurrentAnswer()` (lines 144–150)
- Captures the text in the answer `EditText`, trims it, and stores it by question ID in `answers`.

#### `startVoiceInput()` (lines 152–169)
- Checks `SpeechRecognizer.isRecognitionAvailable()` — if not, shows a toast.
- Creates an `Intent` for `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `LANGUAGE_MODEL_FREE_FORM`.
- Launches via `speechRecognizerLauncher`; on exception shows a fallback toast.

#### `submitAssessment()` (lines 171–310)
- **Guard:** Prevents duplicate submissions by checking if `loadingOverlay` is already visible.
- Shows a full-screen loading overlay with:
  - Phase counter (`"Phase 0 of 6"`), progress bar, status text, and a random motivational quote from `GeminiReportService.QUOTES`.
  - Disables Next/Back buttons.
- Launches a coroutine (`lifecycleScope.launch`):
  1. Builds `qaList` — a `List<QuestionAnswer>` from `questions` and `answers`.
  2. Gets `userId`, `userEmail`, `userName` (from SharedPreferences username, falling back to Firebase display name or `"Student"`).
  3. Creates a `ProgressCallback` that updates `phaseCounter`, `phaseProgressBar`, `progressText`, `quoteText`, and `progressSubstatus` on each phase.
  4. Calls `geminiService.generateReport(userName, userEmail, qaList, progressCallback)`.
  5. On success: calls `saveReportToFirebaseSync(reportHtml, userId, userName, userEmail)`.
  6. If save succeeds: invokes `StageGeneratorService` to generate personalised learning stages:
     - Calculates score via `calculateAssessmentScore()`.
     - Uses a `GenerationCallback` with `onProgress`, `onSuccess`, `onError`.
     - **Retake path:** Calls `stageGenerator.regenerateStages()` (considers history).
     - **First-time path:** Calls `stageGenerator.generatePersonalizedStages()`.
     - Both callbacks call `showCompletionState()` on completion.
  7. On failure: hides loading overlay, re-enables buttons, shows error toast.

##### Nested callbacks inside `submitAssessment`:

- **`ProgressCallback.onProgress(phase, totalPhases, phaseName, quote)`** (lines 218–231): Updates UI phase counter, progress bar, status text, quote, and substatus based on current phase.
- **`GenerationCallback.onProgress(phase, message)`** (lines 261–265): Updates substatus text on UI thread.
- **`GenerationCallback.onSuccess(stages)`** (lines 267–272): Logs stage count, calls `showCompletionState()`.
- **`GenerationCallback.onError(error)`** (lines 274–282): Logs error, shows toast about delayed learning path, still calls `showCompletionState()` since the report itself was saved.

#### `showCompletionState()` (lines 312–330)
- Hides the loading spinner, shows the completion UI.
- Sets up two buttons:
  - **"Preview Report"** → launches `ReportViewerActivity`.
  - **"Continue to Home"** → calls `navigateToHome()`.

#### `navigateToHome()` (lines 332–341)
- Creates an `Intent` for `MainActivity` with flags `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK`.
- Starts the activity and calls `finish()`.

#### `saveReportToFirebaseSync(htmlContent, userId, userName, userEmail): Boolean` (lines 343–387)
- **Suspend function** — called within a coroutine.
- Gets username from SharedPreferences; returns `false` if not found.
- Builds a `reportData` map: `userId`, `userName`, `userEmail`, `reportHtml`, `timestamp`, `totalQuestions`.
- Saves to Firestore path: `users/{username}/data/report` using `set()` (replaces any existing report).
- Uses `await()` for synchronous completion.
- Returns `true` on success, `false` on exception.

#### `calculateAssessmentScore(): Int` (lines 389–415)
- Returns a percentage score (0–100) based on answer word counts (heuristic for effort).
- Scoring per question:

| Word Count | Score |
|---|---|
| ≥ 50 | 100 (detailed) |
| ≥ 30 | 80 (good) |
| ≥ 15 | 60 (adequate) |
| ≥ 5 | 40 (brief) |
| > 0 | 20 (minimal) |
| 0 | 0 (no answer) |

- Returns the average across all questions.
- Defaults to 50 if `questions` is empty.

### Design Decisions
- **Local questions + cloud report:** Questions are static (ship with APK) so the assessment works offline until submission. Report generation requires network (Gemini AI).
- **Speech-to-text:** Allows voice-based answers for accessibility / convenience.
- **Retake vs first-time:** Uses `isRetake` flag to call different stage-generation methods. `regenerateStages` considers the user's history; `generatePersonalizedStages` starts fresh.
- **Synchronous Firebase save:** Uses `await()` to ensure the report is persisted before generating learning stages, preventing a race condition.
- **Removed `markAssessmentCompleted`:** Report existence in Firebase is the source of truth — no separate flag needed.

---

## ReportViewerActivity.kt

### Purpose
Displays the Gemini-generated assessment report (HTML) inside a **WebView** and allows the user to download it as an HTML file.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `ReportViewerActivity` | `AppCompatActivity` | Hosts the WebView and download functionality |
| `binding` | `ActivityReportViewerBinding` | View binding |
| `firestore` | `FirebaseFirestore` | Reads report from Firestore |
| `auth` | `FirebaseAuth` | Gets current user |
| `currentReportHtml` | `String?` | The raw HTML loaded from Firestore (cached for download) |
| `PERMISSION_REQUEST_CODE` | `Int` (companion, 100) | Request code for storage permission |

### All Functions

#### `onCreate(savedInstanceState: Bundle?)` (lines 37–52)
- Inflates binding, sets content view.
- Sets up the toolbar with back navigation and title `"Assessment Report"`.
- Calls `setupWebView()` and `loadReportFromFirebase()`.

#### `onCreateOptionsMenu(menu: Menu): Boolean` (lines 54–57)
- Inflates `R.menu.report_viewer_menu` (contains the download action).

#### `onOptionsItemSelected(item: MenuItem): Boolean` (lines 59–71)
- Handles two menu items:
  - `android.R.id.home` → calls `finish()` (back navigation).
  - `R.id.action_download` → calls `downloadReport()`.

#### `setupWebView()` (lines 73–86)
- Configures the `WebView` settings:
  - Enables JavaScript, DOM storage, wide viewport, and built-in zoom controls.
  - Hides native zoom control buttons.
  - Sets a default `WebViewClient`.

#### `loadReportFromFirebase()` (lines 88–134)
- Gets the current user and username from SharedPreferences.
- Shows loading message via `showLoadingMessage()`.
- **New path:** `users/{username}/data/report` → reads `"reportHtml"` field.
  - On success: caches HTML in `currentReportHtml`, loads into WebView via `loadDataWithBaseURL()`.
  - On failure or missing document: falls back to `loadLegacyReport(user.uid)`.
- If no username in prefs: falls back to legacy path.
- If no user authenticated: calls `showErrorMessage()`.

#### `loadLegacyReport(userId: String)` (lines 136–165)
- Reads from legacy Firestore path: `assessment_reports/{userId}`.
- On success: caches HTML, loads into WebView.
- On failure or missing: calls `showErrorMessage()`.

#### `downloadReport()` (lines 167–188)
- Guards: if `currentReportHtml` is null, shows a toast and returns.
- On Android < 10 (API < 29): checks `WRITE_EXTERNAL_STORAGE` permission, requests it if not granted.
- On Android 10+: skips permission check (MediaStore handles it).
- Calls `saveReportToDownloads(htmlContent)`.

#### `saveReportToDownloads(htmlContent: String)` (lines 190–227)
- Generates a filename: `Embedded_Systems_Report_{username}_{timestamp}.html`.
- **Android 10+ (API ≥ 29):** Uses `MediaStore.Downloads.EXTERNAL_CONTENT_URI` via `ContentValues` and `contentResolver.openOutputStream()`.
- **Older versions:** Writes directly to `Environment.DIRECTORY_DOWNLOADS` via `FileOutputStream`.
- Shows a confirmation toast with the file path on success, or an error toast on failure.

#### `onRequestPermissionsResult(requestCode, permissions, grantResults)` (lines 229–242)
- If `PERMISSION_REQUEST_CODE` and granted: retries `saveReportToDownloads()` with the cached HTML.
- If denied: shows a toast explaining the permission requirement.

#### `showLoadingMessage()` (lines 244–302)
- Builds a styled HTML string with:
  - Dark background (`#0f172a`), indigo spinner animation, heading `"Loading Your Report..."`.
- Loads this HTML into the WebView as a placeholder while fetching the report.

#### `showErrorMessage()` (lines 304–351)
- Builds a styled HTML string with:
  - Dark background, red heading `"📄 No Report Found"`, explanatory text.
- Loads this HTML into the WebView as the error state.

### Design Decisions
- **Dual-path Firestore lookup:** The app migrated its report storage. The new path uses `users/{username}/data/report`; the legacy path is `assessment_reports/{uid}`. The fallback ensures old users can still view reports without migration.
- **WebView rendering:** HTML reports contain rich formatting (tables, charts, CSS) that would be impossible to replicate with native Android views.
- **In-WebView loading/error states:** Rather than using native Android views for loading/error, styled HTML is loaded directly into the WebView, creating a seamless visual experience.
- **MediaStore for downloads:** Avoids needing `WRITE_EXTERNAL_STORAGE` on Android 10+, following scoped storage best practices.

---

## Navigation Context

```
LoginActivity
  └─ IntroductionActivity
       ├─ (has report) → MainActivity (dashboard)
       └─ (no report) → AssessmentActivity
                              ├─ showCompletionState()
                              │    ├─ "Preview Report" → ReportViewerActivity
                              │    └─ "Continue to Home" → MainActivity
                              └─ (error) → retry or back
```
