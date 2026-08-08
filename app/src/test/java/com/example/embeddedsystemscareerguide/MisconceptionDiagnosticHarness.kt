package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.AssessmentAnswerKey
import com.example.embeddedsystemscareerguide.services.AnswerGrader
import com.google.gson.Gson
import org.junit.Test
import java.io.File

/**
 * THROWAWAY DIAGNOSTIC - ecg-017. Asserts almost nothing; it reports.
 *
 * Question: is misconception detection working, or has a6ca2df's "completely
 * inert" failure returned on the grading path?
 *
 * Everything here goes through the production entry point,
 * AnswerGrader.grade(entry, rawAnswer), with the key parsed exactly the way
 * AnswerKeyRepository parses it - plain Gson, same data classes, same asset
 * bytes. Nothing is reflected into and nothing is reimplemented, so a finding
 * here is a finding about production.
 *
 * Delete with the order.
 */
class MisconceptionDiagnosticHarness {

    private val assets = File(File("").absoluteFile, "src/main/assets")

    /** Byte-for-byte what AnswerKeyRepository.load() feeds Gson. */
    private fun loadKeyLikeProduction(): AssessmentAnswerKey {
        val raw = File(assets, "assessment_answer_key.json").readBytes()
        return Gson().fromJson(String(raw, Charsets.UTF_8), AssessmentAnswerKey::class.java)
    }

    private fun deHtml(s: String) = s.replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()

    /** AnswerGrader.normalise is private; this mirrors it for reporting only. */
    private fun mirrorNormalise(t: String) =
        Regex("\\s+").replace(Regex("[^a-z0-9+#_\\s]").replace(t.lowercase(), " "), " ").trim()

    @Test
    fun `diagnose misconception detection end to end`() {
        val key = loadKeyLikeProduction()
        val entries = key.entries
        println("=".repeat(70))
        println("KEY: ${entries.size} entries, reviewed=${key.reviewed}")
        val objs = entries.sumOf { it.misconceptions.size }
        val trigs = entries.sumOf { e -> e.misconceptions.sumOf { it.any.size } }
        println("misconception objects=$objs  trigger strings=$trigs")
        println("entries with zero misconceptions: ${entries.count { it.misconceptions.isEmpty() }}")
        println("entries with a blank trigger string: " +
            entries.count { e -> e.misconceptions.any { m -> m.any.any { it.isBlank() } } })

        // ---- 1. Parse sanity: did Gson actually populate `any` and `note`? ----
        // If the JSON key names had drifted from @SerializedName these would be
        // empty and every trigger would be silently inert - the a6ca2df shape.
        println()
        println("PARSE CHECK  (inert detection would show empty `any` here)")
        entries.take(3).forEach { e ->
            e.misconceptions.forEach { m ->
                println("  Q${e.id} any=${m.any} noteLen=${m.note.length}")
            }
        }

        // ---- 2. The specific claim about Q1 ----------------------------------
        val q1 = entries.first { it.id == 1 }
        val q1Ref = deHtml(q1.modelAnswerHtml)
        val q1Res = AnswerGrader.grade(q1, q1Ref)
        println()
        println("Q1 CLAIM CHECK  (comment says: reference answer trips THREE of its own triggers)")
        println("  Q1 authored triggers (${q1.misconceptions.sumOf { it.any.size }} total): " +
            q1.misconceptions.flatMap { it.any })
        println("  Q1 reference answer, normalised, first 240 chars:")
        println("    ${mirrorNormalise(q1Ref).take(240)}")
        q1.misconceptions.flatMap { it.any }.forEach { t ->
            val n = mirrorNormalise(t)
            println("    trigger '$t' -> normalised '$n' -> present in reference? " +
                mirrorNormalise(q1Ref).contains(n))
        }
        println("  ACTUAL fired count on Q1 reference answer: ${q1Res.matchedMisconceptions.size}")
        println("  verdict=${q1Res.verdict} coverage=${"%.2f".format(q1Res.coverage)}")

        // ---- 3. All 50 reference answers -------------------------------------
        val refFired = entries.count {
            AnswerGrader.grade(it, deHtml(it.modelAnswerHtml)).matchedMisconceptions.isNotEmpty()
        }
        println()
        println("REFERENCE ANSWERS: $refFired of ${entries.size} trip any misconception")

        // ---- 4. The case that actually matters: WRONG answers ----------------
        // For each authored trigger, build an answer that plainly contains the
        // misconception, phrased as a student would write it. If the detector
        // works, every one of these must fire. This is the probe a misconception
        // detector should be judged on - reference answers are the wrong test.
        println()
        println("WRONG ANSWERS  (one per trigger, trigger embedded in a sentence)")
        var probes = 0
        var fired = 0
        val misses = mutableListOf<String>()
        entries.forEach { e ->
            e.misconceptions.forEach { m ->
                m.any.forEach { trig ->
                    probes++
                    val wrong = "In my understanding of this topic there is $trig " +
                        "and that is the main thing to remember about how it works in practice."
                    val r = AnswerGrader.grade(e, wrong)
                    if (r.matchedMisconceptions.isNotEmpty()) fired++
                    else misses.add("Q${e.id} '$trig'")
                }
            }
        }
        println("  probes=$probes  fired=$fired  missed=${misses.size}")
        println("  fire rate = ${"%.1f".format(fired * 100.0 / probes)}%")
        if (misses.isNotEmpty()) println("  MISSES: ${misses.take(15)}")

        // ---- 5. Does a fired trigger change the verdict? ----------------------
        // A high-coverage, long, WRONG answer: without the misconception gate it
        // would certify as CORRECT. This is the behaviour a6ca2df's message says
        // must hold - the trigger withholds certification.
        println()
        println("CERTIFICATION GATE  (reference answer + a misconception appended)")
        var gated = 0
        var wouldHaveBeenCorrect = 0
        entries.forEach { e ->
            val trig = e.misconceptions.firstOrNull()?.any?.firstOrNull() ?: return@forEach
            val clean = deHtml(e.modelAnswerHtml)
            val dirty = "$clean Also, there is $trig in this case."
            val cleanR = AnswerGrader.grade(e, clean)
            val dirtyR = AnswerGrader.grade(e, dirty)
            if (cleanR.verdict == AnswerGrader.Verdict.CORRECT) {
                wouldHaveBeenCorrect++
                if (dirtyR.verdict == AnswerGrader.Verdict.NEEDS_MODEL &&
                    dirtyR.matchedMisconceptions.isNotEmpty()
                ) gated++
            }
        }
        println("  entries whose clean reference answer certifies CORRECT: $wouldHaveBeenCorrect")
        println("  of those, blocked from CORRECT once a misconception is added: $gated")

        // ---- 6. Score effect --------------------------------------------------
        // The architecture decision in force: a keyword heuristic may withhold
        // certification but may not set a score. Confirm the gate does not move
        // the number, only the verdict.
        val e1 = entries.first { it.misconceptions.isNotEmpty() }
        val c = deHtml(e1.modelAnswerHtml)
        val d = "$c Also, there is ${e1.misconceptions.first().any.first()} in this case."
        println()
        println("SCORE EFFECT on Q${e1.id}: clean score=${AnswerGrader.grade(e1, c).score} " +
            "dirty score=${AnswerGrader.grade(e1, d).score} " +
            "(verdicts ${AnswerGrader.grade(e1, c).verdict} -> ${AnswerGrader.grade(e1, d).verdict})")
        println("=".repeat(70))
    }

