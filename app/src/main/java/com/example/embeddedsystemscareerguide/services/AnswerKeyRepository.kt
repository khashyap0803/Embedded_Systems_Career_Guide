package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.models.AnswerKeyEntry
import com.example.embeddedsystemscareerguide.models.AssessmentAnswerKey
import com.google.gson.Gson
import java.security.MessageDigest

/**
 * Loads the precomputed assessment answer key from assets.
 *
 * The key ships as an asset rather than living in Firestore for three reasons:
 * it has to be readable before the user is authenticated, it must work offline,
 * and it is versioned together with the question set it grades. A Firestore
 * copy would have to be world-readable to serve the login flow anyway, so it
 * would buy no confidentiality over an APK asset while adding 50 document reads
 * and a network failure mode.
 *
 * Note the key is extractable by anyone who unzips the APK. That is acceptable
 * here: this is a self-assessment that feeds a learning path, not the proctored
 * exam - that lives in the Challenge module and is graded server-side.
 */
object AnswerKeyRepository {

    private const val TAG = "AnswerKeyRepository"
    private const val KEY_ASSET = "assessment_answer_key.json"
    private const val QUESTIONS_ASSET = "initial_assessment_questions.json"

    /** Parsed once per process; the asset cannot change under a running app. */
    @Volatile
    private var cached: AssessmentAnswerKey? = null

    @Volatile
    private var loadAttempted = false

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Returns the key, or null if it is absent, unparseable, or does not match
     * the current question set.
     *
     * Null is a supported outcome, not an error path to swallow: the caller
     * falls back to generating everything, which is exactly the behaviour that
     * existed before the key. Serving a model answer for a question that has
     * since been reworded would be worse than being slow.
     */
    fun load(context: Context): AssessmentAnswerKey? {
        cached?.let { return it }
        if (loadAttempted) return null

        synchronized(this) {
            cached?.let { return it }
            if (loadAttempted) return null
            loadAttempted = true

            return try {
                val assets = context.applicationContext.assets
                val raw = assets.open(KEY_ASSET).use { it.readBytes() }
                val questionBytes = assets.open(QUESTIONS_ASSET).use { it.readBytes() }

                val parsed = Gson().fromJson(
                    raw.toString(Charsets.UTF_8), AssessmentAnswerKey::class.java
                )
                if (parsed == null || parsed.entries.isEmpty()) {
                    Log.e(TAG, "Answer key parsed to no entries; falling back to generation")
                    return null
                }

                val actual = sha256(questionBytes)
                if (parsed.questionSetSha256.isBlank()) {
                    // Fail closed. The field defaults to "" precisely so a
                    // truncated asset or a dropped line is detectable, and
                    // skipping the check when it is absent would ignore the one
                    // case the default exists to catch.
                    Log.e(TAG, "Answer key declares no questionSetSha256; refusing it")
                    return null
                }
                if (!parsed.questionSetSha256.equals(actual, ignoreCase = true)) {
                    // Loud on purpose. The two assets are edited independently,
                    // and this is the only thing standing between a reworded
                    // question and a confidently wrong "correct answer".
                    Log.e(
                        TAG,
                        "Answer key is stale: built for ${parsed.questionSetSha256.take(12)}, " +
                            "questions are ${actual.take(12)}. Falling back to full generation."
                    )
                    return null
                }

                if (!parsed.reviewed) {
                    Log.w(TAG, "Answer key is model-drafted and not human-reviewed yet")
                }

                Log.d(TAG, "Loaded ${parsed.entries.size} answer key entries")
                cached = parsed
                parsed
            } catch (e: Exception) {
                // Missing asset is the normal case before the key is authored.
                Log.w(TAG, "No usable answer key (${e.javaClass.simpleName}); generating instead")
                null
            }
        }
    }

    /** Entries indexed by question id, empty when no usable key is present. */
    fun entriesById(context: Context): Map<Int, AnswerKeyEntry> =
        load(context)?.entries?.associateBy { it.id } ?: emptyMap()
}
