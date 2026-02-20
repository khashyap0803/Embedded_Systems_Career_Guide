# FlashcardService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/FlashcardService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 12,645 bytes (265 lines)

---

## What This File Does (Simple Explanation)

This service **generates AI-powered flashcards** for each learning stage. When a user completes a stage or wants to review, this service creates 15 flashcards covering that stage's topics. The user can swipe through them — "Know It" or "Need Review" — and the service tracks which ones need revisiting (spaced repetition).

---

## Why This File Exists

Flashcards are a scientifically-proven learning tool. This service:
1. Checks Firestore for existing flashcards for a stage
2. If none exist → generates 15 new ones using Gemini AI
3. Caches them in Firestore for future use
4. Tracks "needs review" status for spaced repetition
5. Falls back to 15 hardcoded generic flashcards if AI fails

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `LearningPathFragment.kt` | `getFlashcards()` when user taps "Flashcards" on a completed stage |
| `PracticeFragment.kt` | `getAllCardsNeedingReview()` for cross-stage review sessions |

---

## Complete Code Walkthrough

### Lines 19-33: Singleton + Constants
- `DEFAULT_CARD_COUNT = 15` — flashcards per stage
- Thread-safe singleton pattern

### Lines 42-46: `FlashcardCallback` Interface
Three callbacks: `onProgress(message)`, `onSuccess(flashcards)`, `onError(error)`.

### Lines 51-78: `getFlashcards()` — Main Entry Point
1. Checks Firestore for existing cached flashcards
2. If found with data → returns them immediately
3. If not → calls `generateAndCacheFlashcards()`

### Lines 83-126: `generateAndCacheFlashcards()`
1. Builds prompt using `GeminiServiceV2.PromptTemplates.flashcards()`
2. Calls Gemini API with 4096 max tokens (flashcards need more output)
3. Parses response into `Flashcard` objects
4. Caches in Firestore via `firestoreManager.saveFlashcards()`
5. Falls back to hardcoded cards on any failure

### Lines 131-164: `parseFlashcardsFromResponse()`
Parses JSON with structure `{"flashcards": [{id, front, back, difficulty, category}]}`:
- `front` — question side
- `back` — answer side
- `difficulty` — "easy", "medium", "hard"
- `category` — "concept", "code", "application"
- `needsReview` defaults to `false`

### Lines 169-192: `createFallbackFlashcards()` — 15 Pre-Built Cards
Hardcoded embedded systems flashcards covering:
1. What is an embedded system?
2. `volatile` keyword
3. Infinite main loop
4. GPIO
5. Polling vs Interrupts
6. Watchdog timer
7. Bit manipulation
8. UART
9. I2C vs SPI
10. Debouncing
11. PWM
12. Pull-up/pull-down resistors
13. ADC
14. ARM Cortex-M features
15. RTOS basics

### Lines 197-209: `updateFlashcardReview()` — Track Review Status
After user swipes a card, updates its `needsReview` flag in Firestore.

### Lines 214-228: `getFlashcardsNeedingReview()` — Stage-Specific Review
Returns only flashcards flagged for review in a specific stage.

### Lines 233-252: `getAllCardsNeedingReview()` — Cross-Stage Review
Iterates all completed stages, collects flashcards needing review, returns a Map<stageId, List<Flashcard>>.

### Lines 257-263: `regenerateFlashcards()` — Force New Generation
Skips the cache check and generates fresh flashcards from AI.

---

## Dependencies

| Import | Why |
|--------|-----|
| `GeminiServiceV2` | AI flashcard generation |
| `FirestoreManager` | Flashcard caching and review tracking |
| `Gson`, `JsonObject` | JSON parsing |

---

## Strengths

- ✅ Cache-first strategy — one API call per stage lifetime
- ✅ Excellent fallback content — 15 real flashcards
- ✅ Spaced repetition tracking via `needsReview` flag
- ✅ Cross-stage review capability
- ✅ Regeneration support for fresh content

## Weaknesses / Technical Debt

- ⚠️ Spaced repetition is binary (review/don't) — no interval scheduling
- ⚠️ Fallback cards are always the same regardless of stage topic
- ⚠️ No difficulty adaptation based on user performance
- ⚠️ `Flashcard` data class defined elsewhere (in `FirestoreManager`)
