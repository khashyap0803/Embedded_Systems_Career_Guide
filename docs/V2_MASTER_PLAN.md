# 🚀 Embedded Systems Career Guide - V2.0 Master Plan

**Document Created:** January 29, 2026  
**Target Launch:** February 7, 2026  
**Status:** Planning Phase

---

## 📋 Executive Summary

Transform the current app into an **AI-powered extreme learning platform** with personalized stages, rich content generation, flashcards, enhanced quizzes, and comprehensive analytics.

---

## 💳 Budget & Credits

| Credit Type | Amount | Expires | Purpose |
|-------------|--------|---------|---------|
| **Free Trial** | ₹26,088 | March 19, 2026 | App launch & testing |
| **GenAI App Builder** | ₹89,272 | December 21, 2026 | Reserved for later |
| **Total Available** | ₹115,360 | | |

### Strategy
- Use ₹26K credits for initial launch (50 days runway)
- **Unlimited usage** - burn credits for real data
- Monitor actual billing for 1 month
- Decide on optimization or use ₹89K credits based on data

---

## 👥 Target Audience

| Metric | Value |
|--------|-------|
| Target Users | 50 (personal reach limit) |
| Distribution | Manual APK |
| Usage Limits | **Unlimited** (deliberate) |

---

## 🎯 Feature Requirements

### Confirmed Decisions

| Decision | Choice |
|----------|--------|
| Number of stages | **30-50** (optimal for learning) |
| UI design | Keep current |
| Flashcard style | **Swipe cards** (Tinder-style) |
| Stage content | **Kindle-style pages** |
| Feature priority | **ALL must-have** |
| Code playground | With execution if possible, else AI review only |

---

## 🏗️ Complete Feature List

### 1. Dynamic Personalized Stages (30-50 stages)
**Description:** AI generates custom learning path based on assessment results

| Aspect | Detail |
|--------|--------|
| Generation trigger | After assessment completion |
| Stage count | 30-50 based on skill gaps |
| Personalization | Topics, order, difficulty based on user's weak areas |
| Storage | Firestore: `users/{userId}/personalized_stages` |
| Regeneration | Allow user to retake assessment and regenerate |

**AI Prompt Requirements:**
- Analyze assessment weak areas
- Create progressive difficulty curve
- Include practical projects
- Balance theory and hands-on

---

### 2. AI-Generated Stage Content
**Description:** Rich learning material for each stage

| Section | Content |
|---------|---------|
| Theory | Core concepts explained |
| Key Points | Bullet-point summary |
| Code Examples | Practical code with comments |
| Common Mistakes | What to avoid |
| Pro Tips | Industry best practices |
| Mini Challenge | Quick practice problem |

**UI:** Kindle-style page navigation (swipe left/right)

---

### 3. Flashcards per Stage
**Description:** 15-20 flashcards per stage for revision

| Aspect | Detail |
|--------|--------|
| Cards per stage | 15-20 |
| Interaction | Swipe cards (Tinder-style) |
| Features | Tap to flip, swipe right = know, swipe left = review |
| Spaced repetition | Track cards marked for review |
| Storage | Firestore: `users/{userId}/flashcards/{stageId}` |

---

### 4. Enhanced Quizzes with Explanations
**Description:** Real-time AI explanations for each answer

| Feature | Detail |
|---------|--------|
| Questions | 5 per quiz (dynamic) |
| After answer | AI explains why correct/incorrect |
| On wrong answer | Provides learning hint |
| Score breakdown | Per-topic analysis |

---

### 5. AI-Powered Doubt Solving Chat
**Description:** Context-aware tutoring chat

| Feature | Detail |
|---------|--------|
| Context | Knows user's current stage & history |
| Capabilities | Concept explanation, code help, debugging |
| Code support | Can analyze and explain code |
| Follow-up | Remembers conversation context |

---

### 6. Progress Analytics with AI Insights
**Description:** Weekly AI-generated progress reports

| Metric | Analysis |
|--------|----------|
| Learning pace | Stages completed vs time |
| Strengths | Topics with high quiz scores |
| Weaknesses | Topics needing review |
| Recommendations | Personalized next steps |
| Comparison | Progress vs typical learner |

---

### 7. Code Playground
**Description:** Write, run, and get AI review of code

| Feature | Priority |
|---------|----------|
| Code editor | Syntax highlighting |
| Run code | Execute C/Python (if time permits) |
| AI review | Always available |
| Output analysis | AI explains errors/output |

**Decision:** If execution is complex, ship with AI review only. Add execution later.

---

### 8. Interview Prep
**Description:** AI-generated interview questions

| Feature | Detail |
|---------|--------|
| Question types | Technical, behavioral, system design |
| Difficulty | Based on user's progress |
| Mock interview | Timed question answering |
| Feedback | AI evaluates answers |

---

### 9. Project Suggestions
**Description:** Personalized project ideas

