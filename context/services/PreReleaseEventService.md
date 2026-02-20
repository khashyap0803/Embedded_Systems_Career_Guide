# PreReleaseEventService.kt — Documentation

## File Identity
| Field | Value |
|---|---|
| **File** | `PreReleaseEventService.kt` |
| **Package** | `com.example.embeddedsystemscareerguide.services` |
| **Lines** | 953 |
| **Role** | Manages all Firebase Realtime Database operations for the pre-release challenge event |

---

## 1. Why Does This File Exist?

The app has a **competitive pre-release event** where participants complete three challenges in sequence. This service handles every aspect of the event's backend:

- **Registration** of participants
- **Status tracking** (waiting, in_progress, completed, terminated, resumable)
- **Challenge lifecycle** (start, submit with evaluation, timeout)
- **Warnings and termination** for rule violations (atomic transactions)
- **Real-time rankings** via Firebase `callbackFlow` listeners
- **Challenge state save/restore** for resume functionality
- **Admin controls** for event configuration, extra time, session resume, and participant management

It bridges Kotlin coroutines with Firebase's callback-based API using `suspendCancellableCoroutine` and `callbackFlow`.

---

## 2. Imports (Lines 1–16)

```kotlin
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
```

Key imports:
- **Firebase Realtime Database** — `DatabaseReference`, `ValueEventListener`, `DataSnapshot`, `MutableData`
- **Kotlin Coroutines Flow** — `callbackFlow` for real-time streams
- **`suspendCancellableCoroutine`** — Bridges Firebase callbacks to suspend functions

---

## 3. Class & Companion Object (Lines 18–51)

```kotlin
class PreReleaseEventService private constructor() {
    companion object {
        private const val TAG = "PreReleaseEventService"

        @Volatile
        private var instance: PreReleaseEventService? = null

        fun getInstance(): PreReleaseEventService
    }
}
```

Thread-safe double-checked locking singleton. No `Context` needed.

---

## 4. Firebase Database Structure

```
preReleaseEvent/
├── config/
│   ├── eventActive: Boolean
│   ├── startTime: Long
│   └── endTime: Long
├── participants/
│   └── {rollNumber}/
│       ├── profile/
│       │   ├── rollNumber: String
│       │   ├── registeredAt: Long
│       │   └── lastActiveAt: Long
│       ├── status/
│       │   ├── currentStatus: String
│       │   ├── warningCount: Int
│       │   ├── isTerminated: Boolean
│       │   ├── terminationReason: String?
│       │   ├── canAccessChallenge2: Boolean
│       │   └── canAccessChallenge3: Boolean
│       ├── challenge1/
│       │   ├── status: String
│       │   ├── startedAt: Long
│       │   ├── completedAt: Long?
│       │   ├── timeTakenMs: Long?
│       │   ├── extraTimeGrantedMs: Long
│       │   ├── savedState: String?
│       │   ├── savedTimeRemainingMs: Long?
│       │   ├── savedProblemIndex: Int?
│       │   ├── stateLastSavedAt: Long?
│       │   ├── savedQuestions: String?
│       │   ├── submissions/
│       │   └── evaluation/
│       ├── challenge2/
│       │   └── (same structure as challenge1)
│       └── challenge3/
│           └── (same structure as challenge1)
└── rankings/
    └── universal/
        └── {rollNumber}/
            ├── rollNumber: String
            ├── ch1: Int (weighted score)
            ├── ch2: Int (weighted score)
            ├── ch3: Int (weighted score)
            ├── totalScore: Int
            └── totalTimeMs: Long
```

> **Note**: The root path is `preReleaseEvent` (camelCase), not `pre_release_event`.

---

## 5. Core Reference Helpers (Lines 38–51)

```kotlin
private val database = FirebaseDatabase.getInstance()
private val eventRef = database.getReference(ChallengeConstants.PATH_PRE_RELEASE_EVENT)

fun getEventRef(): DatabaseReference
fun getConfigRef(): DatabaseReference
fun getParticipantsRef(): DatabaseReference
fun getParticipantRef(rollNumber: String): DatabaseReference  // public
fun getRankingsRef(): DatabaseReference
```

Uses paths from `ChallengeConstants` (`PATH_PRE_RELEASE_EVENT`, `PATH_CONFIG`, etc.).

---

## 6. Config & Event (Lines 55–69)

| Function | Signature | Purpose |
|---|---|---|
| `getEventConfig()` | `suspend fun`: `ChallengeConfig?` | Reads full config from `config/` node |
| `isEventActive()` | `suspend fun`: `Boolean` | Reads `config/eventActive` |

---

