package com.example.embeddedsystemscareerguide

import com.example.embeddedsystemscareerguide.models.AssessmentAnswerKey
import com.example.embeddedsystemscareerguide.models.Question
import com.example.embeddedsystemscareerguide.models.QuestionAnswer
import com.example.embeddedsystemscareerguide.services.AnswerGrader
import com.example.embeddedsystemscareerguide.services.GeminiReportService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import java.io.File

/**
 * THROWAWAY MEASUREMENT HARNESS - ecg-015. Not a test of anything.
 *
 * Measures what generateReportFromKey would cost if the answer key were flipped
 * to reviewed. It never flips it: the gate lives in AssessmentActivity, so the
 * cost can be derived from AnswerGrader plus the real prompt builders without
 * the asset being touched at all.
 *
 * Two halves. The NEEDS_MODEL rate is pure client-side logic and needs no GPU;
 * it decides the call count, which dominates everything. The per-call token cost
 * then comes from running the real prompts this emits against Ollama directly.
 *
 * Delete with the order.
 */
class KeyPathCostHarness {

    private val moduleDir = File("").absoluteFile
    private val assets = File(moduleDir, "src/main/assets")
    private val outDir = File(
        "C:/Users/nani0/AppData/Local/Temp/claude/" +
            "F--Documents-android-Embedded-Systems-Career-Guide/" +
            "2424306d-39c5-4be8-b9c7-984c9e1fdfad/scratchpad/ecg015"
    )

    private val gson = Gson()

    private fun loadKey(): AssessmentAnswerKey =
        gson.fromJson(
            File(assets, "assessment_answer_key.json").readText(),
            AssessmentAnswerKey::class.java
        )

    private fun loadQuestions(): List<Question> =
        gson.fromJson(
            File(assets, "initial_assessment_questions.json").readText(),
            object : TypeToken<List<Question>>() {}.type
        )

