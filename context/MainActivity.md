# MainActivity.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/MainActivity.kt`  
> **Package**: `com.example.embeddedsystemscareerguide`  
> **Size**: 3,904 bytes (103 lines)

---

## What This File Does (Simple Explanation)

This is the **main screen** of the app — the central activity that hosts all the primary fragments (Home, Learning Path, Practice, Profile) using Jetpack Navigation. It's like a frame or container: the toolbar at the top, the content area in the middle (which swaps between different screens/fragments), and a floating action button are all managed here.

When the app opens, this activity:
1. Checks if the user is logged in
2. If not → redirects to LoginActivity
3. If yes → sets up the toolbar, navigation, and floating action button

---

## Why This File Exists

Every Android app needs at least one Activity. This is the **primary Activity** that users interact with after logging in. It uses the **single Activity architecture** pattern where one Activity hosts multiple Fragments that swap in and out via the Jetpack Navigation Component.

---

## Where This File Is Used

| Context | How |
|---------|-----|
| `AndroidManifest.xml` | Declared as the launcher activity (or navigated to from LoginActivity) |
| `LoginActivity.kt` | Navigates here after successful login |
| `IntroductionActivity.kt` | Navigates here after assessment check |
| Navigation graph (`nav_graph.xml`) | Defines which fragments this activity hosts |

---

## Complete Code Walkthrough

### Lines 1-18: Imports

```kotlin
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.embeddedsystemscareerguide.databinding.ActivityMainBinding
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
```

Key imports:
- **Jetpack Navigation** (`NavHostFragment`, `AppBarConfiguration`, `setupActionBarWithNavController`) — manages fragment switching
- **ViewBinding** (`ActivityMainBinding`) — type-safe access to layout views
- **Firebase Auth** — for login state checking and logout
- **Material Design** — FAB and Snackbar components

### Lines 20-24: Class Declaration & Properties

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
```

- Extends `AppCompatActivity` — the base class for activities that use the modern ActionBar
- `appBarConfiguration` — tells the navigation system which fragments are "top-level" (no back button shown)
- `binding` — ViewBinding instance for type-safe view access

### Lines 25-65: `onCreate()` — Activity Initialization

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Check if user is logged in
    if (FirebaseAuth.getInstance().currentUser == null) {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
        return
    }
```

**Auth Gate** (Lines 28-33): The very first thing `onCreate` does is check if the user is logged in. If `FirebaseAuth.getInstance().currentUser` returns `null`, it immediately redirects to `LoginActivity` and calls `finish()` to remove this activity from the back stack. The `return` prevents the rest of `onCreate` from running.

```kotlin
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
```

**Layout Setup** (Lines 35-38): Inflates the layout using ViewBinding and sets the Material Toolbar as the ActionBar.

```kotlin
    findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener { view ->
        Snackbar.make(view, "Quick actions coming soon!", Snackbar.LENGTH_LONG)
            .setAction("OK", null)
            .setAnchorView(R.id.fab)
            .show()
    }
```

**FAB Setup** (Lines 40-45): The floating action button currently shows a "coming soon" snackbar. The `?.` safe call operator means it won't crash if the FAB doesn't exist in the layout. The `setAnchorView(R.id.fab)` positions the Snackbar above the FAB.

```kotlin
    try {
        val navHostFragment = (supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment)
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_learning,
                R.id.nav_practice,
                R.id.nav_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    } catch (e: Exception) {
        e.printStackTrace()
    }
```

**Navigation Setup** (Lines 47-64): 
1. Gets the `NavHostFragment` from the layout — this is the container where fragments swap in/out
2. Gets the `NavController` — the "driver" that manages which fragment is shown
3. Defines top-level destinations (`nav_home`, `nav_learning`, `nav_practice`, `nav_profile`) — these won't show a back arrow in the toolbar
4. Connects the toolbar to the navigation system so it automatically shows the correct title and back button
5. All wrapped in try-catch for safety

### Lines 67-71: `onCreateOptionsMenu()` — Toolbar Menu

```kotlin
override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menu.add(0, R.id.action_logout, 0, "Logout")
    return true
}
```

Adds a "Logout" option to the toolbar's overflow menu (the three dots). Created programmatically rather than from an XML menu resource.

### Lines 73-82: `onOptionsItemSelected()` — Menu Click Handler

```kotlin
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_logout -> {
            logout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
```

When the user taps "Logout" from the menu, it calls the `logout()` function.

### Lines 84-87: `onSupportNavigateUp()` — Back Button

```kotlin
override fun onSupportNavigateUp(): Boolean {
    val navController = findNavController(R.id.nav_host_fragment_content_main)
    return navController.navigateUp() || super.onSupportNavigateUp()
}
```

Handles the toolbar back button — delegates to the NavController to go back in the fragment stack.

### Lines 89-101: `logout()` — Sign Out Flow

```kotlin
private fun logout() {
    // Clear local progress data before signing out to prevent data leakage
    UserProgressSyncService(this).clearLocalProgress()
    
    // Clear user-specific prefs (including username)
    getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply()
    
    FirebaseAuth.getInstance().signOut()
    val intent = Intent(this, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
}
```

The logout flow has 4 steps:
1. **Clear local progress** — calls `UserProgressSyncService.clearLocalProgress()` to remove cached learning data (prevents data leakage to the next user)
2. **Clear preferences** — wipes `user_prefs` SharedPreferences (username, email, etc.)
3. **Firebase sign out** — signs out of Firebase Auth
4. **Navigate to login** — starts LoginActivity with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` which clears the entire activity back stack, preventing the user from pressing back to return to the main app

---

## Dependencies

| Import | Why |
|--------|-----|
| `AppCompatActivity` | Base activity class with modern ActionBar support |
| `NavHostFragment`, `NavController` | Jetpack Navigation for fragment management |
| `AppBarConfiguration` | Defines top-level navigation destinations |
| `ActivityMainBinding` | ViewBinding for the activity's layout |
| `UserProgressSyncService` | Used during logout to clear local data |
| `LoginActivity` | Navigation target for login/logout |
| `FirebaseAuth` | Authentication state management |
| `FloatingActionButton`, `Snackbar` | Material Design components |

---

## Connections to Other Files

```
LoginActivity ──(login success)──► MainActivity ──(hosts)──► HomeFragment
                                        │                     LearningPathFragment
                                        │                     PracticeFragment
                                        │                     ProfileFragment
                                        │                     ChatFragment
                                        │                     SettingsFragment
                                        │
                                   (logout)──► LoginActivity
```

**Layout Files**:
- `activity_main.xml` — main layout with DrawerLayout
- `app_bar_main.xml` — toolbar + FAB + NavHostFragment
- `content_main.xml` — NavHostFragment container

---

## Strengths

- ✅ **Auth gate** — immediately redirects unauthenticated users
- ✅ **Clean logout** — clears all local data before signing out
- ✅ **Error handling** — navigation setup wrapped in try-catch
- ✅ **Single Activity architecture** — modern Android pattern

## Weaknesses / Technical Debt

- ⚠️ **FAB is unused** — just shows "coming soon" snackbar
- ⚠️ **Menu created programmatically** — should use XML menu resource for maintainability
- ⚠️ **Hardcoded string** — `"user_prefs"` should use `PrefsKeys.PREFS_USER`
- ⚠️ **No drawer usage** — the layout has a DrawerLayout but it's not wired up
- ⚠️ **No ViewModel** — business logic (logout) is in the Activity instead of a ViewModel
