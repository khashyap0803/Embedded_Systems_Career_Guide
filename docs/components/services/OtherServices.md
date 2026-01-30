# FirestoreManager.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/FirestoreManager.kt`

## Purpose

Central manager for all **Firebase Firestore** database operations. Provides a unified interface for reading and writing documents across all collections.

## Functionality

### Core Methods

| Method | Description |
|--------|-------------|
| `getDocument(collection, id)` | Read single document |
| `setDocument(collection, id, data)` | Write/update document |
| `queryCollection(collection, field, value)` | Query documents |
| `deleteDocument(collection, id)` | Delete document |

### Collection Structure

```
Firestore Database
├── users/
│   └── {username}/
│       └── data/
│           ├── progress
│           └── report
├── assessment_reports/ (legacy)
└── app_config/ (settings)
```

## Why It's Important

1. **Centralized Access**: Single point for all Firestore operations
2. **Error Handling**: Consistent error management
3. **Logging**: Debug logging for all operations
4. **Type Safety**: Kotlin extensions for Firestore

## Strengths

- ✅ Encapsulates Firestore complexity
- ✅ Reusable across services
- ✅ Consistent error handling
- ✅ Debug logging support

## Weaknesses

- ⚠️ Large file (~24KB)
- ⚠️ Some legacy methods unused
- ⚠️ No batch operation support

## Potential Improvements

1. **Add batch operations** for efficiency
2. **Implement caching layer** 
3. **Remove legacy methods**
4. **Add offline persistence config**

---

# StageContentService.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/StageContentService.kt`

## Purpose

Manages **learning content** for each of the 16 stages. Retrieves lesson materials, explanations, and resources for display in the learning path.

## Content Structure

```kotlin
data class LessonContent(
    val introduction: String,
    val keyPoints: List<String>,
    val examples: List<CodeExample>,
    val summary: String
)
```

## 16 Learning Stages

| Stage | Topic |
|-------|-------|
| 1 | Introduction to Embedded Systems |
| 2 | C Programming Basics |
| 3 | Microcontroller Architecture |
| 4 | GPIO and Digital I/O |
| 5 | Timers and Interrupts |
| 6 | Serial Communication (UART) |
| 7 | SPI and I2C Protocols |
| 8 | ADC and DAC |
| 9 | PWM and Motor Control |
| 10 | RTOS Fundamentals |
| 11 | Memory Management |
| 12 | Low Power Design |
| 13 | Debugging Techniques |
| 14 | Testing Strategies |
| 15 | System Integration |
| 16 | Career Preparation |

---

# NetworkModule.kt & NetworkUtils.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/`

## Purpose

Provide **OkHttp client configuration** and network utility functions for API calls.

## Key Features

- **Singleton OkHttpClient**: Shared across services
- **Timeout Configuration**: 60s connection, 120s read
- **JSON Parsing**: Gson integration
- **Error Handling**: Network error detection

## Usage

```kotlin
val client = NetworkModule.client
val response = client.newCall(request).execute()
```

---

# SecurePrefsManager.kt

## Purpose

Manages **secure storage** for sensitive data using Android EncryptedSharedPreferences.

## Stored Data

- API keys (runtime only)
- Session tokens
- Encrypted user identifiers

> [!NOTE]
> Progress data is NOT stored here - it goes to Firestore.

---

# InputSanitizer.kt

## Purpose

**Sanitizes user input** to prevent injection attacks and ensure data safety.

## Methods

| Method | Purpose |
|--------|---------|
| `sanitizeUsername()` | Remove special characters |
| `sanitizeEmail()` | Validate email format |
| `sanitizeMessage()` | Clean chat messages |

---

# AnalyticsService.kt

## Purpose

Tracks **user analytics** and learning patterns for app improvement.

## Events Tracked

- Stage completions
- Quiz attempts
- Chat interactions
- Session duration

---

# DailyTipService.kt

## Purpose

Provides **daily learning tips** displayed on the home screen.

## Features

- Rotating tip pool
- Day-of-week based selection
- Category-based tips (code, career, learning)