| Feature | Detail |
|---------|--------|
| Based on | Completed stages & skills |
| Difficulty levels | Beginner, Intermediate, Advanced |
| Details | Requirements, expected outcome, resources |
| Tracking | Mark projects as started/completed |

---

### 10. Daily Tips
**Description:** AI-generated learning tips via notification

| Feature | Detail |
|---------|--------|
| Frequency | Once daily |
| Content | Tip related to current learning stage |
| Delivery | Push notification |
| In-app | Tips history section |

---

## 🔥 Firebase Architecture (Cloud-First)

### Storage Estimate

| Data | Per User | 50 Users |
|------|----------|----------|
| User profile | 2 KB | 100 KB |
| Assessment | 10 KB | 500 KB |
| Personalized stages | 100 KB | 5 MB |
| Stage content | 500 KB | 25 MB |
| Flashcards | 200 KB | 10 MB |
| Quiz history | 50 KB | 2.5 MB |
| Progress | 20 KB | 1 MB |
| Chat history | 100 KB | 5 MB |
| **Total** | **~1 MB** | **~50 MB** |

**Verdict:** Well under 1 GiB free limit ✅

### Firestore Structure
```
users/
  {userId}/
    profile/
    assessment_results/
    personalized_stages/
      {stageId}/
        content/
        flashcards/
        quiz_history/
    progress/
    chat_history/
    projects/
    interview_prep/

cached_content/  (shared across users)
  stages/
  flashcard_templates/
  quiz_pools/
```

### Daily Operations (50 Users)

| Operation | Estimate | Free Limit | Status |
|-----------|----------|------------|--------|
| Reads | 10,000 | 50,000 | ✅ |
| Writes | 2,500 | 20,000 | ✅ |
| Deletes | 250 | 20,000 | ✅ |

### Billing Note
> Firebase Blaze Plan charges go to **Google Cloud billing** - same as Gemini API. Your ₹26K and ₹89K credits work for Firebase too!

---

## 📅 Development Timeline

| Date | Day | Focus | Deliverables |
|------|-----|-------|--------------|
| **Jan 29** | Wed | Planning | This document, architecture design |
| **Jan 30** | Thu | Core AI Services | Gemini service refactor, prompt templates |
| **Jan 31** | Fri | Personalized Stages | Assessment → AI stages generation |
| **Feb 1** | Sat | Stage Content | AI content generation, Kindle UI |
| **Feb 2** | Sun | Flashcards | Card generation, swipe UI |
| **Feb 3** | Mon | Enhanced Quiz | Per-answer explanations |
| **Feb 4** | Tue | Chat & Analytics | Context-aware chat, progress reports |
| **Feb 5** | Wed | Additional Features | Interview prep, projects, daily tips |
| **Feb 6** | Thu | Code Playground | Editor + AI review (execution if time) |
| **Feb 7** | Fri | **LAUNCH** | Final testing, build APK |

---

## 💰 Estimated Token Usage (Full Features)

### Per User (Monthly, Heavy Usage)

| Feature | Output Tokens | Cost |
|---------|---------------|------|
| Personalized stages | 50,000 | ₹11.25 |
| 50 stage contents | 500,000 | ₹112.50 |
| 50 flashcard sets | 250,000 | ₹56.25 |
| Enhanced quizzes | 100,000 | ₹22.50 |
| Chat (100 msgs) | 80,000 | ₹18.00 |
| Analytics (4 reports) | 32,000 | ₹7.20 |
| Interview prep | 50,000 | ₹11.25 |
| Projects | 20,000 | ₹4.50 |
| Daily tips | 15,000 | ₹3.38 |
| Code playground | 50,000 | ₹11.25 |
| **Total/User** | **1,147,000** | **₹258/month** |

### 50 Users
```
₹258 × 50 = ₹12,900/month
With buffer: ~₹15,000/month
```

### Budget Runway (₹26,088)
```
₹26,088 ÷ ₹15,000 = ~52 days (perfect for trial period!)
```

---

## ❓ Open Questions for Future

1. After March: Use ₹89K credits or optimize?
2. Monetization strategy?
3. Scale beyond 50 users?

---

## 📝 Discussion History

### January 29, 2026

**Session 1: Code Audit Completion**
- Fixed all 23 code audit issues
- LearningPath crash resolved
- SharedPreferences keys centralized

**Session 2: Cost Analysis**
- Analyzed Jan 27-28 billing data
- Created API_COST_ANALYSIS.md
- Projected costs for 50 users

**Session 3: V2.0 Planning (Current)**
- Decided on extreme AI features
- Firebase storage analyzed - fits free tier
- Timeline confirmed: Jan 29 - Feb 7
- All features marked as must-have

---

## ✅ Next Steps

1. Create implementation_plan.md with technical details
2. Design Firestore schema
3. Create Gemini prompt templates
4. Start building personalized stages feature

---

*Document will be updated as discussions continue*