## 7. Registration & Status (Lines 73–167)

| Function | Signature | Purpose |
|---|---|---|
| `registerParticipant()` | `suspend fun(rollNumber)`: `Boolean` | Creates participant with profile + status; idempotent (returns true if exists) |
| `getParticipantStatus()` | `suspend fun(rollNumber)`: `ParticipantStatus?` | Reads full `ParticipantStatus` object including `warningCount`, `isTerminated`, `canAccessChallenge2/3` |
| `updateParticipantStatus()` | `suspend fun(rollNumber, status)`: `Boolean` | Updates `currentStatus` field |
| `isChallengeCompleted()` | `suspend fun(rollNumber, challengeNumber)`: `Boolean` | Checks if `challenge{N}/status == "completed"` |
| `updateLastActive()` | `suspend fun(rollNumber)` | Updates `profile/lastActiveAt` timestamp |

---

## 8. Challenge Lifecycle (Lines 171–403)

### Per-Challenge Start & Submit Functions

| Function | Lines | Purpose |
|---|---|---|
| `startChallenge1(rollNumber)` | 171–185 | Sets `challenge1/status = "in_progress"`, `startedAt`, updates participant status |
| `submitChallenge1(rollNumber, submissions, evaluation, isTimeout)` | 187–240 | Stores submissions + evaluation, calculates weighted score (`evaluation.weightedScore`), updates rankings, clears saved state |
| `startChallenge2(rollNumber)` | 244–258 | Same as Ch1 but for challenge2 |
| `submitChallenge2(rollNumber, questions, evaluation, isTimeout)` | 260–322 | Stores question data + evaluation, calculates weighted score, updates rankings |
| `startChallenge3(rollNumber)` | 326–340 | Same pattern for challenge3 |
| `submitChallenge3(rollNumber, questions, evaluation, isTimeout)` | 342–403 | Stores question data + evaluation, calculates weighted score, updates rankings |

