# NetworkModule.kt

> **Full Path**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/NetworkModule.kt`  
> **Package**: `com.example.embeddedsystemscareerguide.services`  
> **Size**: 2,672 bytes (67 lines)

---

## What This File Does (Simple Explanation)

This file is the **central networking configuration** for the app. It provides pre-configured HTTP clients that all the Gemini API services share. Instead of every service creating its own HTTP client (wasteful and inconsistent), they all use the clients from this file.

It provides:
1. **`standardClient`** — for normal API calls (chat, quiz) with 60-second timeouts
2. **`longTimeoutClient`** — for report generation with 5-minute timeouts
3. **`getGeminiApiUrl()`** — builds the correct Gemini API URL with the API key

It also implements **certificate pinning** for security in release builds.

---

## Why This File Exists

The comment says "M3 fix" — this was created during refactoring to centralize network configuration. Before this, each service created its own OkHttpClient, leading to:
- Wasted memory (each client has its own connection pool)
- Inconsistent timeout settings across services
- No certificate pinning (security gap)

---

## Where This File Is Used

| File | What It Uses |
|------|-------------|
| `GeminiChatService.kt` | `standardClient` for chat API calls |
| `GeminiQuizService.kt` | `standardClient` for quiz generation |
| `GeminiReportService.kt` | `longTimeoutClient` for report generation |
| `GeminiServiceV2.kt` | `standardClient` + `getGeminiApiUrl()` |
| `GeminiChallengeService.kt` | `standardClient` for challenge evaluation |

---

## Complete Code Walkthrough

### Lines 16-21: Certificate Pinning

```kotlin
private val certificatePinner = CertificatePinner.Builder()
    .add("generativelanguage.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgRaK5cHq0=") // GTS Root R1
    .add("generativelanguage.googleapis.com", "sha256/Vfd95BwDeSQo+NUYxVEEb1lmHRY3q0+E8L3bEHZYx4M=") // GTS Root R2
    .add("*.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgRaK5cHq0=")
    .add("*.googleapis.com", "sha256/Vfd95BwDeSQo+NUYxVEEb1lmHRY3q0+E8L3bEHZYx4M=")
    .build()
```

**Certificate pinning** (labeled "C1 fix") prevents man-in-the-middle attacks. It tells OkHttp: "only trust connections to `generativelanguage.googleapis.com` if the server's SSL certificate matches one of these specific Google Trust Services root certificates." Two root CAs are pinned (R1 and R2) for redundancy. Both the specific domain and wildcard `*.googleapis.com` are pinned.

### Lines 27-39: Standard Client

```kotlin
val standardClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (!BuildConfig.DEBUG) {
                certificatePinner(certificatePinner)
            }
        }
        .build()
}
```

- **`by lazy`** — the client is only created when first accessed, then cached forever (efficient)
- **Timeouts**: 30s connect, 60s read, 30s write
- **Certificate pinning**: Only enabled in release builds (`!BuildConfig.DEBUG`) — disabled in debug to allow proxy tools like Charles

### Lines 45-57: Long Timeout Client

```kotlin
val longTimeoutClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)  // 5 minutes
        .writeTimeout(90, TimeUnit.SECONDS)
        ...
}
```

Same as standard but with much longer timeouts — 90s connect, **5 minutes read**, 90s write. Needed because Gemini report generation processes 50 questions and can take several minutes.

### Lines 63-65: API URL Builder

```kotlin
fun getGeminiApiUrl(model: String = "gemini-2.5-flash"): String {
    return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
}
```

Constructs the full Gemini API URL. Default model is `gemini-2.5-flash`. The API key comes from `BuildConfig.GEMINI_API_KEY` (injected from `local.properties` at build time).

---

## Dependencies

| Import | Why |
|--------|-----|
| `BuildConfig` | Access to the GEMINI_API_KEY build config field |
| `CertificatePinner` | OkHttp's SSL certificate pinning |
| `OkHttpClient` | HTTP client for API calls |
| `TimeUnit` | For timeout configuration |

---

## Strengths

- ✅ **Connection pool reuse** — all services share the same client
- ✅ **Certificate pinning** — prevents MITM attacks in production
- ✅ **Debug-friendly** — pinning disabled in debug builds
- ✅ **Lazy initialization** — clients only created when needed
- ✅ **Centralized URL** — API endpoint defined in one place

## Weaknesses / Technical Debt

- ⚠️ Certificate hashes are hardcoded — will break if Google rotates their root CAs
- ⚠️ API key in URL query parameter — visible in logs and server access logs
- ⚠️ No request/response logging interceptor for debugging
- ⚠️ No retry interceptor at the client level
