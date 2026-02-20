# NetworkUtils.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/NetworkUtils.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 3,245 bytes (79 lines)

---

## What This File Does (Simple Explanation)

This is a **network connectivity checker**. Before the app makes any API call to Gemini AI, it can use this utility to check if the device actually has internet access. It provides 4 methods:

1. **`isNetworkAvailable()`** — Is there any internet connection?
2. **`isWifiConnected()`** — Is the device on WiFi?
3. **`isMobileDataConnected()`** — Is the device on mobile data?
4. **`getNetworkStatus()`** — Returns a human-readable status string ("WiFi", "Mobile Data", "Connected", "Offline")

---

## Why This File Exists

The comment says "M7 fix" — this was added to prevent the app from making API calls when there's no internet, which would result in confusing timeout errors. By checking connectivity first, the app can show a proper "No internet connection" message.

---

## Where This File Is Used

| File | Method Used | Purpose |
|------|-------------|---------|
| `GeminiChatService.kt` | `isNetworkAvailable()` | Check before sending chat messages |
| `GeminiQuizService.kt` | `isNetworkAvailable()` | Check before generating quiz questions |
| `GeminiReportService.kt` | `isNetworkAvailable()` | Check before generating assessment reports |
| `UserProgressSyncService.kt` | `isNetworkAvailable()` | Check before syncing progress to Firestore |
| `Challenge1/2/3Activity.kt` | `isNetworkAvailable()` | Check before submitting challenge answers |
| `HomeFragment.kt` | `getNetworkStatus()` | Display network status indicator |

---

## Complete Code Walkthrough

### Lines 17-31: `isNetworkAvailable()` — Main Connectivity Check

```kotlin
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnectedOrConnecting == true
    }
}
```

**How it works:**
- Gets the `ConnectivityManager` system service
- **Android 6.0+ (API 23+)**: Uses the modern `NetworkCapabilities` API
  - Checks for `NET_CAPABILITY_INTERNET` — device has an internet route
  - AND checks for `NET_CAPABILITY_VALIDATED` — the connection actually works (can reach servers)
- **Below Android 6.0**: Falls back to deprecated `activeNetworkInfo` API
  - `@Suppress("DEPRECATION")` silences the compiler warning

### Lines 36-48: `isWifiConnected()` — WiFi-Specific Check

Same pattern as `isNetworkAvailable()` but checks for `TRANSPORT_WIFI` transport type.

### Lines 53-65: `isMobileDataConnected()` — Mobile Data Check

Same pattern but checks for `TRANSPORT_CELLULAR` transport type.

### Lines 70-77: `getNetworkStatus()` — Human-Readable Status

```kotlin
fun getNetworkStatus(context: Context): String {
    return when {
        isWifiConnected(context) -> "WiFi"
        isMobileDataConnected(context) -> "Mobile Data"
        isNetworkAvailable(context) -> "Connected"
        else -> "Offline"
    }
}
```

Returns one of 4 strings. Checks WiFi first (most common), then mobile data, then generic connectivity, then falls back to "Offline".

---

## Dependencies

| Import | Why |
|--------|-----|
| `Context` | Access to system services |
| `ConnectivityManager` | Android's network state manager |
| `NetworkCapabilities` | Modern API for checking connection type |
| `Build` | Check Android SDK version for API compatibility |

---

## Strengths

- ✅ Handles both modern and legacy Android APIs
- ✅ Checks for validated internet (not just connected)
- ✅ Simple, focused utility

## Weaknesses / Technical Debt

- ⚠️ No callback/listener for network changes — only checks current state
- ⚠️ Code duplication — the 3 check methods repeat the ConnectivityManager setup
- ⚠️ `getNetworkStatus()` makes 3 separate system service calls — inefficient
