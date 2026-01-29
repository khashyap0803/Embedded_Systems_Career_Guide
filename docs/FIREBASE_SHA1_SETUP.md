# Firebase SHA-1 Configuration Guide

## Issue: DEVELOPER_ERROR in Google API Manager

The error `Unknown calling package name 'com.google.android.gms'` indicates that the app's SHA-1 fingerprint is not registered in Firebase Console.

## How to Fix

### Step 1: Get Your Debug SHA-1 Fingerprint

Open a terminal in your project directory and run:

```bash
# Windows (PowerShell)
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Windows (CMD)
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Mac/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Look for the line that says `SHA1:` and copy the fingerprint.

### Step 2: Add SHA-1 to Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **Embedded Systems Career Guide**
3. Click the **⚙️ Settings gear** icon → **Project Settings**
4. Scroll down to **Your apps** section
5. Find your Android app (`com.example.embeddedsystemscareerguide`)
6. Click **Add fingerprint**
7. Paste your SHA-1 fingerprint
8. Click **Save**

### Step 3: Download Updated google-services.json

After adding the SHA-1:
1. Click **Download google-services.json**
2. Replace the existing file in: `app/google-services.json`
3. Clean and rebuild the project

### Step 4: Verify

Run the app again. The `DEVELOPER_ERROR` should no longer appear.

---

## For Release Builds

When publishing to Play Store, you'll also need to add the **release keystore SHA-1**:

```bash
keytool -list -v -keystore path/to/your-release-key.keystore -alias your-alias
```

And if using **Google Play App Signing**, add the SHA-1 from:
Play Console → Your App → Release → Setup → App integrity → App signing key certificate

---

## Common Issues

| Issue | Solution |
|-------|----------|
| `keytool` not found | Add Java bin to PATH or use full path |
| Wrong fingerprint | Make sure you use debug for debug builds |
| Still seeing error | Clear app data and try again |
