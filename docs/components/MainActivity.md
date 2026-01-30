# MainActivity.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/MainActivity.kt`

## Purpose

**Main navigation host** for the application. Sets up the bottom navigation and hosts all main fragments.

## Functionality

### Navigation Setup

```kotlin
val navController = findNavController(R.id.nav_host_fragment)
NavigationUI.setupWithNavController(binding.bottomNavView, navController)
```

### Navigation Destinations

| Destination | Fragment |
|-------------|----------|
| Home | HomeFragment |
| Learning | LearningPathFragment |
| Chat | ChatFragment |
| Practice | PracticeFragment |
| Profile | ProfileFragment |

### Drawer Menu

Uses `DrawerLayout` with `NavigationView` for additional options:
- Settings
- Help
- About
- Logout

## Layout

```xml
<DrawerLayout>
  <CoordinatorLayout>
    <AppBarLayout>
      <Toolbar />
    </AppBarLayout>
    <NavHostFragment />
    <BottomNavigationView />
  </CoordinatorLayout>
  <NavigationView (drawer) />
</DrawerLayout>
```

## Why It's Important

1. **Central Navigation**: All fragments hosted here
2. **Consistent UI**: Shared toolbar and navigation
3. **State Management**: Handles back stack

## Strengths

- ✅ Clean navigation setup
- ✅ Material Design patterns
- ✅ Proper back handling

## Weaknesses

- ⚠️ Drawer rarely used
- ⚠️ Could use Navigation Compose

---

# AppConstants.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/AppConstants.kt`

## Purpose

Centralized **constants** for the entire application.

## Constants

```kotlin
object AppConstants {
    const val TOTAL_LEARNING_STAGES = 16
    const val XP_PER_STAGE = 50
    const val MAX_STARS = 3
    
    // Firestore paths
    const val USERS_COLLECTION = "users"
    const val PROGRESS_DOCUMENT = "progress"
    const val REPORT_DOCUMENT = "report"
    
    // SharedPreferences names
    const val PREFS_USER = "user_prefs"
    const val PREFS_LEARNING = "learning_progress"
}
```

## Usage

```kotlin
if (completedStages >= AppConstants.TOTAL_LEARNING_STAGES) {
    showCompletionBadge()
}
```

## Strengths

- ✅ Single source for magic numbers
- ✅ Easy configuration changes
- ✅ Clear naming

## Weaknesses

- ⚠️ Some constants still hardcoded elsewhere
- ⚠️ No remote config support
