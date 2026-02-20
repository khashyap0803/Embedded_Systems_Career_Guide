# SecurePrefsManager.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/SecurePrefsManager.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 3,506 bytes (98 lines)

---

## What This File Does (Simple Explanation)

This is a **secure storage manager** that encrypts sensitive user data (username, user ID, email) before saving it to the device. It uses Android's `EncryptedSharedPreferences` library, which automatically encrypts both the keys and values using AES-256 encryption.

If encryption fails (on some older devices), it falls back to regular unencrypted SharedPreferences to prevent the app from crashing.

---

## Why This File Exists

Normal SharedPreferences stores data as **plain text XML** on the device. A rooted phone or a backup tool can read these files. To protect sensitive user data:
- Username
- User ID
- User email

This manager encrypts them using AES-256, so even if someone accesses the file, they see encrypted gibberish instead of actual data.

---

## Where This File Is Used

| File | How It Uses This |
|------|-----------------|
| `LoginActivity.kt` | `saveUsername()`, `saveUserId()` after login |
| `HomeFragment.kt` | `getUsername()` to display greeting |
| `ProfileFragment.kt` | `getUsername()`, `getUserId()` for profile display |
| `MainActivity.kt` | `clearAll()` during logout |

---

## Complete Code Walkthrough

### Lines 19-20: Thread-Safe Singleton Cache

```kotlin
@Volatile
private var securePrefs: SharedPreferences? = null
```

The `@Volatile` annotation ensures that reads/writes to `securePrefs` are immediately visible to all threads. Combined with the double-checked locking pattern in `getSecurePrefs()`, this ensures thread safety.

### Lines 26-30: `getSecurePrefs()` — Double-Checked Locking

```kotlin
fun getSecurePrefs(context: Context): SharedPreferences {
    return securePrefs ?: synchronized(this) {
        securePrefs ?: createSecurePrefs(context).also { securePrefs = it }
    }
}
```

This is the **double-checked locking pattern**:
1. First check: if `securePrefs` is not null, return it immediately (fast path)
2. If null, enter a synchronized block (thread-safe)
3. Second check: if still null, create it and cache it

This ensures only one `EncryptedSharedPreferences` instance is ever created, even with concurrent access from multiple threads.

### Lines 32-50: `createSecurePrefs()` — Encryption Setup

```kotlin
private fun createSecurePrefs(context: Context): SharedPreferences {
    return try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create encrypted prefs, falling back to regular", e)
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }
}
```

**Encryption details:**
- **Master Key**: Created with `AES256_GCM` scheme — Android Keystore-backed, hardware-protected on supported devices
- **Key encryption**: `AES256_SIV` (Synthetic Initialization Vector) — deterministic encryption for keys, allows looking up values by key
- **Value encryption**: `AES256_GCM` (Galois/Counter Mode) — authenticated encryption providing both confidentiality and integrity

**Fallback**: If encryption fails (rare, but possible on some devices or after factory reset of Keystore), falls back to regular SharedPreferences and logs the error.

### Lines 54-72: Convenience Methods

```kotlin
fun saveUsername(context: Context, username: String)    // Save username
fun getUsername(context: Context): String?              // Read username
fun saveUserId(context: Context, userId: String)        // Save user ID
fun getUserId(context: Context): String?                // Read user ID
fun clearAll(context: Context)                          // Wipe all secure data
```

Simple wrapper methods that hide the SharedPreferences details from callers. Each method calls `getSecurePrefs()` first to ensure the encrypted instance exists.

### Lines 77-87: `migrateFromLegacyPrefs()` — Migration

```kotlin
fun migrateFromLegacyPrefs(context: Context) {
    val legacyPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val legacyUsername = legacyPrefs.getString("current_username", null)
    
    if (legacyUsername != null) {
        saveUsername(context, legacyUsername)
        legacyPrefs.edit().remove("current_username").apply()
        Log.d(TAG, "Migrated username to encrypted storage")
    }
}
```

One-time migration function that moves data from old unencrypted `user_prefs` to the new encrypted storage. After copying, it deletes the old unencrypted value.

### Lines 92-96: Keys Object

```kotlin
object Keys {
    const val CURRENT_USERNAME = "secure_current_username"
    const val USER_ID = "secure_user_id"
    const val USER_EMAIL = "secure_user_email"
}
```

Dedicated key constants prefixed with `secure_` to distinguish from legacy keys.

---

## Dependencies

| Import | Why |
|--------|-----|
| `Context` | Android context for system services |
| `SharedPreferences` | Base storage interface |
| `Log` | Error and debug logging |
| `EncryptedSharedPreferences` | AndroidX Security library for encryption |
| `MasterKey` | Android Keystore key management |

---

## Strengths

- ✅ AES-256 encryption for sensitive data
- ✅ Thread-safe singleton with double-checked locking
- ✅ Graceful fallback if encryption fails
- ✅ Migration path from legacy unencrypted storage
- ✅ Simple API with convenience methods

## Weaknesses / Technical Debt

- ⚠️ Only migrates `username` — doesn't migrate `user_id` or `user_email`
- ⚠️ `USER_EMAIL` key exists but no `saveEmail()`/`getEmail()` convenience methods
- ⚠️ Fallback to unencrypted storage silently degrades security
- ⚠️ Some parts of the app still use `PrefsKeys.CURRENT_USERNAME` (unencrypted) instead of this
