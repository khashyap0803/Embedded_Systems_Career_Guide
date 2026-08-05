package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Centralized network configuration for the Ollama LLM API.
 *
 * All AI requests route through an authenticated gateway that fronts the Ollama
 * server. The gateway proxies the same /api/generate and /api/chat endpoints with
 * the same request and response JSON - only what sits in front of Ollama changed.
 *
 * Authentication: every request carries `Authorization: Bearer <Firebase ID token>`,
 * attached by [authInterceptor]. The previous deployment was a plain ngrok tunnel
 * that published Ollama's entire API unauthenticated to anyone who found the URL;
 * it has been shut down. The gateway answers 401 for a missing, invalid, or expired
 * token, 403 for a forbidden endpoint, and 429 when a single Firebase uid exceeds
 * its rate limit.
 */
object NetworkModule {

    private const val TAG = "NetworkModule"

    // Authenticated gateway in front of the Ollama server, reached over the
    // operator's DuckDNS hostname (still bypasses CGNAT, but no longer depends on
    // an ngrok account or its interstitial). Everything under /llm is proxied to
    // Ollama untouched, so the path suffixes below are Ollama's own. This URL is
    // inert without a token - the gateway, not the app, is what keeps the model
    // off the open internet.
    private const val OLLAMA_BASE_URL = "https://katarapuhome.duckdns.org/llm"

    // Default fine-tuned model. Must match an Ollama model name registered on the
    // server this app talks to (see D:\Data\es-training\Modelfile_Q6_fixed and
    // start-server.bat/.vbs). "es-guide-q6" previously named the model but nothing
    // enforced that the Ollama tag actually stayed in sync with it - if the server
    // operator re-tags or replaces the model under that name, every AI feature in
    // the app 404s with no indication why. Renamed to something descriptive of
    // what it actually is.
    const val DEFAULT_MODEL = "es-career-guide-14b"

    private const val HEADER_AUTHORIZATION = "Authorization"
    private const val HTTP_UNAUTHORIZED = 401

    /**
     * Ceiling on one blocking ID-token fetch.
     *
     * Every AI call site enqueues rather than executes, so this interceptor already
     * runs on an OkHttp dispatcher thread and blocking here is safe. An unbounded
     * wait would not be: a stalled token fetch would outlive the client's own read
     * timeout and strand the call with nothing to time it out, so the fetch is
     * bounded well inside the shortest of them.
     */
    private const val TOKEN_TIMEOUT_SECONDS = 10L

    /**
     * Returns the signed-in user's Firebase ID token, or null if nobody is signed
     * in or the fetch fails. The token itself is never logged or returned in any
     * message - only whether the attempt succeeded.
     *
     * Only the three exceptions `Tasks.await` actually declares are caught. A
     * blanket `catch (e: Exception)` would also swallow CancellationException,
     * which must keep propagating so that a user backing out of a screen is never
     * mistaken for a failed request.
     */
    private fun fetchIdToken(forceRefresh: Boolean): String? {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Signed out. Proceed unauthenticated and let the gateway answer 401 -
            // throwing here would turn "not signed in" into a crash on a code path
            // the caller only expects HTTP failures from.
            Log.w(TAG, "No signed-in user; AI request will go out unauthenticated")
            return null
        }
        return try {
            val result = Tasks.await(
                user.getIdToken(forceRefresh),
                TOKEN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
            result?.token
        } catch (e: TimeoutException) {
            Log.w(TAG, "ID token fetch timed out after ${TOKEN_TIMEOUT_SECONDS}s")
            null
        } catch (e: ExecutionException) {
            // Log the failure's class only. The message could carry request detail.
            Log.w(TAG, "ID token fetch failed: ${e.cause?.javaClass?.simpleName}")
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "ID token fetch interrupted")
            null
        }
    }

    private fun Request.withBearerToken(token: String?): Request =
        if (token.isNullOrEmpty()) this
        else newBuilder().header(HEADER_AUTHORIZATION, "Bearer $token").build()

    /**
     * Attaches the Firebase ID token to every request and recovers from expiry.
     *
     * ID tokens last about an hour, so any session longer than that meets a 401
     * mid-use. That is expiry, not an outage, and must not surface as "server
     * down" - so a 401 buys exactly one retry with a force-refreshed token. If
     * that retry is also refused, its response is returned as-is: a genuinely
     * unauthorized user has to surface as 401 rather than spin.
     */
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val response = chain.proceed(original.withBearerToken(fetchIdToken(forceRefresh = false)))

        if (response.code != HTTP_UNAUTHORIZED) return@Interceptor response

        val refreshedToken = fetchIdToken(forceRefresh = true)
        if (refreshedToken.isNullOrEmpty()) {
            // Nothing to retry with (signed out, or the refresh itself failed), so
            // hand back the 401 the request already earned. Deliberately NOT closed
            // - the caller still has to read this body.
            return@Interceptor response
        }

        Log.d(TAG, "Token rejected; retrying once with a refreshed token")
        response.close()
        chain.proceed(original.withBearerToken(refreshedToken))
    }

    /**
     * Standard OkHttpClient for chat and quiz services
     * 30s connect, 120s read (local LLM can be slow on first load), 30s write
     */
    val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Long-timeout OkHttpClient for report generation and stage content.
     *
     * The read timeout was 600s. Callers chain several of these calls and each
     * retries up to three times, so a half-open connection - routine on a
     * tunnelled endpoint - could keep a single screen "loading" for hours.
     * 180s is well past a healthy generation on the current model while still
     * failing inside a span someone might actually wait through, and the
     * retry ladder now aborts as soon as the caller is cancelled.
     */
    val longTimeoutClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get the Ollama API URL for content generation
     */
    fun getOllamaGenerateUrl(): String {
        return "$OLLAMA_BASE_URL/api/generate"
    }

    /**
     * Get the Ollama API URL for chat (multi-turn conversations)
     */
    fun getOllamaChatUrl(): String {
        return "$OLLAMA_BASE_URL/api/chat"
    }

    /**
     * Server down error message shown to users
     */
    const val SERVER_DOWN_MESSAGE = "Server down. Try again later or contact the service provider: 9032827339"

    /**
     * Shown when the gateway rate-limits this user (HTTP 429).
     *
     * Distinct from [SERVER_DOWN_MESSAGE] on purpose: nothing is down, the limit is
     * per-account and it clears on its own, so telling the student to call the
     * service provider would send them chasing a fault that does not exist.
     */
    const val RATE_LIMITED_MESSAGE = "Too many AI requests in a short time. Please wait a minute and try again."
}
