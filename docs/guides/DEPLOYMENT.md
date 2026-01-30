# Deployment Guide

## Build Types

| Type | Purpose | Signing |
|------|---------|---------|
| Debug | Development | Debug keystore |
| Release | Production | Upload keystore |

## Release Build

### Step 1: Create Keystore

```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
```

### Step 2: Configure Signing

In `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("release-key.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "release"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        proguardFiles(...)
    }
}
```

### Step 3: Build APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Step 4: Build AAB (Play Store)

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Play Store Deployment

1. Create app in Play Console
2. Upload AAB
3. Complete store listing
4. Submit for review

## Firebase Production Checklist

- [ ] Enable Firestore security rules
- [ ] Set up Firebase Analytics
- [ ] Configure Crashlytics
- [ ] Remove debug logs
- [ ] Test with release build

## ProGuard Rules

Ensure these are kept:

```proguard
-keep class com.example.embeddedsystemscareerguide.models.** { *; }
-keep class com.google.firebase.** { *; }
```

## Version Management

In `app/build.gradle.kts`:

```kotlin
versionCode = 1  // Increment for each release
versionName = "1.0.0"  // Semantic versioning
```
