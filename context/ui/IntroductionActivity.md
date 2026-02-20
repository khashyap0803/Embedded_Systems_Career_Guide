# Introduction Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.introduction`  
> **File:** `IntroductionActivity.kt` (103 lines)

---

## Purpose
Gateway screen after login. Checks Firestore for an existing assessment report and routes accordingly:
- **Has report →** `MainActivity` (main dashboard)
- **No report →** Shows welcome UI with "Start Assessment" button

## Class Overview

| Element | Type | Role |
|---|---|---|
| `IntroductionActivity` | `AppCompatActivity` | Routing screen |
| `binding` | `ActivityIntroductionBinding` | View binding |
| `auth` | `FirebaseAuth` | Gets current user UID |
| `firestore` | `FirebaseFirestore` | Checks for existing report |

## All Functions (5)

- **`onCreate(savedInstanceState)`** — Inflates binding. Calls `checkExistingReportInFirebase()`.
- **`checkExistingReportInFirebase()`** — Gets current user. Disables button ("Checking..."). Gets username from `user_prefs` SharedPreferences. If username exists, checks Firestore path `users/{username}/data/report`:
  - Found → `navigateToHome()`.
  - Not found → `checkLegacyReport(uid)`.
  - Error → `checkLegacyReport(uid)`.
  - No username → `checkLegacyReport(uid)`.
  - No user → `setupUI()`.
- **`checkLegacyReport(userId)`** — Checks `assessment_reports/{userId}` in Firestore:
  - Found → `navigateToHome()`.
  - Not found → `setupUI()`.
  - Error → `setupUI()`.
- **`setupUI()`** — Enables "Start Assessment" button. Sets click → launches `AssessmentActivity` and finishes.
- **`navigateToHome()`** — Launches `MainActivity` with `NEW_TASK | CLEAR_TASK` flags. Finishes.

## Design Decisions
- **Dual-path report check:** Primary path (`users/{username}/data/report`) + legacy fallback (`assessment_reports/{uid}`), matching the same pattern used in `LoginActivity` and `ReportViewerActivity`.
- **Finish on navigation:** Calls `finish()` after all navigation to prevent back-stack loops.
- **Cloud-only:** No local caching — Firestore is the only source of truth for report existence.
