# HomeFragment.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/ui/home/HomeFragment.kt`

## Purpose

The **main dashboard** and entry point of the app. Displays user progress summary, quick actions, streak information, and daily tips.

## Functionality

### UI Components

| Component | Description |
|-----------|-------------|
| Welcome Card | Personalized greeting with time of day |
| Progress Card | XP, level, streak stats |
| Study Streak | Visual week indicator |
| Quick Actions | Navigation to main features |
| Daily Insight | Rotating motivational tips |

### Pull-to-Refresh

```kotlin
binding.swipeRefreshLayout.setOnRefreshListener {
    loadProgressFromCloud()
}
```

### Cloud Data Loading

```kotlin
private fun loadProgressFromCloud() {
    lifecycleScope.launch {
        val progress = progressSyncService.loadProgressFromCloud()
        updateProgressDashboard(progress)
        updateStudyStreak(progress.streak)
    }
}
```

## Why It's Important

1. **First Impression**: Users see this first after login
2. **Progress Overview**: Quick view of all metrics
3. **Navigation Hub**: Access to all features
4. **Cloud Sync**: Pull-to-refresh for data sync

## Where Data Comes From

| Data | Source |
|------|--------|
| XP, Streak | Firestore via `UserProgressSyncService` |
| Username | SharedPreferences (login session) |
| Assessment Status | Firestore document existence |

## Strengths

- ✅ Cloud-only data loading
- ✅ Pull-to-refresh support
- ✅ Beautiful animations
- ✅ Clear progress visualization

## Weaknesses

- ⚠️ Loads data twice on resume
- ⚠️ No skeleton loading states
- ⚠️ Hard-coded stage count (16)

## Potential Improvements

1. **Add loading skeletons** for better UX
2. **Implement data caching** to reduce API calls
3. **Add achievement notifications**
4. **Dynamic stage count** from config

---

# HomeViewModel.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/ui/home/HomeViewModel.kt`

## Purpose

ViewModel for HomeFragment. Currently minimal - most logic is in Fragment.

## Future Role

- Move data loading logic from Fragment
- Expose LiveData for UI updates
- Handle configuration changes

---

# Fragment Layout: fragment_home.xml

> **Location**: `app/src/main/res/layout/fragment_home.xml`

## Structure

```xml
<SwipeRefreshLayout>
  <ScrollView>
    <ConstraintLayout>
      <!-- Welcome Card -->
      <!-- Progress Card -->
      <!-- Streak Card -->
      <!-- Quick Actions Grid -->
      <!-- Achievements Section -->
    </ConstraintLayout>
  </ScrollView>
</SwipeRefreshLayout>
```

## Size

~46KB - One of the largest layouts due to rich UI.
