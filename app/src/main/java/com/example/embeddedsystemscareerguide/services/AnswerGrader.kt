package com.example.embeddedsystemscareerguide.services

import com.example.embeddedsystemscareerguide.models.AnswerKeyEntry

/**
 * Grades a free-text assessment answer against its precomputed key, without
 * calling the model.
 *
 * This exists to answer one question cheaply: does this answer need the LLM at
 * all? Most do not. An unanswered question needs no generation, and a clearly
 * complete answer only needs the cached feedback. Everything in between is
 * routed to the model, which is where the per-user value actually is.
 *
 * The thresholds are deliberately asymmetric. Key-point matching is a blunt
 * instrument on essay answers: a student can restate the question using the
 * right vocabulary without understanding it, and can also explain something
 * correctly in words the key does not list. Of those two errors only one hurts
 * the student - being wrongly told "correct, well done" means they never see
 * the correction, on an app they are using to build a career. Being wrongly
 * routed to the model just costs tokens. So [Verdict.CORRECT] requires high
 * coverage AND enough substance AND no misconception hit, and anything
 * ambiguous falls through to [Verdict.NEEDS_MODEL].
 */
object AnswerGrader {

    /** Coverage at or above this may skip the model - see the class note on why it is high. */
    private const val CORRECT_COVERAGE = 0.80

    /** Below this an answer is treated as absent rather than wrong. */
    private const val MIN_WORDS_ANSWERED = 4

    /** Keyword coverage alone cannot certify a one-line answer as complete. */
    private const val MIN_WORDS_CORRECT = 25

    enum class Verdict {
        /** Blank or a few words. No model call, no credit. */
        UNANSWERED,

        /** Confidently complete. Serve cached feedback and the model answer. */
        CORRECT,

        /** Anything else. The model decides, and writes the feedback. */
        NEEDS_MODEL
    }

    data class Result(
        val questionId: Int,
        val verdict: Verdict,
        /** 0.0-1.0, weighted fraction of key points matched. */
        val coverage: Double,
        /** Score out of 100 for topic aggregation. */
        val score: Int,
        val matchedMisconceptions: List<String>,
        val topics: List<String>
    )

    /**
     * Normalises for matching: lowercase, strip everything that is not a letter,
     * digit or the few symbols that carry meaning in C, then collapse runs of
     * whitespace.
     *
     * Keeping `+ # _` matters - dropping them would turn "C++" into "c" and
     * merge it with plain C, and would break identifiers like `xQueueSend_FromISR`.
     */
    private fun normalise(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9+#_\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun wordCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /**
     * Matches one accepted phrasing against a normalised answer.
     *
     * Long stems match as substrings on purpose, so "optimis" covers
     * "optimising" and "optimised". Short ones cannot: protocol pin names are
     * genuinely two or three characters - `SS` for SPI slave select, `WS` for
     * I2S word select, `SD`, `TLS` - and as substrings they hit inside ordinary
     * words. "ss" alone matches "process", "assess" and "less", which would
     * inflate coverage on an answer that never mentioned the pin and push it
     * toward a false CORRECT. Below four characters, require a word boundary.
     */
    private fun matches(haystack: String, phrase: String): Boolean {
        val needle = normalise(phrase)
        if (needle.isBlank()) return false
        if (needle.length >= 4) return haystack.contains(needle)
        return Regex("(?<![a-z0-9])${Regex.escape(needle)}(?![a-z0-9])")
            .containsMatchIn(haystack)
    }

    fun grade(entry: AnswerKeyEntry, rawAnswer: String): Result {
        val words = wordCount(rawAnswer)
        if (words < MIN_WORDS_ANSWERED) {
            return Result(entry.id, Verdict.UNANSWERED, 0.0, 0, emptyList(), entry.topics)
        }

        val haystack = normalise(rawAnswer)

        val totalWeight = entry.keyPoints.sumOf { it.weight }.coerceAtLeast(1)
        val matchedWeight = entry.keyPoints
            .filter { kp -> kp.any.any { matches(haystack, it) } }
            .sumOf { it.weight }

        val coverage = matchedWeight.toDouble() / totalWeight

        val misconceptions = entry.misconceptions
            .filter { m -> m.any.any { matches(haystack, it) } }
            .map { it.note }

        // No key points authored yet for this question - we have no basis to
        // certify anything, so let the model handle it rather than guessing.
        val gradable = entry.keyPoints.isNotEmpty()

        val verdict = when {
            !gradable -> Verdict.NEEDS_MODEL
            misconceptions.isNotEmpty() -> Verdict.NEEDS_MODEL
            coverage >= CORRECT_COVERAGE && words >= MIN_WORDS_CORRECT -> Verdict.CORRECT
            else -> Verdict.NEEDS_MODEL
        }

        // Coverage is the score for topic aggregation. For NEEDS_MODEL answers
        // this is provisional and should be replaced by the model's rating once
        // it comes back - it is a floor, not a verdict.
        return Result(
            questionId = entry.id,
            verdict = verdict,
            coverage = coverage,
            score = (coverage * 100).toInt().coerceIn(0, 100),
            matchedMisconceptions = misconceptions,
            topics = entry.topics
        )
    }

    /**
     * Aggregates per-question results into a per-topic percentage.
     *
     * This is what [StageGeneratorService] needs and has never been given: its
     * topic-score branch is already written and correct, but the caller passes
     * an empty map, so every learning path falls through to one of three
     * hardcoded topic lists chosen by overall percentage. Since the overall
     * score was itself computed from answer word count, the "personalised" path
     * was in practice selected by how much the student typed.
     *
     * A question contributes its score to each topic it is tagged with.
     */
    fun topicPercentages(results: List<Result>): Map<String, Int> {
        val sums = mutableMapOf<String, Int>()
        val counts = mutableMapOf<String, Int>()
        results.forEach { r ->
            r.topics.forEach { topic ->
                sums[topic] = (sums[topic] ?: 0) + r.score
                counts[topic] = (counts[topic] ?: 0) + 1
            }
        }
        return sums.mapValues { (topic, total) -> total / (counts[topic] ?: 1) }
    }
}
