# ProjectSuggestionService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/ProjectSuggestionService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 14,619 bytes (337 lines)

---

## What This File Does (Simple Explanation)

This service **suggests hands-on embedded systems projects** based on what the student has learned. If a student just completed GPS and sensor stages, it might suggest building a "GPS Tracker with LCD Display." It determines skill level (beginner → expert) from progress percentage and provides step-by-step instructions.

---

## Why This File Exists

Practical projects are essential for learning embedded systems. This service bridges theory and practice by:
1. Analyzing which stages the student completed
2. Determining their skill level
3. Generating personalized project ideas via AI
4. Providing complete project specs (components, steps, outcomes)
5. Tracking project status (not started / in progress / completed)

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `PracticeFragment.kt` | `generateProjectSuggestions()` for project recommendations |

---

## Complete Code Walkthrough

### Lines 50-98: `generateProjectSuggestions()` — Main Entry Point
1. Loads personalized stages from Firestore
2. If no completed stages → returns 3 beginner projects
3. Determines skill level from completion percentage
4. Calls AI with `projectSuggestions()` prompt
5. Parses response → saves to Firestore → returns via callback
6. Falls back to hardcoded projects on failure

### Lines 103-114: `determineSkillLevel()`
Based on completion percentage:
- < 25% → "beginner"
- < 50% → "intermediate"  
- < 75% → "advanced"
- ≥ 75% → "expert"

### Lines 161-222: `createBeginnerProjects()` — 3 Starter Projects
1. **LED Blinker** (2 hours) — GPIO basics
2. **Button Controlled LED** (3 hours) — input reading + debouncing
3. **Serial Communication Logger** (4 hours) — UART debugging

Each includes: `skills`, `components` (real hardware), `learningOutcomes`, and step-by-step `steps`.

### Lines 228-317: `createFallbackProjects()` — 4 Intermediate/Advanced Projects
1. **Digital Thermometer with LCD** (8 hours) — ADC + I2C + LCD
2. **PWM Motor Speed Controller** (6 hours) — timers + motor drivers
3. **Real-Time Clock with Alarm** (10 hours) — I2C + interrupts + RTC
4. **Multi-Sensor Dashboard (RTOS)** (20 hours) — FreeRTOS + multi-tasking

### Lines 323-335: `updateProjectStatus()`
Updates project status in Firestore: "not_started" → "in_progress" → "completed".

---

## Dependencies

| Import | Why |
|--------|-----|
| `GeminiServiceV2` | AI project generation |
| `FirestoreManager` | Project storage and status tracking |
| `Gson`, `JsonObject` | JSON parsing |

---

## Strengths

- ✅ Skill-adaptive project suggestions
- ✅ Complete project specs with real components
- ✅ Project status tracking in Firestore
- ✅ Excellent fallback projects — practical and well-structured

## Weaknesses / Technical Debt

- ⚠️ Fallback projects don't adapt to completed topics — always same projects
- ⚠️ `Project` data class defined elsewhere (likely `FirestoreManager`)
- ⚠️ No project regeneration support (unlike `FlashcardService`)
- ⚠️ Status tracking uses strings instead of an enum