    /** Strip tags from authored HTML so a reference answer can be graded as prose. */
    private fun deHtml(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun words(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size

    private fun report(label: String, key: AssessmentAnswerKey, answers: Map<Int, String>): Triple<Int, Int, Int> {
        var correct = 0
        var needsModel = 0
        var unanswered = 0
        val reasons = mutableMapOf<String, Int>()

        key.entries.forEach { entry ->
            val a = answers[entry.id].orEmpty()
            val r = AnswerGrader.grade(entry, a)
            when (r.verdict) {
                AnswerGrader.Verdict.CORRECT -> correct++
                AnswerGrader.Verdict.UNANSWERED -> unanswered++
                AnswerGrader.Verdict.NEEDS_MODEL -> {
                    needsModel++
                    // Why it fell through, in the order grade() checks them.
                    val why = when {
                        entry.keyPoints.isEmpty() -> "no key points authored"
                        r.matchedMisconceptions.isNotEmpty() -> "misconception trigger"
                        words(a) < 25 -> "under 25 words"
                        r.coverage < 0.80 -> "coverage below 0.80"
                        else -> "other"
                    }
                    reasons[why] = (reasons[why] ?: 0) + 1
                }
            }
        }

        println("--- $label ---")
        println("  CORRECT=$correct  NEEDS_MODEL=$needsModel  UNANSWERED=$unanswered  (of ${key.entries.size})")
        println("  NEEDS_MODEL rate = ${"%.1f".format(needsModel * 100.0 / key.entries.size)}%")
        reasons.entries.sortedByDescending { it.value }.forEach { println("    reason: ${it.key} = ${it.value}") }
        val calls = (needsModel + 4) / 5
        println("  adjudication calls = ceil($needsModel/5) = $calls   + 1 roadmap = ${calls + 1} total")
        return Triple(correct, needsModel, unanswered)
    }

    @Test
    fun `measure needs-model rate and emit the real prompts`() {
        outDir.mkdirs()
        val key = loadKey()
        val questions = loadQuestions()
        println("key entries=${key.entries.size} reviewed=${key.reviewed} questions=${questions.size}")
        println("entries with zero keyPoints: ${key.entries.count { it.keyPoints.isEmpty() }}")

        // ---- Answer set A: the key's own reference answers -------------------
        // The best any student could realistically do. Gives the FLOOR of the
        // NEEDS_MODEL rate: nothing a student writes can score better against
        // criteria than the text the criteria were authored alongside.
        val setA = key.entries.associate { it.id to deHtml(it.modelAnswerHtml) }

        // ---- Answer set B: a real submission ---------------------------------
        // The 50 answers actually submitted through the app on 2026-08-05,
        // recovered from that run's stored report.
        val realFile = File(outDir.parentFile, "real_answers.json")
        val setB: Map<Int, String> = if (realFile.exists()) {
            val raw: Map<String, String> =
                gson.fromJson(realFile.readText(), object : TypeToken<Map<String, String>>() {}.type)
            raw.mapKeys { it.key.toInt() }
        } else emptyMap()

        // ---- Answer set C: reference answers cut to ~30 words -----------------
        // A strong student who writes concisely: clears MIN_WORDS_CORRECT but
        // carries only the opening of the reference content.
        val setC = setA.mapValues { (_, v) -> v.split(" ").take(30).joinToString(" ") }

        // ---- Answer set D: reference answers cut to ~60 words -----------------
        val setD = setA.mapValues { (_, v) -> v.split(" ").take(60).joinToString(" ") }

        val a = report("A  reference answers (quality ceiling)", key, setA)
        val d = report("D  reference answers, first 60 words", key, setD)
        val c = report("C  reference answers, first 30 words", key, setC)
        val b = report("B  real submission 2026-08-05 (12-14 words each)", key, setB)

        println()
        println("word-count profile of set A: " +
            setA.values.map { words(it) }.sorted().let { "min=${it.first()} median=${it[it.size/2]} max=${it.last()}" })
        println("set A entries scoring coverage>=0.80: " +
            key.entries.count { AnswerGrader.grade(it, setA[it.id].orEmpty()).coverage >= 0.80 })
        println("set A entries tripping a misconception: " +
            key.entries.count { AnswerGrader.grade(it, setA[it.id].orEmpty()).matchedMisconceptions.isNotEmpty() })

        // ---- Emit the REAL prompts for the token measurement ------------------
        // Set B is the observed submission and yields the full 50 NEEDS_MODEL,
        // so it produces the complete set of 10 adjudication chunks - the ones
        // to measure. Built by reflection so the measured prompt is byte-for-byte
        // what production would send.
        val svc = GeminiReportService()
        val qaList = questions.mapIndexed { i, q ->
            QuestionAnswer(n = i + 1, q = q.question, u = setB[q.id].orEmpty(), id = q.id)
        }
        val graded = key.entries.mapNotNull { e ->
            qaList.firstOrNull { it.id == e.id }?.let { AnswerGrader.grade(e, it.u) }
        }
        val verdicts = graded.associateBy { it.questionId }
        val entries = key.entries.associateBy { it.id }
        val needsModel = qaList.filter { verdicts[it.id]?.verdict == AnswerGrader.Verdict.NEEDS_MODEL }

        val mAdj = GeminiReportService::class.java.getDeclaredMethod(
            "buildAdjudicationPrompt", List::class.java, Map::class.java, Map::class.java
        ).apply { isAccessible = true }
        val mRoad = GeminiReportService::class.java.getDeclaredMethod(
            "buildRoadmapPrompt", String::class.java, Map::class.java
        ).apply { isAccessible = true }

        needsModel.chunked(5).forEachIndexed { i, chunk ->
            val p = mAdj.invoke(svc, chunk, verdicts, entries) as String
            File(outDir, "adj_%02d.txt".format(i + 1)).writeText(p)
        }
        val topics = AnswerGrader.topicPercentages(graded)
        File(outDir, "roadmap.txt").writeText(mRoad.invoke(svc, "katarapukhashyap", topics) as String)

        println()
        println("emitted ${needsModel.chunked(5).size} adjudication prompts + 1 roadmap prompt to $outDir")
        println("prompt char sizes: " + outDir.listFiles()!!.sortedBy { it.name }
            .joinToString(", ") { "${it.name}=${it.length()}" })
    }
}
