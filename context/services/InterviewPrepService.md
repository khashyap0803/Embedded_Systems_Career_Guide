# InterviewPrepService.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/InterviewPrepService.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 17,191 bytes (365 lines)

---

## What This File Does (Simple Explanation)

This service helps students **prepare for embedded systems job interviews**. It has two features:

1. **Question Generation** — Creates interview questions based on what the student has learned (topics completed)
2. **Answer Evaluation** — The student types their answer, and AI grades it (0-100) with specific feedback

If AI fails, it provides 8 hardcoded real interview questions with ideal answers and follow-ups.

---

## Why This File Exists

Interview prep is a V2 feature that takes the app beyond just learning — it actively prepares students for jobs. This service bridges the gap between "knowing concepts" and "explaining them to an interviewer."

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `PracticeFragment.kt` | `generateInterviewQuestions()` for interview mode |
| `InterviewActivity.kt` | `evaluateAnswer()` for answer grading |

---

## Complete Code Walkthrough

### Lines 40-48: `InterviewQuestion` Data Class
Fields: `id`, `question`, `category` (technical/behavioral/system-design), `difficulty` (easy/medium/hard), `idealAnswer`, `keyPoints` list, `followUpQuestions` list.

### Lines 53-59: `AnswerEvaluation` Data Class
Fields: `score` (0-100), `feedback`, `strengths` list, `improvements` list, `isAcceptable` (score ≥ 60).

### Lines 74-110: `generateInterviewQuestions()`
1. Calls `GeminiServiceV2.PromptTemplates.interviewPrep()` with topics, difficulty, count
2. Parses AI response into `InterviewQuestion` list
3. Falls back to hardcoded questions on failure
4. Uses lambda callback `(Result<List<InterviewQuestion>>) -> Unit` instead of interface

### Lines 115-191: `evaluateAnswer()` — AI Grading
1. Checks for blank answers → immediate 0 score
2. Builds evaluation prompt with: question, category, difficulty, ideal answer key points, student's answer
3. Sends to AI for grading on: technical accuracy, completeness, clarity, practical examples
4. **Fallback grading** (if AI fails): purely word-count based:
   - < 20 words → 30 points
   - < 50 words → 50 points
   - < 100 words → 70 points
   - 100+ words → 80 points

### Lines 264-344: `createFallbackQuestions()` — 8 Hardcoded Questions
Professional-grade interview questions:
1. **volatile vs const** (medium) — with follow-up about ISR variables
2. **Priority inversion in RTOS** (hard) — with follow-up about FreeRTOS
3. **I2C protocol vs UART** (medium) — with follow-up about address conflicts
4. **Hard fault debugging on ARM** (hard) — with follow-up about bus faults
5. **GPIO pull-up configuration** (easy) — with follow-up about external pull-ups
6. **DMA** (medium) — with follow-up about limitations
7. **Atomic access** (medium) — with follow-up about interrupt disable drawbacks
8. **ARM boot process** (hard) — with follow-up about bootloaders

### Lines 350-363: Utility Methods
- `getCategories()` — returns 7 topic categories
- `getDifficultyLevels()` — returns ["easy", "medium", "hard"]

---

## Dependencies

| Import | Why |
|--------|-----|
| `GeminiServiceV2` | AI content generation |
| `Gson`, `JsonObject` | JSON parsing |
| `Dispatchers.IO` | Background threading |

---

## Strengths

- ✅ Two-way AI interaction (generate questions → evaluate answers)
- ✅ Each question has follow-ups for deeper assessment
- ✅ Excellent fallback questions — real interview material
- ✅ Word-count fallback evaluation when AI is unavailable

## Weaknesses / Technical Debt

- ⚠️ Fallback evaluation based purely on word count — not content quality
- ⚠️ No Firestore caching of generated questions (re-generates each time)
- ⚠️ Uses lambda callback inconsistently (not the `InterviewCallback` interface)
- ⚠️ `InterviewCallback` interface is defined but never used
