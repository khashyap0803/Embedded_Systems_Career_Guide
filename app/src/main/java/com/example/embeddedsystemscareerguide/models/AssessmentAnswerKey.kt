package com.example.embeddedsystemscareerguide.models

import com.google.gson.annotations.SerializedName

/**
 * Precomputed grading data for the fixed 50-question assessment.
 *
 * The questions are identical for every user, so the reference answer, the
 * criteria a correct answer has to hit, and the feedback given to someone who
 * answered well are all the same for everyone. Generating them per user was
 * the bulk of the report workload and produced no per-user information.
 *
 * [questionSetSha256] binds this key to the exact `initial_assessment_questions.json`
 * it was authored against. The two files are edited independently, and serving a
 * model answer for a question that has since been reworded is a silent
 * correctness failure - so a mismatch must fall back to full generation rather
 * than being ignored.
 *
 * Every field carries an explicit default: Gson bypasses Kotlin null-safety, so
 * a truncated or hand-edited asset would otherwise put null into a declared
 * non-null property and fail far from the actual cause.
 */
data class AssessmentAnswerKey(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("questionSetSha256") val questionSetSha256: String = "",
    /** False until a human has checked the generated content. See [AnswerKeyEntry]. */
    @SerializedName("reviewed") val reviewed: Boolean = false,
    @SerializedName("entries") val entries: List<AnswerKeyEntry> = emptyList()
)

/**
 * Grading data for one question.
 *
 * The initial content is drafted by the fine-tuned model and is expected to be
 * reviewed before it is trusted - a model answer shown to a student as
 * authoritative is exactly the kind of content that must not be taken on faith.
 */
data class AnswerKeyEntry(
    @SerializedName("id") val id: Int = 0,
    /** Topics from the ES_TOPICS vocabulary the model was fine-tuned on. */
    @SerializedName("topics") val topics: List<String> = emptyList(),
    /** Authored HTML. Trusted markup - must NOT be passed through the escaper. */
    @SerializedName("modelAnswerHtml") val modelAnswerHtml: String = "",
    @SerializedName("keyPoints") val keyPoints: List<KeyPoint> = emptyList(),
    @SerializedName("correctFeedbackHtml") val correctFeedbackHtml: String = "",
    @SerializedName("misconceptions") val misconceptions: List<Misconception> = emptyList()
)

/**
 * One thing a correct answer has to convey.
 *
 * [any] holds interchangeable phrasings of the same point, stored as lowercase
 * stems so they survive suffixes ("optimis" matches "optimising"/"optimised").
 * Matching any one of them counts the point as covered.
 */
data class KeyPoint(
    /** 3 = the answer is wrong without this; 1 = a nice-to-have detail. */
    @SerializedName("weight") val weight: Int = 1,
    @SerializedName("any") val any: List<String> = emptyList()
)

/** A common wrong belief. Hitting one blocks the automatic "correct" verdict. */
data class Misconception(
    @SerializedName("any") val any: List<String> = emptyList(),
    @SerializedName("note") val note: String = ""
)
