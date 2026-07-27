package com.example.embeddedsystemscareerguide.services

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized network configuration for Ollama LLM API.
 * All AI requests route through the Ngrok static domain to the local Ollama server.
 *
 * Authentication/Bypass: We use the `ngrok-skip-browser-warning` header to bypass 
 * the Ngrok free-tier interstitial page.
 * NOTE: For this to work with Ollama, the Ngrok tunnel MUST be started with the 
 * `--host-header=rewrite` flag, otherwise Ollama will reject the request with 403 Forbidden.
 */
object NetworkModule {

    private const val TAG = "NetworkModule"

    // Ollama server exposed via Ngrok's free persistent dev domain (bypasses
    // CGNAT). This domain is auto-assigned to the "khashyap" ngrok account
    // and does not change across ngrok restarts, unlike a plain
    // `ngrok http 11434` with no --domain flag. See start-server.bat /
    // start-server-silent.vbs for the matching tunnel launch command.
    private const val OLLAMA_BASE_URL = "https://landfall-quilt-passover.ngrok-free.dev"

    // Default fine-tuned model. Must match an Ollama model name registered on the
    // server this app talks to (see D:\Data\es-training\Modelfile_Q6_fixed and
    // start-server.bat/.vbs). "es-guide-q6" previously named the model but nothing
    // enforced that the Ollama tag actually stayed in sync with it - if the server
    // operator re-tags or replaces the model under that name, every AI feature in
    // the app 404s with no indication why. Renamed to something descriptive of
    // what it actually is.
    const val DEFAULT_MODEL = "es-career-guide-14b"

    /**
     * Interceptor that adds the Ngrok bypass header to every request.
     * This bypasses the free-tier interstitial page.
     */
    private val bypassInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(request)
    }

    /**
     * Standard OkHttpClient for chat and quiz services
     * 30s connect, 120s read (local LLM can be slow on first load), 30s write
     */
    val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(bypassInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Long-timeout OkHttpClient for report generation and stage content
     * 90s connect, 600s read (10 min for large responses), 90s write
     */
    val longTimeoutClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(bypassInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
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
}
