# Development Setup Guide

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Android Studio | Ladybug (2024.2+) |
| JDK | 11+ |
| Kotlin | 2.0.0 |
| Gradle | 8.10.2 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |

## Step 1: Clone Repository

```bash
git clone https://github.com/your-repo/Embedded_Systems_Career_Guide.git
cd Embedded_Systems_Career_Guide
```

## Step 2: AI features need no API key

The app holds no AI API key. AI requests go through `NetworkModule` to the authenticated Ollama gateway, and each carries the student's Firebase ID token as `Authorization: Bearer <token>`. Nothing goes in `local.properties` for AI; Android Studio fills in `sdk.dir` itself.

## Step 3: Configure Firebase

1. Create Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add Android app with package: `com.example.embeddedsystemscareerguide`
3. Download `google-services.json`
4. Place in `app/` directory
5. Enable Authentication (Email/Password, Google)
6. Create Firestore database

## Step 4: Add SHA-1 for Google Sign-In

```bash
# Debug SHA-1
cd android
./gradlew signingReport
```

Add SHA-1 to Firebase Console > Project Settings > Your App.

## Step 5: Build & Run

```bash
./gradlew assembleDebug
```

Or use Android Studio:
1. Open project
2. Sync Gradle
3. Run on emulator or device

## Project Structure

```
app/
├── src/main/
│   ├── java/.../embeddedsystemscareerguide/
│   │   ├── MainActivity.kt
│   │   ├── AppConstants.kt
│   │   ├── models/
│   │   ├── services/
│   │   └── ui/
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   ├── drawable/
│   │   └── navigation/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── google-services.json
```

## Common Issues

### AI features fail with "Server down" or a sign-in error
The app needs a signed-in Firebase account and a reachable AI gateway. There is no API key to set.

### Google Sign-In fails
Add correct SHA-1 fingerprint in Firebase Console.

### Firestore permission denied
Check security rules allow authenticated users.

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```
