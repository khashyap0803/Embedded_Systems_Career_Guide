package com.example.embeddedsystemscareerguide.services

import com.example.embeddedsystemscareerguide.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * M3 fix: Centralized network configuration
 * Provides shared OkHttpClient instances for all Gemini API services
 * Benefits: Connection pool reuse, consistent timeouts, single certificate pinning config
 */
object NetworkModule {

    // C1 fix: Certificate pinning for Google APIs using Google Trust Services root CAs
    private val certificatePinner = CertificatePinner.Builder()
        .add("generativelanguage.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgRaK5cHq0=") // GTS Root R1
        .add("generativelanguage.googleapis.com", "sha256/Vfd95BwDeSQo+NUYxVEEb1lmHRY3q0+E8L3bEHZYx4M=") // GTS Root R2
        .add("*.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgRaK5cHq0=")
        .add("*.googleapis.com", "sha256/Vfd95BwDeSQo+NUYxVEEb1lmHRY3q0+E8L3bEHZYx4M=")
        .build()

    /**
     * Standard OkHttpClient for chat and quiz services
     * 30s connect, 60s read/write timeouts
     */
    val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                // Enable certificate pinning for release builds only
                if (!BuildConfig.DEBUG) {
                    certificatePinner(certificatePinner)
                }
            }
            .build()
    }

    /**
     * Long-timeout OkHttpClient for report generation
     * 90s connect, 300s read (5 min), 90s write
     */
    val longTimeoutClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)  // 5 minutes for large responses
            .writeTimeout(90, TimeUnit.SECONDS)
            .apply {
                // Enable certificate pinning for release builds only
                if (!BuildConfig.DEBUG) {
                    certificatePinner(certificatePinner)
                }
            }
            .build()
    }

    /**
     * M6 fix: Centralized Gemini API URL construction
     * Uses BuildConfig for API key (injected from local.properties)
     */
    fun getGeminiApiUrl(model: String = "gemini-2.5-flash"): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
    }
}