    /**
     * The comment's claim, tested against the key it was actually written about.
     *
     * finalScore's note says Q1's reference answer tripped three of its own
     * triggers and that "synchronous communication" fires inside the correct
     * statement that UART is asynchronous. Against today's key that is
     * impossible - Q1 has two triggers. But the comment landed in e4b1617, and
     * the key immediately before it (d6aedcb) had six triggers on Q1 including
     * ordinary vocabulary. This grades that key with today's matcher.
     */
    @Test
    fun `test the comment's claim against the key it was written about`() {
        val histFile = File(
            "C:/Users/nani0/AppData/Local/Temp/claude/" +
                "F--Documents-android-Embedded-Systems-Career-Guide/" +
                "2424306d-39c5-4be8-b9c7-984c9e1fdfad/scratchpad/key_d6aedcb.json"
        )
        if (!histFile.exists()) { println("historical key not dumped; skipping"); return }
        val hist: AssessmentAnswerKey =
            Gson().fromJson(histFile.readText(), AssessmentAnswerKey::class.java)

        println("=".repeat(70))
        println("HISTORICAL KEY d6aedcb - the state the comment describes")
        val h1 = hist.entries.first { it.id == 1 }
        val h1Ref = deHtml(h1.modelAnswerHtml)
        val h1Res = AnswerGrader.grade(h1, h1Ref)
        println("  Q1 triggers: ${h1.misconceptions.flatMap { it.any }}")
        h1.misconceptions.flatMap { it.any }.forEach { t ->
            println("    '$t' present in Q1 reference? ${mirrorNormalise(h1Ref).contains(mirrorNormalise(t))}")
        }
        println("  >>> Q1 reference answer fired ${h1Res.matchedMisconceptions.size} misconception(s)")
        println("      comment claimed: 3")

        val h31 = hist.entries.firstOrNull { it.id == 31 }
        if (h31 != null) {
            val r31 = AnswerGrader.grade(h31, deHtml(h31.modelAnswerHtml))
            println("  Q31 triggers: ${h31.misconceptions.flatMap { it.any }}")
            println("  >>> Q31 reference answer fired ${r31.matchedMisconceptions.size}")
            println("      'asynchronous' contains 'synchronous': " +
                mirrorNormalise("UART is asynchronous communication unlike SPI")
                    .contains(mirrorNormalise("synchronous communication")))
        }

        val histFired = hist.entries.count {
            AnswerGrader.grade(it, deHtml(it.modelAnswerHtml)).matchedMisconceptions.isNotEmpty()
        }
        println("  REFERENCE ANSWERS TRIPPING A TRIGGER: $histFired of ${hist.entries.size}")
        println("  (today's key, same matcher: 0 of 50)")
        println("=".repeat(70))
    }
}