**Key implementation detail**: All `submitChallenge*` functions use `evaluation.weightedScore` (pre-computed by the Activity's `calculateTimeBonus` + weight math), not re-calculating it. This prevents double-counting bugs.

---

## 9. Warning & Termination System (Lines 407–461)

### `addWarning()` (Lines 407–427)
```kotlin
suspend fun addWarning(rollNumber: String): Boolean
```
- Uses **Firebase Transaction** (`runTransaction`) for atomic increment of `status/warningCount`
- Prevents race conditions when multiple clients write simultaneously
- Does **not** auto-terminate — termination is handled separately by the Activity

### `terminateParticipant()` (Lines 429–443)
```kotlin
suspend fun terminateParticipant(rollNumber: String, reason: String): Boolean
```
- Sets `status/isTerminated = true`
- Sets `status/terminationReason = reason`
- Sets `status/currentStatus = "terminated"`

### `timeoutChallenge()` (Lines 445–461)
```kotlin
suspend fun timeoutChallenge(rollNumber: String, challengeNumber: Int): Boolean
```
- Sets `challenge{N}/status = "timeout"`
- Sets `status/currentStatus = "timeout"`

---

## 10. Real-Time Observation (Lines 465–502)

### `observeParticipantStatus()` (Lines 465–481)
```kotlin
fun observeParticipantStatus(rollNumber: String): Flow<ParticipantStatus>
```
Uses `callbackFlow` to create a real-time stream of the participant's full status object. `awaitClose` ensures listener cleanup.

### `observeUniversalRankings()` (Lines 483–502)
```kotlin
fun observeUniversalRankings(): Flow<List<RankingEntry>>
```
Real-time stream of all rankings, sorted by `totalScore` descending. Emits new sorted list whenever any ranking changes.

---

## 11. Admin Functions (Lines 506–700)

| Function | Lines | Purpose |
|---|---|---|
| `toggleChallengeAccess(rollNumber, challengeNumber, enabled)` | 506–516 | Sets `canAccessChallenge2/3` boolean |
| `grantExtraTime(rollNumber, challengeNumber, extraTimeMs)` | 518–529 | Adds extra time to `challenge{N}/extraTimeGrantedMs` |
| `resumeParticipantSession(rollNumber)` | 531–551 | Resets terminated/timed-out participant to `"in_progress"` |
| `addExtraTime(rollNumber, minutes)` | 553–608 | Wrapper: converts minutes to ms, finds active challenge, calls `grantExtraTime()` |
| `getParticipantDetails(rollNumber)` | 610–630 | Returns `ParticipantDetails` with scores from all challenges |
| `observeAllParticipants()` | 632–687 | `callbackFlow` emitting `List<ParticipantStatus>` for admin dashboard |
| `deleteParticipant(rollNumber)` | 689–700 | Removes participant from Firebase |
| `getAllParticipants()` | 702–717 | One-shot fetch of all participants |
| `updateUniversalRankings()` | 721–764 | Recalculates and writes all rankings sorted by total score, with rank positions |
| `isValidRollNumber(rollNumber)` | 768–770 | Validates against `ChallengeConstants.ROLL_NUMBER_REGEX` |

---

## 12. Question Save/Load (Lines 774–832)

| Function | Lines | Purpose |
|---|---|---|
| `saveGeneratedQuestions(rollNumber, challengeNumber, questionsJson)` | 774–792 | Saves generated questions JSON for resume |
| `loadSavedQuestions(rollNumber, challengeNumber)` | 794–809 | Loads previously saved questions JSON |
| `getTimedOutChallengeNumber(rollNumber)` | 811–832 | Returns which challenge has `"timeout"` status (0 if none) |

---

## 13. State Save/Restore for Resume (Lines 834–951)

| Function | Lines | Purpose |
|---|---|---|
| `getExtraTimeInfo(rollNumber)` | 834–856 | Checks all 3 challenges for `timeout`/`resumable` status with extra time. Returns `Pair(challengeNumber, extraTimeMs)` or `null` |
| `setChallengeResumable(rollNumber, challengeNumber)` | 858–874 | Sets challenge status to `"resumable"` and participant status to `"resumable"` |
| `saveChallengeState(rollNumber, challengeNumber, stateJson, timeRemainingMs, currentProblemIndex)` | 878–905 | Saves complete state: `savedState`, `savedTimeRemainingMs`, `savedProblemIndex`, `stateLastSavedAt` |
| `loadChallengeState(rollNumber, challengeNumber)` | 907–931 | Returns `Triple(stateJson, timeRemainingMs, problemIndex)` or `null` |
| `clearChallengeState(rollNumber, challengeNumber)` | 933–951 | Sets all saved state fields to `null` |

---

## 14. Connection Map

```
┌───────────────────┐     ┌─────────────────────────────┐     ┌──────────────────┐
│ ChallengeActivity │────▶│                             │────▶│ Firebase         │
│ (1, 2, 3)        │     │ PreReleaseEventService       │     │ Realtime DB      │
├───────────────────┤     │ (Singleton)                  │◀────│                  │
│ RankingDashboard  │────▶│                             │     └──────────────────┘
│ (rankings UI)     │     │  ┌─────────────────────┐    │
│                   │◀────│  │ Flow<RankingEntry>   │    │
├───────────────────┤     │  │ Flow<ParticipantStatus>│  │
│ AdminDashboard    │────▶│  └─────────────────────┘    │
│ (admin controls)  │     └─────────────────────────────┘
└───────────────────┘
```

### Key Consumer Relationships
| Consumer | Functions Used |
|---|---|
| Challenge Activities | `startChallenge*()`, `submitChallenge*()`, `saveChallengeState()`, `loadChallengeState()`, `addWarning()`, `terminateParticipant()` |
| Rankings UI | `observeUniversalRankings()`, `observeParticipantStatus()` |
| Roll Number Entry | `registerParticipant()`, `getParticipantStatus()`, `isChallengeCompleted()` |
| Admin Dashboard | `toggleChallengeAccess()`, `resumeParticipantSession()`, `addExtraTime()`, `deleteParticipant()`, `observeAllParticipants()` |

---

## 15. Coroutine-Firebase Bridge Pattern

```kotlin
suspend fun someOperation(): Boolean = suspendCancellableCoroutine { cont ->
    firebaseRef.someOperation()
        .addOnSuccessListener { cont.resume(true) }
        .addOnFailureListener { e ->
            Log.e(TAG, "Failed", e)
            cont.resume(false)  // Returns false instead of throwing
        }
}
```

**Design choice**: Failures return `false`/`null` instead of throwing exceptions, keeping error handling simple for callers.

**Exception**: `addWarning()` uses `runTransaction` for atomic increments, not `suspendCancellableCoroutine`.

---

## 16. Summary

`PreReleaseEventService` (953 lines) is the **complete Firebase backend for the competitive challenge event**. It manages the full participant lifecycle from registration through 3 separate challenge submissions, with atomic warning transactions, real-time ranking flows, challenge state persistence (save/load/clear), and comprehensive admin controls. All data is stored in Firebase Realtime Database under the `preReleaseEvent` root path, using `ChallengeConstants` path constants. Operations are bridged to Kotlin coroutines via `suspendCancellableCoroutine` for one-shot operations and `callbackFlow` for real-time streams.
