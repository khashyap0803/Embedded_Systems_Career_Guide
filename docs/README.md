<p align="center">
  <img src="../pictures/icon/icon.png" alt="Embedded Systems Career Guide" width="120" height="120" style="border-radius: 20px;">
</p>

<h1 align="center">⚡ Embedded Systems Career Guide</h1>

<p align="center">
  <strong>AI-Powered Android Learning Platform for Embedded Systems Mastery</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-SDK_26--36-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Firebase-34.3.0-FFCA28?logo=firebase&logoColor=black" alt="Firebase">
  <img src="https://img.shields.io/badge/Gemini_AI-2.5_Flash-4285F4?logo=google&logoColor=white" alt="Gemini">
  <img src="https://img.shields.io/badge/Material_3-1.13.0-6750A4?logo=materialdesign&logoColor=white" alt="Material 3">
  <img src="https://img.shields.io/badge/License-Educational-blue" alt="License">
</p>

<p align="center">
  A gamified, AI-driven learning app that transforms how students and professionals learn embedded systems — from microcontrollers to IoT, real-time OS to industry protocols.
</p>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Firebase Setup](#-firebase-setup)
- [Gemini AI Integration](#-gemini-ai-integration)
- [Security](#-security)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🎯 Overview

**Embedded Systems Career Guide** is a production-grade Android application designed to take learners from zero to job-ready in embedded systems. The app combines a structured curriculum with Google's Gemini AI to create a truly personalized learning experience.

### What Makes It Special

| Feature | Description |
|---------|-------------|
| 🤖 **AI-Personalized Path** | No two learners get the same journey — stages adapt to your strengths and weaknesses |
| 📚 **Stanford/MIT/IIT-Level Content** | AI generates detailed academic content with theory, code, and pro tips |
| 🏆 **Gamified Progression** | XP, stars, streaks, and unlockable stages keep you motivated |
| 🧠 **Smart Assessment** | 50-question initial evaluation determines your starting point |
| 💬 **AI Tutor on Demand** | Context-aware chat that knows your progress and current topic |
| 🏅 **Live Competitions** | Real-time coding challenges with universal rankings and leaderboards |

### Who Is This For?

- 🎓 **Students** preparing for embedded systems careers
- 💼 **Professionals** transitioning into embedded development
- 🎯 **Interview candidates** practicing for embedded systems roles
- 📱 **Self-learners** who want a structured, personalized curriculum

---

## ✨ Key Features

### 1. 🗺️ Dynamic Personalized Learning Path (30–50 Stages)

The app generates a custom learning path of 30–50 stages based on your assessment results. Each stage is tailored to your skill gaps, ordered by difficulty, and covers topics from digital electronics to advanced RTOS.

- **AI-generated curriculum** — adapts to your weak/strong areas
- **8 topic categories** — Foundation, Microcontroller, Programming, Communication, Real-Time, IoT, Advanced, Industry
- **Gamified game-path UI** — visual progression map with animated nodes
- **Progress synced to cloud** — pick up where you left off on any device

### 2. 📖 Kindle-Style Stage Content

Each stage includes rich, AI-generated educational content presented in a beautiful reading experience:

| Section | Details |
|---------|---------|
| **Theory** | 1,500–2,000 words of detailed academic content |
| **Key Points** | 8–10 bullet-point summaries with specifics |
| **Code Examples** | Difficulty-adapted code (15–80 lines) with line-by-line explanations |
| **Common Mistakes** | What beginners get wrong and how to avoid it |
| **Pro Tips** | Industry best practices from real embedded engineers |
| **Mini Challenge** | A quick hands-on problem to test your understanding |

Content is generated once via 4 separate AI calls (to avoid truncation) and cached in Firestore for instant retrieval.

### 3. 🧠 AI-Powered Smart Assessment

A comprehensive 50-question assessment that evaluates your knowledge across all embedded systems topics:

- **AI-generated exam** covering from basics to advanced concepts
- **Detailed HTML report** with per-topic breakdown (strengths & weaknesses)
- **Personalized recommendations** for what to study next
- **Retake support** — regenerates your learning path based on new results

### 4. 🃏 Flashcards (Tinder-Style Swipe)

15–20 flashcards per stage for quick revision:

- **Swipe right** = I know this → moves to next
- **Swipe left** = Need review → flagged for revision
- **Tap to flip** = Reveal answer
- **Spaced repetition** tracking for optimal retention

### 5. 📝 Enhanced AI Quizzes

Dynamic quizzes with real-time AI explanations:

- **5 questions per quiz** — generated fresh each time
- **Instant feedback** — AI explains why each answer is correct or wrong
- **Score breakdown** — per-topic performance analysis
- **Progress tracking** — quiz history saved to cloud

### 6. 💬 Context-Aware AI Chat Tutor

An intelligent chat assistant that understands where you are in your learning journey:

- **Knows your current stage** and recently studied topics
- **Conversation memory** — follows up on previous questions
- **Code analysis** — paste code and get detailed explanations
- **Doubt solving** — ask anything about embedded systems

### 7. 🏆 Pre-Release Event Challenge System

A complete competition platform with 3 timed challenge types:

| Challenge | Format | Time | Scoring |
|-----------|--------|------|---------|
| **Challenge 1** | Code modification (fix/enhance embedded code) | 20 min | 6-category AI rubric |
| **Challenge 2** | Multiple choice (5 questions) | 20 min | Weighted scoring |
| **Challenge 3** | Code writing (build from requirements) | 25 min | 6-category AI rubric |

**Additional features:**
- 🔐 Roll number + credential-based authentication
- 🏅 Real-time universal ranking with overall percentage
- 👨‍💼 Admin dashboard for participant monitoring
- ⚠️ Anti-cheat: warning system with auto-termination
- 💾 State preservation: resume challenges after disconnection
- ⏰ Extra time granting by admin

### 8. 📊 Progress Analytics & AI Insights

Comprehensive learning analytics with AI-generated insights:

- **Learning pace** — stages completed vs. time spent
- **Strength/weakness map** — visual topic performance
- **AI recommendations** — personalized next steps
- **Streak tracking** — daily learning consistency
- **XP leaderboard** — compare with other learners

### 9. 🎯 Interview Preparation

AI-powered interview practice tailored to your completed stages:

- **Question types** — technical, behavioral, system design
- **Difficulty scaling** — easy / medium / hard based on progress
- **Answer evaluation** — AI grades your responses with feedback
- **Category filtering** — practice specific topic areas

### 10. 💡 Project Suggestions

Personalized embedded systems project ideas based on your skills:

- **3 difficulty levels** — Beginner, Intermediate, Advanced
- **Step-by-step guidance** — requirements, outcomes, resources
- **Progress tracking** — mark projects as started/completed
- **AI-generated** — suggestions adapt as you learn more

### 11. 🌟 Daily Learning Tips

AI-generated tips delivered daily:

- **Context-aware** — related to your current learning stage
- **Weekly batches** — 7 tips generated at a time for efficiency
- **In-app history** — browse all past tips

---

## 🏗️ Architecture

The app follows the **MVVM** (Model-View-ViewModel) pattern with a clean separation of concerns:

```
┌──────────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER                         │
│                                                                  │
│  ┌──────────────┐  ┌───────────────┐  ┌───────────────────────┐  │
│  │ HomeFragment │  │ LearningPath  │  │  Challenge Activities │  │
│  │ ChatFragment │  │   Fragment    │  │  (1, 2, 3 + Admin)    │  │
│  │ ProfileFrag  │  │ ContentReading│  │  Assessment + Login   │  │
│  │ PracticeFrag │  │  Activity     │  │  Ranking Dashboard    │  │
│  └──────┬───────┘  └──────┬────────┘  └──────────┬────────────┘  │
│         │                 │                      │               │
│         └─────────────────┼──────────────────────┘               │
│                           │                                      │
│                    ┌──────▼──────┐                                │
│                    │  ViewModels │                                │
│                    └──────┬──────┘                                │
└───────────────────────────┼──────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                        SERVICE LAYER (19 Services)               │
│                                                                  │
│  ┌─────────────────────┐  ┌──────────────────┐  ┌────────────┐  │
│  │  GeminiServiceV2    │  │ UserProgressSync │  │ Firestore  │  │
│  │  (Central AI Hub)   │  │    Service       │  │  Manager   │  │
│  ├─────────────────────┤  ├──────────────────┤  ├────────────┤  │
│  │ GeminiChatService   │  │ StageGenerator   │  │ Analytics  │  │
│  │ GeminiQuizService   │  │ StageContent     │  │  Service   │  │
│  │ GeminiReportService │  │ FlashcardService │  │ DailyTip   │  │
│  │ GeminiChallenge     │  │ InterviewPrep    │  │  Service   │  │
│  │   Service           │  │ ProjectSuggest   │  │ NetworkMod │  │
│  ├─────────────────────┤  │   ion            │  │ NetworkUtil│  │
│  │ PreReleaseEvent     │  ├──────────────────┤  │ InputSanit │  │
│  │   Service (953 LOC) │  │ SecurePrefsMan   │  │   izer     │  │
│  └─────────────────────┘  └──────────────────┘  └────────────┘  │
└───────────────────────────┬──────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│                         DATA LAYER                               │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              Firebase (Single Source of Truth)            │    │
│  │                                                          │    │
│  │  ┌────────────────┐  ┌──────────────┐  ┌──────────────┐ │    │
│  │  │   Firestore    │  │  Realtime DB │  │  Firebase    │ │    │
│  │  │  (User Data,   │  │ (Challenges, │  │    Auth      │ │    │
│  │  │ Stages, Content│  │  Rankings,   │  │ (Google +    │ │    │
│  │  │  Progress)     │  │  Events)     │  │  Email Auth) │ │    │
│  │  └────────────────┘  └──────────────┘  └──────────────┘ │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
User Action → Activity/Fragment → ViewModel → Service → Firebase/Gemini API
                                                   ↓
User sees result ← UI Update ← LiveData ← Callback/Coroutine Result
```

---

## 🔧 Tech Stack

### Core

| Category | Technology | Version |
|----------|-----------|---------|
| **Language** | Kotlin | 2.2.20 |
| **Build System** | Android Gradle Plugin | 9.0.1 |
| **Min SDK** | Android 8.0 (Oreo) | API 26 |
| **Target SDK** | Android 16 | API 36 |
| **JVM Target** | Java 11 | — |

### UI & Design

| Component | Technology |
|-----------|-----------|
| **Design System** | Material Design 3 (v1.13.0) |
| **Layout Binding** | ViewBinding + DataBinding |
| **Navigation** | Jetpack Navigation (v2.9.4) |
| **Lists** | RecyclerView (v1.4.0) |
| **Layouts** | ConstraintLayout (v2.2.1) |
| **Cards** | CardView (v1.0.0) |
| **Pull-to-Refresh** | SwipeRefreshLayout (v1.1.0) |
| **Custom Views** | GamePathView, ParticleBackgroundView, CircularProgressView |

### Backend & Cloud

| Service | Technology |
|---------|-----------|
| **Cloud Platform** | Firebase BOM (v34.3.0) |
| **Authentication** | Firebase Auth + Google Sign-In (v21.4.0) |
| **Main Database** | Cloud Firestore (user data, stages, content) |
| **Realtime Database** | Firebase Realtime DB (challenges, rankings, events) |
| **AI Engine** | Google Gemini 2.5 Flash API |

### Networking & Async

| Component | Technology |
|-----------|-----------|
| **HTTP Client** | OkHttp (v4.12.0) |
| **JSON Parsing** | Gson (v2.10.1) |
| **Async Operations** | Kotlin Coroutines (v1.7.3) |
| **Coroutine Integrations** | coroutines-android, coroutines-play-services |
| **Lifecycle** | LiveData + ViewModel (v2.9.4) |

### Security

| Feature | Implementation |
|---------|---------------|
| **Credential Storage** | EncryptedSharedPreferences (v1.1.0-alpha06) |
| **API Key Protection** | BuildConfig injection from `local.properties` |
| **Certificate Pinning** | OkHttp CertificatePinner (release builds only) |
| **Input Sanitization** | Custom InputSanitizer (prompt injection prevention) |
| **Code Obfuscation** | R8/ProGuard enabled for release builds |
| **Network Validation** | Pre-flight connectivity checks via NetworkUtils |

---

## 📁 Project Structure

```
Embedded_Systems_Career_Guide/
│
├── 📱 app/src/main/java/com/example/embeddedsystemscareerguide/
│   │
│   ├── MainActivity.kt                    # Navigation host (103 lines)
│   ├── AppConstants.kt                    # App-wide constants (128 lines)
│   │
│   ├── 📦 models/                         # Data models (4 files)
│   │   ├── LearningStage.kt              #   Stage, UserProgress, StageProgress (83 lines)
│   │   ├── Question.kt                   #   Quiz question model (7 lines)
│   │   ├── AssessmentReport.kt           #   Assessment report model (19 lines)
│   │   └── challenge/
│   │       └── ChallengeModels.kt        #   17 data classes for challenges (340 lines)
│   │
│   ├── ⚙️ services/                       # Business logic (19 files)
│   │   ├── GeminiServiceV2.kt            #   Central AI hub — all prompts (683 lines)
│   │   ├── GeminiChallengeService.kt     #   Challenge evaluation engine (1,294 lines)
│   │   ├── GeminiReportService.kt        #   Assessment report generation
│   │   ├── GeminiQuizService.kt          #   Dynamic quiz generation (488 lines)
│   │   ├── GeminiChatService.kt          #   Context-aware AI chat (212 lines)
│   │   ├── StageContentService.kt        #   4-part content generation (1,616 lines)
│   │   ├── StageGeneratorService.kt      #   Personalized stage creation
│   │   ├── PreReleaseEventService.kt     #   Competition event management (953 lines)
│   │   ├── UserProgressSyncService.kt    #   Cloud progress synchronization
│   │   ├── FirestoreManager.kt           #   Firestore CRUD operations
│   │   ├── FlashcardService.kt           #   Flashcard generation (265 lines)
│   │   ├── InterviewPrepService.kt       #   Interview question generation (365 lines)
│   │   ├── ProjectSuggestionService.kt   #   Project ideas generator (337 lines)
│   │   ├── DailyTipService.kt            #   Daily learning tips (244 lines)
│   │   ├── AnalyticsService.kt           #   Learning analytics (411 lines)
│   │   ├── NetworkModule.kt              #   Centralized OkHttp config (67 lines)
│   │   ├── NetworkUtils.kt               #   Connectivity checks (79 lines)
│   │   ├── InputSanitizer.kt             #   Prompt injection prevention (64 lines)
│   │   └── SecurePrefsManager.kt         #   Encrypted preferences (98 lines)
│   │
│   └── 🎨 ui/                            # UI components (~30 files)
│       ├── home/                          #   Dashboard (HomeFragment + ViewModel)
│       ├── learningpath/                  #   Gamified stage map (9 files)
│       ├── content/                       #   Kindle-style reading
│       ├── auth/                          #   Login (894L) + Register (351L)
│       ├── assessment/                    #   Assessment + Report viewer
│       ├── chat/                          #   AI tutor chat
│       ├── challenge/                     #   6 challenge activities
│       ├── quiz/                          #   Quiz activity (306 lines)
│       ├── practice/                      #   Practice mode
│       ├── profile/                       #   User profile
│       ├── settings/                      #   App settings
│       ├── introduction/                  #   First-run introduction
│       └── custom/                        #   Custom views (particles, paths)
│
├── 📝 context/                            # Source code documentation (35 files)
│   ├── services/                          #   19 service documentation files
│   ├── models/                            #   4 model documentation files
│   └── ui/                                #   10 UI documentation files
│
├── 📚 docs/                               # Project documentation (25 files)
│   ├── README.md                          #   This file
│   ├── architecture/                      #   System design, data flow, navigation
│   ├── api/                               #   Gemini API & Firebase docs
│   ├── components/                        #   Component-level documentation
│   └── guides/                            #   Setup & deployment guides
│
├── 🔒 database.rules.json                # Firebase Realtime DB security rules
├── 🔒 firestore.rules                    # Firestore security rules
└── ⚙️ gradle/                             # Build configuration
    └── libs.versions.toml                 #   Version catalog
```

**Codebase Stats:**
- **61 Kotlin source files** across models, services, and UI
- **35 documentation files** covering every source file
- **~15,000+ lines of Kotlin** — production-quality, fully documented

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Minimum |
|-------------|---------|
| **Android Studio** | Ladybug (2024.2+) or newer |
| **JDK** | 11 or higher |
| **Android SDK** | API 26+ (Android 8.0 Oreo) |
| **Google Account** | For Firebase + Gemini API access |

### Step-by-Step Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/khashyap0803/Embedded_Systems_Career_Guide.git
cd Embedded_Systems_Career_Guide
```

#### 2. Get a Gemini API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Click **"Create API Key"**
3. Copy the generated key

#### 3. Configure `local.properties`

Open (or create) the `local.properties` file in the project root and add:

```properties
# Android SDK path (auto-generated by Android Studio)
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# Gemini API Key — required for AI features
GEMINI_API_KEY=your_gemini_api_key_here
```

> ⚠️ **Important:** Never commit `local.properties` to version control. It's already in `.gitignore`.

#### 4. Set Up Firebase

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project (or use an existing one)
3. Add an Android app with package name: `com.example.embeddedsystemscareerguide`
4. Download `google-services.json` and place it in the `app/` directory
5. Enable the following Firebase services:
   - **Authentication** → Email/Password + Google Sign-In
   - **Cloud Firestore** → Create database in production mode
   - **Realtime Database** → Create database (for challenges)

#### 5. Configure SHA-1 for Google Sign-In

```bash
# Get your debug SHA-1 fingerprint
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Add the SHA-1 fingerprint to your Firebase project settings → Android app.

#### 6. Deploy Security Rules

Upload the provided security rules to Firebase:

- **Firestore Rules:** Copy contents of `firestore.rules` to Firebase Console → Firestore → Rules
- **Realtime DB Rules:** Copy contents of `database.rules.json` to Firebase Console → Realtime Database → Rules

#### 7. Build & Run

```bash
# Open in Android Studio and sync Gradle
# OR build from terminal:
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

---

## 🔥 Firebase Setup

### Database Architecture

The app uses **two Firebase databases** for different purposes:

| Database | Purpose | Why |
|----------|---------|-----|
| **Cloud Firestore** | User profiles, learning stages, content, progress, quiz history | Flexible queries, offline support, scalable |
| **Realtime Database** | Challenge events, live rankings, participant status | Real-time sync, low latency, ideal for live competitions |

### Firestore Collections

```
users/{userId}/
  ├── profile                    # User profile data
  ├── assessment_results         # Assessment reports
  ├── personalized_stages/       # AI-generated learning path
  │   └── {stageId}/
  │       ├── content/           # Cached stage content
  │       ├── flashcards/        # Stage flashcards
  │       └── quiz_history/      # Quiz attempts
  ├── progress/                  # Overall learning progress
  ├── chat_history/              # AI chat conversations
  ├── projects/                  # Project tracking
  └── interview_prep/            # Interview practice history

cached_content/                  # Shared across all users
  ├── stages/                    # Reusable content templates
  ├── flashcard_templates/       # Common flashcard sets
  └── quiz_pools/                # Question banks
```

### Realtime Database Structure

```
preReleaseEvent/
  ├── config/                    # Event configuration
  │   ├── eventActive            # Boolean: is event live?
  │   ├── registrationOpen       # Boolean: can users register?
  │   └── extraTimeMinutes       # Admin-granted extra time
  ├── participants/{odId}/       # Participant data
  │   ├── profile/               # Name, roll number, email
  │   ├── status/                # Challenge progress, warnings
  │   └── challenges/            # Submission data + scores
  └── rankings/{odId}/           # Universal ranking data
```

### Storage Estimates

| Data Type | Per User | 50 Users |
|-----------|----------|----------|
| User profile | 2 KB | 100 KB |
| Assessment data | 10 KB | 500 KB |
| Personalized stages | 100 KB | 5 MB |
| Stage content (cached) | 500 KB | 25 MB |
| Flashcards | 200 KB | 10 MB |
| Quiz history | 50 KB | 2.5 MB |
| Progress data | 20 KB | 1 MB |
| Chat history | 100 KB | 5 MB |
| **Total** | **~1 MB** | **~50 MB** |

> ✅ Well within Firebase's free tier limits (1 GiB Firestore, 500 MB Realtime DB)

---

## 🤖 Gemini AI Integration

### How AI Powers the App

The app makes **direct REST API calls** to Google's Gemini 2.5 Flash model using OkHttp. All AI logic is centralized in `GeminiServiceV2.kt` (683 lines) with specialized services for specific features.

### AI Service Architecture

| Service | Purpose | Key Functions |
|---------|---------|---------------|
| `GeminiServiceV2` | Central AI hub | `personalizedStages()`, `stageContent()`, `flashcards()`, `quizWithExplanations()`, `contextAwareChat()`, `progressAnalytics()`, `codeReview()` |
| `GeminiChallengeService` | Challenge evaluation | `evaluateChallenge1()`, `evaluateChallenge2()`, `evaluateChallenge3()` with 6-category rubric |
| `GeminiReportService` | Assessment reports | Generates detailed HTML reports with per-topic analysis |
| `GeminiQuizService` | Quiz generation | Dynamic question generation with explanations |
| `GeminiChatService` | Chat responses | Context-aware responses considering user's learning history |

### AI Features at a Glance

| Feature | Input | Output |
|---------|-------|--------|
| **Stage Generation** | Assessment weak/strong areas | 30–50 personalized learning stages |
| **Content Generation** | Stage name + topics | Theory, key points, code, tips (4 API calls) |
| **Quiz Generation** | Stage topics | 5 MCQs with detailed explanations |
| **Flashcard Generation** | Stage topics | 15 Q&A flashcards |
| **Chat Response** | User message + context | Contextual embedded systems tutoring |
| **Challenge Evaluation** | Submitted code/answers | 6-category scored rubric (0–10 each) |
| **Interview Questions** | Completed topics + difficulty | Technical interview Q&A pairs |
| **Project Suggestions** | Completed stages + skill level | Step-by-step project guides |
| **Daily Tips** | Current stage + history | Weekly batch of learning tips |
| **Code Review** | User's code | Line-by-line analysis + suggestions |

### Server-Side Enforcement

The challenge evaluation system includes **server-side enforcement rules** to ensure consistent scoring:

- Empty/placeholder answers → automatic 0 score
- Single-word answers → capped at 2/10
- Very short answers (< 20 chars) → capped at 4/10
- These rules override AI responses to prevent inconsistency

### Token Usage & Cost Estimates

| Feature | Monthly Tokens (per user) | Estimated Cost |
|---------|--------------------------|----------------|
| Personalized stages | 50,000 | ₹11.25 |
| 50 stage contents | 500,000 | ₹112.50 |
| 50 flashcard sets | 250,000 | ₹56.25 |
| Enhanced quizzes | 100,000 | ₹22.50 |
| Chat (100 messages) | 80,000 | ₹18.00 |
| Analytics (4 reports) | 32,000 | ₹7.20 |
| Interview prep | 50,000 | ₹11.25 |
| Projects | 20,000 | ₹4.50 |
| Daily tips | 15,000 | ₹3.38 |
| Code review | 50,000 | ₹11.25 |
| **Total per user** | **~1.15M** | **~₹258/month** |

---

## 🔒 Security

### Security Measures Implemented

| Layer | Protection | Implementation |
|-------|-----------|----------------|
| **API Keys** | Not hardcoded in source | Injected via `BuildConfig` from `local.properties` |
| **User Credentials** | Encrypted at rest | `EncryptedSharedPreferences` with AES-256-GCM |
| **Network** | Certificate pinning | OkHttp `CertificatePinner` for `googleapis.com` (release only) |
| **User Input** | Prompt injection prevention | `InputSanitizer` strips system/assistant prompts, markdown injection |
| **HTML Display** | XSS prevention | HTML entity encoding via `sanitizeForHtml()` |
| **Username** | Validation & sanitization | Lowercase, alphanumeric only, max 20 chars |
| **Release Builds** | Code obfuscation | R8/ProGuard with `minifyEnabled = true` |
| **Firebase** | Security rules | User-scoped read/write rules for both Firestore and Realtime DB |
| **Anti-Cheat** | Challenge integrity | Warning system, auto-termination, state monitoring |
| **Authentication** | Multi-provider | Firebase Auth with Email/Password + Google Sign-In |

### Legacy Prefs Migration

The app includes automatic migration from unencrypted `SharedPreferences` to `EncryptedSharedPreferences` — existing users are seamlessly upgraded on first launch after update.

---

## 📚 Documentation

### Source Code Documentation (`context/`)

Every single `.kt` source file has a corresponding `.md` documentation file in the `context/` directory. Each doc includes:

- ✅ Exact line counts (verified against source)
- ✅ Complete function lists with signatures
- ✅ Class structures and data models
- ✅ Design decisions and architecture notes
- ✅ Bug fix history and changelog

| Category | Files | Documentation Directory |
|----------|-------|------------------------|
| Services | 19 | `context/services/` |
| Models | 4 | `context/models/` |
| UI | ~30 (10 grouped docs) | `context/ui/` |
| Root | 2 | `context/` |

### Project Documentation (`docs/`)

| Document | Path | Description |
|----------|------|-------------|
| **This README** | `docs/README.md` | Complete project overview |
| **Architecture** | `docs/architecture/ARCHITECTURE.md` | System design and patterns |
| **Navigation Flow** | `docs/architecture/NAVIGATION_FLOW.md` | Screen-to-screen navigation |
| **Data Flow** | `docs/architecture/DATA_FLOW.md` | Cloud data architecture |
| **Gemini API Guide** | `docs/api/GEMINI_API.md` | AI integration details |
| **Firebase Guide** | `docs/api/FIREBASE.md` | Cloud setup and configuration |
| **Setup Guide** | `docs/guides/SETUP.md` | Development environment setup |
| **Deployment Guide** | `docs/guides/DEPLOYMENT.md` | Build and release process |
| **V2 Master Plan** | `docs/V2_MASTER_PLAN.md` | Feature planning and roadmap |
| **API Cost Analysis** | `docs/API_COST_ANALYSIS.md` | Token usage and billing estimates |
| **Challenge System** | `docs/PRE_RELEASE_EVENT_CHALLENGE.md` | Competition system documentation |
| **Challenge Audit** | `docs/AUDIT_REPORT_CHALLENGE_SYSTEM.md` | Bug fix audit report |

---

## 🤝 Contributing

This project is currently developed for educational purposes. If you'd like to contribute:

1. **Fork** this repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** your changes: `git commit -m 'Add amazing feature'`
4. **Push** to the branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Code Style

- Kotlin with standard Android conventions
- MVVM architecture pattern
- Service classes for business logic
- Coroutines for all async operations
- Firebase callbacks bridged via `suspendCancellableCoroutine` and `callbackFlow`

---

## 📄 License

This project is developed for **educational purposes**.

---

<p align="center">
  Made with ❤️ for the embedded systems community
</p>

<p align="center">
  <a href="https://github.com/khashyap0803">
    <img src="https://img.shields.io/badge/GitHub-khashyap0803-181717?logo=github" alt="GitHub">
  </a>
</p>
