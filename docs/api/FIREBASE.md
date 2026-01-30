# Firebase Integration

## Services Used

| Service | Purpose |
|---------|---------|
| Firebase Auth | User authentication |
| Cloud Firestore | Data storage |

## Authentication

### Supported Methods

1. **Email/Password** - Traditional registration
2. **Google Sign-In** - One-tap login

### Setup

1. Enable in Firebase Console
2. Add SHA-1 fingerprint for Google Sign-In
3. Download `google-services.json` to `app/`

### Code Example

```kotlin
// Sign in
auth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener { result ->
        val user = result.user
    }

// Google Sign-In
val credential = GoogleAuthProvider.getCredential(idToken, null)
auth.signInWithCredential(credential)
```

## Firestore Structure

```
firestore/
├── users/
│   └── {username}/
│       ├── profile (document)
│       │   ├── email: string
│       │   ├── firebaseUid: string
│       │   └── createdAt: timestamp
│       └── data/
│           ├── progress (document)
│           │   ├── totalXP: number
│           │   ├── currentStage: number
│           │   ├── streak: number
│           │   ├── bestStreak: number
│           │   ├── lastVisitDate: string
│           │   ├── completedStages: array
│           │   └── stageStars: map
│           └── report (document)
│               ├── htmlContent: string
│               ├── timestamp: number
│               └── userName: string
```

## Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only access their own data
    match /users/{username}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Offline Support

Firestore persistence is enabled by default, but app currently requires network for:
- Progress sync
- Quiz generation
- Chat responses

## Indexes

No custom indexes required - all queries are simple document reads/writes.

## Cost Optimization

1. **Minimize writes** - Only save on actual changes
2. **Use merge** - Partial document updates
3. **Batch reads** - Load once per screen
