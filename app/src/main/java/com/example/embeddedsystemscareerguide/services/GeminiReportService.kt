package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.models.QuestionAnswer
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AI Report Generation Service - Powered by local Ollama LLM
 *
 * Generates detailed assessment reports with personalized 12-week roadmaps
 * based on user quiz responses. Features include:
 * - Topic-by-topic analysis of strengths and weaknesses
 * - Personalized study recommendations
 * - Mobile-optimized HTML output
 * - Progress callbacks for UI updates during generation
 *
 * @see ProgressCallback for generation phase notifications
 */
class GeminiReportService {

    /**
     * Callback interface for reporting generation progress
     */
    interface ProgressCallback {
        fun onProgress(phase: Int, totalPhases: Int, phaseName: String, quote: String)
    }

    private val client = NetworkModule.longTimeoutClient
    private val gson = Gson()

    /**
     * HTML-escape a user-supplied value before it is interpolated into report markup.
     *
     * Assessment answers are free text and were previously spliced into the report
     * verbatim. The report is then rendered in a WebView, so an answer containing
     * `<script>` executed inside the app and was persisted to Firestore, making it a
     * stored-XSS sink rather than a one-off. Escaping here closes the injection at
     * the point of construction; ReportViewerActivity additionally runs with
     * JavaScript disabled.
     */
    private fun esc(value: String): String = InputSanitizer.sanitizeForHtml(value)

    companion object {
        private const val TAG = "GeminiReportService"

        // Questions per feedback call. Was 15, which put the call length right on
        // top of longTimeoutClient's 180s read timeout, so whether a student got a
        // real report was decided by how verbose the model happened to be that run.
        //
        // Measured against the real prompt on the serving GPU (es-career-guide-14b,
        // NUM_PARALLEL=2), two generations in flight, worst call of each set:
        //
        //   15 questions -> 6,693 completion tokens, 185s  -- OVER the timeout
        //    8 questions -> 3,769 completion tokens, 100s  -- 44% headroom
        //    5 questions -> 1,934 completion tokens,  50s  -- 72% headroom
        //
        // Contention is not what breaks it: per-sequence throughput barely moves
        // between one and two in flight (37-39 tok/s either way, because batching a
        // second sequence reuses the same weight reads). What breaks it is how much
        // completion each call asks for. Output length is proportional to questions
        // per call and varies about 40% run to run, so at 15 the upper tail crossed
        // 180s while the median sat under it. Eight is the largest size measured
        // whose WORST call still clears the timeout by 40%.
        //
        // Total tokens across the whole report barely change - the same 50 questions
        // get written up either way - and prompt processing is cheap, so paying for
        // more, smaller calls costs little and removes the cliff.
        private const val CHUNK_SIZE = 8
        
        // Motivational quotes for loading screen
        val QUOTES = listOf(
            "\"The expert in anything was once a beginner.\" – Helen Hayes",
            "\"Learning is not attained by chance; it must be sought for.\" – Abigail Adams",
            "\"The only way to do great work is to love what you do.\" – Steve Jobs",
            "\"Embedded systems are the invisible computers that make our world smart.\" – Anonymous",
            "\"A good engineer thinks in reverse and asks, what could go wrong?\" – Clive Maxfield",
            "\"In theory, there is no difference between theory and practice. In practice, there is.\" – Yogi Berra",
            "\"The best code is no code at all.\" – Jeff Atwood",
            "\"Simplicity is the ultimate sophistication.\" – Leonardo da Vinci",
            "\"First, solve the problem. Then, write the code.\" – John Johnson",
            "\"The function of good software is to make the complex appear simple.\" – Grady Booch"
        )
    }

    /**
     * A generated report, plus whether any part of it is filler.
     *
     * Generation degrades in three independent ways - per-question feedback can
     * fall back to a canned summary, the surrounding report shell can fall back
     * to a template, and a critical failure can produce an emergency report.
     * Every one of those used to be indistinguishable from a real report at the
     * call site, so a student could be handed a career roadmap assembled from
     * filler and told nothing. [isDegraded] is true when any of them happened,
     * so the UI can say so.
     */
    data class ReportResult(val html: String, val isDegraded: Boolean)

    /**
     * Generate complete assessment report using two-phase approach
     * With robust error handling to prevent blank reports
     * @param progressCallback Optional callback to report progress updates
     */
    suspend fun generateReport(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>,
        progressCallback: ProgressCallback? = null
    ): ReportResult = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "Starting report generation for ${questions.size} questions")

            val chunks = questions.chunked(CHUNK_SIZE)
            val totalChunks = chunks.size
            // Total phases: chunks + 1 (structuring) + 1 (finalizing)
            val totalPhases = totalChunks + 2

            var degraded = false

            // Phase 1 to N: Generate feedback for chunks
            val feedbackChunks = try {
                generateDetailedFeedbackWithProgress(questions, totalPhases, progressCallback)
            } catch (e: CancellationException) {
                // A cancelled generation must not fall through to a fallback:
                // CancellationException is an Exception, so the handler below
                // would otherwise turn "the user backed out" into a report
                // built from filler and present it as a real one.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate detailed feedback, using minimal feedback", e)
                degraded = true
                listOf(generateMinimalFeedback(questions))
            }

            // Phase N+1: Generate overall report structure
            withContext(Dispatchers.Main) {
                progressCallback?.onProgress(
                    totalChunks + 1, 
                    totalPhases, 
                    "Structuring your personalized roadmap...", 
                    QUOTES.random()
                )
            }
            
            val reportShell = try {
                generateOverallReport(userName, userEmail, questions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate report shell, using fallback", e)
                degraded = true
                "" // Will trigger fallback in assembleReport
            }

            // Phase N+2: Final Assembly
            withContext(Dispatchers.Main) {
                progressCallback?.onProgress(
                    totalPhases, 
                    totalPhases, 
                    "Assembling final report...", 
                    QUOTES.random()
                )
            }
            
            val completeReport = assembleReport(reportShell, feedbackChunks)

            // Log report length for debugging blank reports
            Log.d(TAG, "Report generation completed, length: ${completeReport.html.length} chars")

            if (completeReport.html.length < 500) {
                Log.w(TAG, "Report seems too short, may be incomplete")
            }

            return@withContext ReportResult(
                html = completeReport.html,
                isDegraded = degraded || completeReport.isDegraded
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Critical error generating report, using emergency fallback", e)
            // Emergency fallback - return basic report
            return@withContext ReportResult(
                html = generateEmergencyReport(userName, userEmail, questions),
                isDegraded = true
            )
        }
    }
    
    /**
     * Generate minimal feedback when API fails
     */
    private fun generateMinimalFeedback(questions: List<QuestionAnswer>): String {
        return questions.joinToString("\n") { qa ->
            """
            <div class="question-feedback">
                <h4>Question ${qa.n}: ${esc(qa.q)}</h4>
                <div class="user-answer">
                    <strong>Your Answer:</strong>
                    <blockquote>${esc(qa.u).ifBlank { "[No answer provided]" }}</blockquote>
                </div>
                <p>Your answer has been recorded. Please review embedded systems resources to improve your understanding of this topic.</p>
            </div>
            """.trimIndent()
        }
    }
    
    /**
     * Emergency fallback report when everything fails
     */
    private fun generateEmergencyReport(userName: String, userEmail: String, questions: List<QuestionAnswer>): String {
        val date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val questionsHtml = questions.joinToString("\n") { qa ->
            """
            <div class="question-feedback">
                <h4>Question ${qa.n}: ${esc(qa.q)}</h4>
                <div class="user-answer"><strong>Your Answer:</strong> ${esc(qa.u).ifBlank { "[No answer]" }}</div>
            </div>
            """.trimIndent()
        }
        
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Assessment Report</title>
    <style>
        body { font-family: sans-serif; background: #0f172a; color: #e2e8f0; padding: 1rem; line-height: 1.6; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { color: #a5b4fc; }
        h2 { color: #818cf8; margin-top: 1.5rem; }
        h4 { color: #c4b5fd; }
        .question-feedback { background: #1e293b; padding: 1rem; margin: 1rem 0; border-radius: 0.5rem; border-left: 4px solid #6366f1; }
        .user-answer { background: #334155; padding: 0.5rem; border-radius: 0.25rem; margin: 0.5rem 0; }
        .user-info { background: linear-gradient(135deg, #1e3a8a, #312e81); padding: 1rem; border-radius: 0.5rem; margin-bottom: 1rem; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Assessment Report</h1>
        <div class="user-info">
            <p><strong>Student:</strong> ${esc(userName)}</p>
            <p><strong>Email:</strong> ${esc(userEmail)}</p>
            <p><strong>Date:</strong> $date</p>
        </div>
        <h2>Your Responses</h2>
        $questionsHtml
        <h2>📌 Next Steps</h2>
        <p>Your assessment has been recorded. We encountered an issue generating detailed feedback. Please review your responses and explore embedded systems learning resources.</p>
        <p><strong>Recommended:</strong> Review STM32 tutorials, RTOS concepts, and C programming for embedded systems.</p>
    </div>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Phase 1: Generate detailed feedback for question chunks in parallel
     */
    private suspend fun generateDetailedFeedback(questions: List<QuestionAnswer>): List<String> =
        withContext(Dispatchers.IO) {

        val chunks = questions.chunked(CHUNK_SIZE)
        Log.d(TAG, "Processing ${chunks.size} chunks of questions")

        // Process all chunks concurrently
        val deferredResults = chunks.map { chunk ->
            async {
                generateFeedbackForChunk(chunk)
            }
        }

        // Wait for all chunks to complete
        deferredResults.awaitAll()
    }
    
    /**
     * Generate detailed feedback with progress reporting
     * Processes chunks sequentially to report progress accurately
     */
    private suspend fun generateDetailedFeedbackWithProgress(
        questions: List<QuestionAnswer>,
        totalPhases: Int,
        progressCallback: ProgressCallback?
    ): List<String> = withContext(Dispatchers.IO) {
        
        val chunks = questions.chunked(CHUNK_SIZE)
        Log.d(TAG, "Processing ${chunks.size} chunks of questions with progress")
        
        val results = mutableListOf<String>()
        
        chunks.forEachIndexed { index, chunk ->
            // Report progress for this chunk
            withContext(Dispatchers.Main) {
                progressCallback?.onProgress(
                    index + 1,
                    totalPhases,
                    "Analyzing questions ${(index * CHUNK_SIZE) + 1} to ${minOf((index + 1) * CHUNK_SIZE, questions.size)}...",
                    QUOTES.random()
                )
            }
            
            // Generate feedback for this chunk
            val feedback = generateFeedbackForChunk(chunk)
            results.add(feedback)
        }
        
        results
    }

    /**
     * Generate feedback for a single chunk of questions
     */
    private suspend fun generateFeedbackForChunk(questionChunk: List<QuestionAnswer>): String =
        withContext(Dispatchers.IO) {

        val prompt = buildFeedbackPrompt(questionChunk)
        val response = callGeminiAPIWithRetry(prompt)

        return@withContext response
    }

    /**
     * Phase 2: Generate overall report structure with roadmap
     */
    private suspend fun generateOverallReport(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>
    ): String = withContext(Dispatchers.IO) {

        // Two calls, not one. Together these produce exactly what the single
        // call produced; apart, neither is long enough to run at the read
        // timeout. The roadmap is far and away the biggest section - twelve
        // weeks of daily tasks - so leaving it in with everything else was what
        // kept this call at 160-260s while every feedback chunk finished inside
        // 100s. Splitting it also lets it carry ROADMAP_MAX_TOKENS, the same cap
        // the answer-key path already applies to the same content.
        val body = callGeminiAPIWithRetry(buildReportPrompt(userName, userEmail, questions))

        // Six weeks at a time. Measured, the whole twelve ran to exactly 4,096
        // completion tokens - the cap - which means it was being truncated, and
        // a truncated roadmap is thrown away rather than shown. Half of it fits
        // with room to spare, and the student still gets all twelve weeks.
        val roadmap = try {
            val firstHalf = callGeminiAPIWithRetry(
                buildLegacyRoadmapPrompt(questions, 1, 6),
                maxTokens = ROADMAP_MAX_TOKENS
            )
            val secondHalf = callGeminiAPIWithRetry(
                buildLegacyRoadmapPrompt(questions, 7, 12),
                maxTokens = ROADMAP_MAX_TOKENS
            )
            stripDocumentScaffolding(firstHalf) + "\n" + stripDocumentScaffolding(secondHalf)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missing roadmap is a worse report, not a broken one. Returning
            // the body alone lets assembleReport keep the summary, the topic
            // analysis and all fifty questions of feedback; the placeholder is
            // replaced with a plain note below.
            Log.w(TAG, "Roadmap generation failed; report will note it is missing", e)
            ""
        }

        val roadmapSection = if (roadmap.isBlank()) {
            "<p>Your roadmap could not be generated this time. The feedback above still applies.</p>"
        } else {
            stripDocumentScaffolding(roadmap)
        }

        val strippedBody = stripDocumentScaffolding(body)
        return@withContext if (strippedBody.contains(ROADMAP_PLACEHOLDER)) {
            strippedBody.replace(ROADMAP_PLACEHOLDER, roadmapSection)
        } else {
            // The model dropped the placeholder. Append with its own heading
            // rather than silently discard twelve weeks of roadmap.
            Log.w(TAG, "Roadmap placeholder missing from report body; appending roadmap")
            strippedBody +
                "\n<h2>🗺️ Your Personalized 12-Week Embedded Systems Roadmap</h2>\n" +
                roadmapSection
        }
    }

    /**
     * The roadmap, on its own.
     *
     * Same instructions the combined prompt used to carry, unchanged, so the
     * student gets the same roadmap. It is split out only because it is the
     * long pole: asked for alongside the summary and topic analysis it pushed
     * one completion past 5,000 tokens, and at ~33 tok/s under load that is a
     * call sitting on top of the 180s read timeout.
     */
    private fun buildLegacyRoadmapPrompt(
        questions: List<QuestionAnswer>,
        weekFrom: Int,
        weekTo: Int
    ): String {
        val userInputText = questions.joinToString("\n") { qa ->
            "Q${qa.n}: ${qa.q}\nA: ${qa.u.ifBlank { "[No answer]" }}\n"
        }
        val opening = if (weekFrom == 1) {
            "Start directly with the hardware recommendations, then Week $weekFrom."
        } else {
            "Weeks 1 to ${weekFrom - 1} have already been written and the hardware " +
                "recommendations are already in place - do NOT repeat either. " +
                "Start directly with <h3>Week $weekFrom</h3>."
        }

        return """
You are a world-class career mentor and principal embedded systems architect.
Based on the student's assessment below, write WEEKS $weekFrom TO $weekTo of their
personalized 12-week learning roadmap, as an HTML fragment.

Write ONLY weeks $weekFrom to $weekTo. Do not write any other week, and do not
write a summary, conclusion or closing paragraph - the rest of the roadmap and
the report around it are produced separately.

Output ONLY the roadmap markup. Do NOT output `<!DOCTYPE>`, `<html>`, `<head>`,
`<style>`, `<body>`, any CSS, or a heading for the roadmap - the heading is
already in place. $opening

**CRITICAL INSTRUCTIONS FOR THE 12-WEEK ROADMAP:**
This is the most important part of the report. It must be HYPER-DETAILED, SPECIFIC, PRECISE, and PRACTICAL. Do not give vague advice.

**MOBILE-OPTIMIZED FORMATTING:** You MUST NOT use tables. Instead, use the structure with `<h3>` tags for weeks and nested `<ul>` lists for daily tasks.

- **Resources:** You MUST provide specific resources:
  - **Books:** Include names of the best books and specify exact chapters (e.g., "The Definitive Guide to ARM Cortex-M3 and Cortex-M4 Processors by Joseph Yiu, Chapters 3-5").
  - **YouTube:** DO NOT include clickable YouTube links (they may be invalid). Instead, mention the exact YouTube channel name and video/playlist title like this: "YouTube: Embedded Systems Academy channel - 'ARM Cortex-M for Beginners' playlist" or "YouTube: ControllersTech channel - 'STM32 GPIO Tutorial' video".
  - **Online Courses:** Mention course names and platforms (e.g., "Udemy: 'Mastering Microcontroller with Embedded Driver Development' by FastBit").
- **Projects:** Break down into concrete daily steps with specific instructions.
- **Concepts:** Be precise with technical details and practical examples.

The roadmap should follow this structure for each week:

<h3>Week 1: [Topic Name]</h3>
<p><strong>Goal:</strong> [Clear learning objective]</p>
<ul>
    <li><strong>Day 1-2: [Subtopic]</strong>
        <ul>
            <li>[Specific book with chapter]</li>
            <li>YouTube: [Channel Name] - "[Video/Playlist Title]" (search on YouTube)</li>
            <li>[Key concept to master]</li>
            <li><strong>Mini-Project:</strong> [Concrete task]</li>
        </ul>
    </li>
    <li><strong>Day 3-4: [Next Subtopic]</strong>
        <ul>
            <li>[Details...]</li>
        </ul>
    </li>
</ul>

Use this format for every week in your assigned range, ensuring mobile readability with proper spacing and concise but detailed content.

**Hardware Recommendations:** At the beginning of the roadmap, recommend specific, affordable microcontroller boards (e.g., STM32 Nucleo, Arduino, ESP32) with approximate prices in Indian Rupees (₹) and mention where to purchase them in India (like Amazon.in, Robu.in, or electronics stores).

USER'S FULL Q&A TRANSCRIPT:
---
$userInputText
        """.trimIndent()
    }

    /**
     * Build the feedback generation prompt for a chunk
     */
    private fun buildFeedbackPrompt(questionChunk: List<QuestionAnswer>): String {
        return """
You are a world-class career mentor and principal embedded systems architect.
Your task is to provide in-depth, encouraging, and HYPER-DETAILED feedback for a student's answers.
CRITICAL INSTRUCTION: Use VERY simple, direct language. Explain WHY an answer is wrong or how it could be better. Acknowledge correct parts.
Your output MUST be ONLY the HTML for the "question-feedback" divs. Do NOT include any other text, markdown, or HTML structure.

For each of the following questions, generate a single `<div class="question-feedback">` block. All content for one question must be inside this single div.
The structure for each block MUST be in this exact order:
1. An `<h4>` tag containing the question number and text.
2. A `<div class="user-answer">` containing a `<strong>Your Answer:</strong>` label and a `<blockquote>` with the user's submitted answer.
3. Your detailed feedback on the user's answer in one or more `<p>` tags. This feedback should be distinct from the correct answer, providing personalized advice, explaining the practical importance of the concepts, and giving actionable advice for improvement.
4. A `<div class="correct-answer">` containing a `<strong>Correct Answer:</strong>` label and the detailed, comprehensive, expert answer.
5. **CRITICAL CODE FORMATTING:** If you provide C code examples in the correct answer, you MUST wrap them in `<pre><code>...</code></pre>` tags for correct styling.
6. A concluding `<div class="rating">` that provides a score out of 10 based on the quality of the user's answer (e.g., bad answers get 1-3, good answers get 7-9). Example: `<div class="rating"><strong>Rating:</strong> 8/10</div>`
7. A closing `</div>` tag for the main "question-feedback" div. IT IS CRITICAL that you close this div correctly after all other content for the question.

Example for ONE question with code:
<div class="question-feedback">
    <h4>Question 4: How do you perform bitwise operations...</h4>
    <div class="user-answer">
        <strong>Your Answer:</strong>
        <blockquote>my_reg = my_reg | (1 << 5);</blockquote>
    </div>
    <p>This is the correct way to set a bit! Well done. To make your code more readable and maintainable, especially in a team setting, it's a good practice to use macros. See the example in the correct answer.</p>
    <div class="correct-answer">
        <strong>Correct Answer:</strong>
        <p>To manipulate bits in a register, you use bitwise operators. It's best practice to define macros for readability.</p>
        <pre><code>#define BIT(n) (1U << (n))

// Set bit 5
REGISTER |= BIT(5);

// Clear bit 5
REGISTER &= ~BIT(5);

// Toggle bit 5
REGISTER ^= BIT(5);</code></pre>
    </div>
    <div class="rating"><strong>Rating:</strong> 9/10</div>
</div>

Now, generate these blocks for the following questions:
${questionChunk.joinToString("\n") { item ->
            """
---
Question ${item.n}: ${item.q}
User Answer: ${item.u.ifBlank { "[No answer provided]" }}
---
"""
        }}
        """.trimIndent()
    }

    /**
     * Build the overall report generation prompt
     */
    private fun buildReportPrompt(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>
    ): String {
        val userInputText = questions.joinToString("\n") { qa ->
            "Q${qa.n}: ${qa.q}\nA: ${qa.u.ifBlank { "[No answer]" }}\n"
        }

        return """
You are a world-class career mentor and principal embedded systems architect.
Based on the student's full Q&A transcript, generate a personalized HTML feedback report.

**CRITICAL: OUTPUT AN HTML FRAGMENT, NOT A DOCUMENT**
Do NOT output `<!DOCTYPE>`, `<html>`, `<head>`, `<style>`, `<body>`, or any CSS.
The page around your text, including every style, is added afterwards. Output
ONLY the inner HTML described below; anything outside it is discarded.

Begin with exactly this block:

<div class="user-info">
    <p><strong>Student:</strong> ${esc(userName)}</p>
    <p><strong>Email:</strong> ${esc(userEmail)}</p>
    <p><strong>Assessment Date:</strong> ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}</p>
</div>

The stylesheet already defines these classes - use them and do not invent
others: `.user-info`, `.question-feedback`, `.user-answer`, `.correct-answer`,
`.rating`, `.section`, plus ordinary `h2`/`h3`/`h4`/`p`/`ul`/`li`/`pre`/`code`/
`blockquote`.

**CRITICAL: REPORT STRUCTURE ORDER**
After the user info block, output these sections in this exact order:
1. Overall Summary
2. Topic-by-Topic Analysis (your strengths and weaknesses by topic)
3. Detailed Question Feedback Section (IMPORTANT: For this section, you MUST output ONLY this exact HTML comment with a heading: `<h2>📝 Detailed Question-by-Question Analysis</h2><!-- QUESTION_FEEDBACK_INSERT_POINT -->` - DO NOT generate any actual question feedback here, the detailed feedback will be inserted automatically at this placeholder)
4. The 12-week roadmap section (IMPORTANT: like the question feedback, output ONLY this exact heading and comment: `<h2>🗺️ Your Personalized 12-Week Embedded Systems Roadmap</h2><!-- ROADMAP_INSERT_POINT -->` - DO NOT write the roadmap itself, it is generated separately and inserted at this placeholder)
5. Final Recommendations & Conclusion

**CRITICAL: DO NOT GENERATE DUPLICATE QUESTION FEEDBACK**
The question-by-question feedback is generated separately and will be injected at the placeholder. You MUST NOT create your own question feedback section. Only place the heading and the exact comment `<!-- QUESTION_FEEDBACK_INSERT_POINT -->`.

**CRITICAL INSTRUCTIONS FOR TOPIC-BY-TOPIC ANALYSIS:**
This section must be comprehensive and directly reflect the assessment results. Based on the user's answers, provide a detailed breakdown of their knowledge gaps across all major embedded systems categories. For each category (e.g., "Embedded Systems Fundamentals & Architecture", "C Programming for Embedded Systems", "Microcontroller Peripherals & Drivers", "Real-Time Operating Systems (RTOS)", etc.), list the specific concepts where the user showed weakness AND strength in bullet points. The goal is to give the student a clear overview of their performance before they see the detailed question feedback.

USER'S FULL Q&A TRANSCRIPT:
---
$userInputText
        """.trimIndent()
    }

    /**
     * Make API call to Ollama
     */
    /**
     * @param maxTokens completion cap. The default 16384 is what the legacy
     *   whole-document path needs; the answer-key path passes something far
     *   smaller, because ADJUDICATION_CHUNK bounds the PROMPT and nothing was
     *   bounding the completion. At a degraded 15 tok/s the 180s read timeout
     *   is reached around 2,700 tokens, so a 16384 budget lets a single call
     *   blow the timeout no matter how small its prompt was.
     * @param temperature lower for structured output than for prose.
     */
    /**
     * Retrying wrapper. This service was the only one of the three without a
     * ladder, and the answer-key path made that worse by going from 5 sequential
     * calls to 11: at a 5% per-call failure rate that is a 43% chance of at
     * least one failing per report, versus 23% before. Three attempts with
     * backoff takes it to about 0.1%.
     *
     * A transient 502 from the tunnel is the common case and is exactly what a
     * retry is for. Cancellation is rethrown before the ladder sees it.
     */
    internal suspend fun callGeminiAPIWithRetry(
        prompt: String,
        maxTokens: Int = 16384,
        temperature: Double = 0.7,
        maxRetries: Int = 3,
        deadlineNanos: Long? = null,
        // Seam for tests only. The default is the real call, so production
        // behaviour is exactly what it was before this parameter existed.
        call: suspend (String, Int, Double) -> String = { p, m, t -> callGeminiAPI(p, m, t) }
    ): String {
        var last: Exception? = null
        var delayMs = 1000L
        repeat(maxRetries) { attempt ->
            // Checked BEFORE the attempt, not only after one has failed.
            //
            // The budget used to be consulted in the failure handlers alone,
            // which gated retries but did not bound the clock: every remaining
            // call still got one unguarded attempt, and an unguarded attempt is
            // a full 180s read timeout. With ten calls left in the report that
            // is thirty minutes spent entirely past a deadline that had already
            // expired. Refusing to dial at all is what turns
            // REPORT_BUDGET_MINUTES into a ceiling rather than a suggestion.
            if (budgetSpent(System.nanoTime(), deadlineNanos)) {
                Log.w(TAG, "Report budget spent before attempt ${attempt + 1}; not calling")
                // Prefer the real cause when there is one, so a report that ran
                // out of time mid-ladder still logs what was actually failing.
                throw last ?: ReportBudgetExpiredException(
                    "Report wall-clock budget spent before this call could start"
                )
            }
            try {
                return call(prompt, maxTokens, temperature)
            } catch (e: CancellationException) {
                // MUST stay the first catch. CancellationException is an
                // Exception, so any catch placed above this one turns "the
                // student backed out" into a fallback report presented as real.
                throw e
            } catch (e: TruncatedResponseException) {
                // Retrying is pointless: the same prompt and the same ceiling
                // produce the same truncation, three times, slowly.
                throw e
            } catch (e: ClientErrorException) {
                throw e
            } catch (e: RateLimitedException) {
                // The one 4xx worth repeating. Wait the interval the server
                // asked for, or the ladder's own backoff if it did not say.
                last = e
                val wait = rateLimitWaitMs(e.retryAfterMs, delayMs)
                Log.w(TAG, "Rate limited on attempt ${attempt + 1}/$maxRetries; waiting ${wait}ms")
                if (attempt >= maxRetries - 1) throw e
                if (!rateLimitFitsBudget(wait, System.nanoTime(), deadlineNanos)) {
                    Log.w(TAG, "Retry-After outlasts the report budget; giving up on this call")
                    throw e
                }
                kotlinx.coroutines.delay(wait)
                delayMs *= 2
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Report API attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                // Same predicate the loop head uses. Kept here as well so a
                // spent budget does not first sleep out its backoff for nothing.
                val outOfTime = budgetSpent(System.nanoTime(), deadlineNanos)
                if (outOfTime) {
                    Log.w(TAG, "Retry budget exhausted; giving up on this call")
                    throw e
                }
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        throw last ?: Exception("Max retries exceeded")
    }

    private suspend fun callGeminiAPI(
        prompt: String,
        maxTokens: Int = 16384,
        temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        try {
            val requestBody = JsonObject().apply {
                addProperty("model", NetworkModule.DEFAULT_MODEL)
                addProperty("prompt", prompt)
                addProperty("stream", false)
                add("options", JsonObject().apply {
                    addProperty("temperature", temperature)
                    addProperty("num_predict", maxTokens)
                    addProperty("top_p", 0.95)
                })
            }

            val request = Request.Builder()
                .url(NetworkModule.getOllamaGenerateUrl())
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val cleaned = client.newCall(request).awaitResponse().use { response ->
                val responseBody = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    Log.e(TAG, "API Error: ${response.code} - $responseBody")
                    throw httpFailureFor(response.code, response.header("Retry-After"))
                }

                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

                // Ollama reports why generation stopped. "length" means the
                // completion hit num_predict, so what came back is a fragment -
                // a 12-week roadmap cut off at week 6, or JSON missing its
                // closing bracket. Crucially the fragment is NON-BLANK, so every
                // isBlank() degradation guard downstream sees success. Treat it
                // as the failure it is, at the only point that can still tell.
                if (jsonResponse.get("done_reason")?.asString == "length") {
                    throw TruncatedResponseException(
                        "Generation hit the ${'$'}maxTokens token limit and was cut off"
                    )
                }

                val content = jsonResponse.get("response")?.asString
                    ?: throw Exception("No response text from Ollama")

                // Strip Qwen3 <think>...</think> reasoning blocks before returning HTML content
                content.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
            }

            Log.d(TAG, "API response length: ${cleaned.length} chars")

            return@withContext cleaned

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Ollama API", e)
            throw e
        }
    }
    /**
     * Wraps the model's report body in the page around it.
     *
     * This markup used to live in the prompt: the model was handed the whole
     * document - doctype, head and 130 lines of CSS - and told to reproduce it
     * verbatim before it wrote a word of prose. That is the same 4,332
     * characters on every report, regenerated at roughly 38 tokens a second,
     * and it was what pushed this one call up against the 180s read timeout
     * while the feedback chunks around it finished comfortably.
     *
     * The bytes are identical either way, so they are emitted here for nothing
     * and the model is asked only for what actually differs per student.
     *
     * [bodyHtml] is model HTML and is emitted as markup, exactly as it was when
     * the model produced the whole document, so the trust level is unchanged.
     * If anything the model now has less reach: it no longer produces the
     * document, so it cannot emit <head>, <style> or <script> at all.
     */
    private fun wrapGeneratedReport(bodyHtml: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
    <title>Your Personalized Embedded Systems Report</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
            background-color: #0f172a; 
            color: #cbd5e1; 
            line-height: 1.6; 
            padding: 1rem;
            font-size: 14px;
        }
        .container { 
            max-width: 100%; 
            margin: 0 auto; 
            background-color: #1e293b; 
            border-radius: 0.75rem; 
            padding: 1rem; 
            border: 1px solid #334155; 
        }
        h1 { 
            font-size: 1.5rem; 
            text-align: center; 
            background: linear-gradient(to right, #818cf8, #60a5fa); 
            -webkit-background-clip: text; 
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 1rem;
            line-height: 1.3;
        }
        h2 { 
            font-size: 1.25rem; 
            color: #f1f5f9;
            border-bottom: 2px solid #475569; 
            padding-bottom: 0.5rem; 
            margin-top: 1.5rem;
            margin-bottom: 0.75rem;
        }
        h3 { 
            font-size: 1.1rem; 
            color: #94a3b8; 
            margin-top: 1rem;
            margin-bottom: 0.5rem;
        }
        h4 {
            font-size: 1rem;
            color: #f1f5f9;
            margin-bottom: 0.5rem;
        }
        p { 
            color: #cbd5e1; 
            margin-bottom: 0.75rem;
            word-wrap: break-word;
        }
        ul, ol { 
            padding-left: 1.25rem; 
            margin-bottom: 0.75rem;
        }
        li { 
            color: #cbd5e1; 
            margin-bottom: 0.5rem;
            word-wrap: break-word;
        }
        a { 
            color: #818cf8; 
            text-decoration: none; 
            word-break: break-all;
        }
        a:hover { text-decoration: underline; }
        .section { margin-bottom: 2rem; }
        .question-feedback { 
            margin-bottom: 1.5rem; 
            padding: 1rem; 
            background-color: #0f172a; 
            border-radius: 0.5rem; 
            border: 1px solid #334155; 
        }
        .user-answer, .correct-answer, .rating { 
            margin-top: 0.75rem; 
        }
        strong { color: #94a3b8; }
        blockquote { 
            border-left: 3px solid #4f46e5; 
            padding-left: 0.75rem; 
            margin: 0.5rem 0;
            font-style: italic; 
            color: #94a3b8;
            word-wrap: break-word;
        }
        pre { 
            background-color: #020617; 
            color: #e2e8f0; 
            padding: 0.75rem; 
            border-radius: 0.5rem; 
            overflow-x: auto; 
            white-space: pre-wrap;
            word-wrap: break-word;
            font-family: 'Courier New', monospace; 
            font-size: 0.85rem; 
            border: 1px solid #334155;
            margin: 0.5rem 0;
        }
        code { 
            font-family: 'Courier New', monospace; 
            background-color: #334155; 
            padding: 0.2em 0.4em; 
            border-radius: 0.25rem; 
            font-size: 0.85rem;
            word-break: break-all;
        }
        pre > code { 
            background-color: transparent; 
            padding: 0; 
        }
        .user-info {
            background: linear-gradient(135deg, #1e3a8a 0%, #312e81 100%);
            padding: 1rem;
            border-radius: 0.5rem;
            margin-bottom: 1.5rem;
        }
        .user-info p {
            margin-bottom: 0.25rem;
            font-size: 0.9rem;
        }
        @media (max-width: 480px) {
            body { padding: 0.5rem; font-size: 13px; }
            .container { padding: 0.75rem; }
            h1 { font-size: 1.25rem; }
            h2 { font-size: 1.1rem; }
            h3 { font-size: 1rem; }
            pre { font-size: 0.75rem; padding: 0.5rem; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Your Personalized Embedded Systems Report</h1>
$bodyHtml
    </div>
</body>
</html>
        """.trimIndent()
    }



    /**
     * Assemble final report by injecting feedback chunks into report shell
     * With fallback handling for blank or truncated content
     */
    private fun assembleReport(reportShell: String, feedbackChunks: List<String>): ReportResult {
        val combinedFeedback = feedbackChunks.joinToString("\n\n")

        // The model now returns the report body; the document around it is built
        // here by [wrapGeneratedReport]. Strip any scaffolding it emitted anyway -
        // the instruction is explicit but a model asked for HTML will sometimes
        // still reach for a doctype, and that must not end up nested inside ours.
        val body = stripDocumentScaffolding(reportShell)

        // Sections the prompt asks for: summary, topic analysis, the feedback
        // heading, roadmap, conclusion. Well under that is not a report worth
        // presenting as complete. Hard truncation is caught earlier and more
        // precisely by TruncatedResponseException, which reads Ollama's own
        // done_reason; this is the coarse net behind it.
        val sectionCount = Regex("<h2", RegexOption.IGNORE_CASE).findAll(body).count()
        if (body.isBlank() || sectionCount < 3) {
            Log.w(TAG, "Report body was blank or too short ($sectionCount sections), using fallback")
            return ReportResult(generateFallbackReport(combinedFeedback), isDegraded = true)
        }

        val withFeedback = if (body.contains(FEEDBACK_PLACEHOLDER)) {
            body.replace(FEEDBACK_PLACEHOLDER, combinedFeedback)
        } else {
            // Losing the placeholder must not lose fifty questions of feedback.
            Log.w(TAG, "Feedback placeholder missing from report body; appending feedback")
            body + "\n" + combinedFeedback
        }

        return ReportResult(wrapGeneratedReport(withFeedback), isDegraded = false)
    }

    /**
     * Reduces whatever the model returned to the body content we asked for.
     *
     * The prompt says to emit a fragment, but this is defensive: a full document
     * nested inside our own container renders as a mess, and a fenced code block
     * renders as literal angle brackets. Both are cheap to undo here and neither
     * is worth degrading a report over.
     */
    internal fun stripDocumentScaffolding(html: String): String {
        var s = html.trim()

        // ```html ... ``` fences
        if (s.startsWith("```")) {
            s = s.removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
        }

        // A whole document: keep what is inside <body>.
        val bodyOpen = s.indexOf("<body", ignoreCase = true)
        if (bodyOpen >= 0) {
            val contentStart = s.indexOf('>', bodyOpen)
            val bodyClose = s.lastIndexOf("</body>", ignoreCase = true)
            if (contentStart >= 0 && bodyClose > contentStart) {
                s = s.substring(contentStart + 1, bodyClose).trim()
            }
        } else {
            // No <body>, but a stray head/style would still be emitted verbatim.
            s = s.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</?html[^>]*>", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        // Our own container and title are added by the wrapper; drop duplicates.
        val container = Regex("^<div\\s+class=\"container\"\\s*>", RegexOption.IGNORE_CASE)
        if (container.containsMatchIn(s)) {
            s = container.replace(s, "").trim().removeSuffix("</div>").trim()
        }
        s = s.replace(Regex("^<h1[\\s\\S]*?</h1>", RegexOption.IGNORE_CASE), "").trim()

        return s
    }
    
    /**
     * Generate a fallback report if the main generation fails
     */
    // ========================================================================
    // Answer-key path
    //
    // The report is assembled here and the model is asked only for what is
    // genuinely per-user. Today it writes the whole document: the CSS, the
    // reference answer for all 50 questions, and an echo of the student's own
    // answer - roughly 29k tokens per report of content that either already
    // ships in the APK or is already on the device. None of that is
    // per-user, and regenerating it is most of the wall-clock cost.
    //
    // Only questions the grader could not resolve reach the model, in small
    // chunks, returning structured feedback and nothing else.
    // ========================================================================

    /** Questions per adjudication call. Sized so one call fits the 180s read
     *  timeout even at a degraded ~15 tok/s, not for throughput. */
    private val ADJUDICATION_CHUNK = 5

    /**
     * Wall-clock ceiling for one report, across every call and retry.
     *
     * Was 10, which was shorter than a healthy run. A full report is ten calls
     * and measured 13m11s end to end on device (23:00:24 -> 23:13:35) with
     * nothing failing, so from minute 10 onward the ladder was switched off for
     * the rest of every run - including the roadmap, which is last. Under cohort
     * load calls slow, the run stretches, and the share of it with retries
     * disabled grows. Retries were unavailable exactly when they were needed.
     *
     * Raising it was previously unsafe for a reason that had nothing to do with
     * the number: the generation overlay disabled the system back gesture and
     * offered no way out, so the budget was the only thing standing between a
     * student and being held for the better part of an hour. It was protecting
     * them from the screen, not the server from the load. The overlay now
     * carries a cancel control (AssessmentActivity.cancelReportGeneration), so
     * the ceiling can be sized for the work instead of for the trap.
     *
     * 20 minutes is ~50% headroom over the measured clean run. The worst case is
     * NOT 20 minutes though - see [budgetSpent]. The deadline stops new attempts
     * from starting; it cannot abort one already in flight, and an attempt that
     * starts a microsecond inside the deadline still owns the 180s read timeout.
     * So the bound is:
     *
     *   20 min budget + 180 s in-flight read timeout = 23 minutes, worst case.
     *
     * That is the ceiling for a run in which every single call burns its full
     * timeout. It is a number a student can be held to only because they can
     * leave at any point; the typical run remains the measured ~13 minutes.
     */
    internal val REPORT_BUDGET_MINUTES = 20L

    /**
     * The instant this report must stop dialling out.
     *
     * Split out of [generateReportFromKey] so the ceiling is asserted in a test
     * rather than recomputed by hand there and in the tests separately.
     */
    internal fun reportDeadlineNanos(startNanos: Long): Long =
        startNanos + REPORT_BUDGET_MINUTES * 60L * 1_000_000_000L

    /**
     * Whether the report's wall-clock budget is gone.
     *
     * A null deadline means no budget was ever set. That is the legacy
     * [generateReport] path, which threads no deadline into any of its ten
     * calls; there, cancellation is the only bound and this must not invent one.
     */
    internal fun budgetSpent(nowNanos: Long, deadlineNanos: Long?): Boolean =
        deadlineNanos != null && nowNanos > deadlineNanos

    /** Retrying this changes nothing: the same prompt hits the same ceiling. */
    private class TruncatedResponseException(message: String) : Exception(message)

    /**
     * The report's wall-clock budget was spent before this call could start, so
     * no request was made. Distinct from a failed call: nothing was attempted,
     * and the 180s that attempting would have cost is the whole point of the
     * type. It is an ordinary Exception, so every caller's existing handler
     * marks the report degraded, which is what a report missing a section is.
     */
    internal class ReportBudgetExpiredException(message: String) : Exception(message)

    /**
     * A 4xx is a request the server will keep rejecting - with one exception.
     * 429 means "you asked too often", not "you asked wrongly", and is the one
     * 4xx worth repeating. It is typed separately as [RateLimitedException] and
     * must not be folded back in here.
     */
    internal class ClientErrorException(message: String) : Exception(message)

    /**
     * HTTP 429 from the gateway: refused because this student is over the per-uid
     * cap, not because the request was malformed. The cap was restored on
     * 2026-08-05 after a crash had been silently clearing its counter, so this is
     * newly reachable in production.
     *
     * [retryAfterMs] is the server's own Retry-After, already parsed and clamped,
     * or null when the header was absent or unusable - in which case the ladder
     * falls back to its own exponential delay.
     */
    internal class RateLimitedException(
        message: String,
        val retryAfterMs: Long?
    ) : Exception(message)

    /**
     * Bounds on how long a Retry-After may park a student.
     *
     * The floor stops a `Retry-After: 0` turning the ladder into a busy loop
     * against a server that is already asking for room. The ceiling stops a
     * hostile or miscomputed header holding the screen: three retries at the
     * ceiling is three minutes, which still fits inside REPORT_BUDGET_MINUTES
     * alongside the generation itself.
     */
    private val RETRY_AFTER_MIN_MS = 1_000L
    private val RETRY_AFTER_MAX_MS = 60_000L

    /**
     * Parses an HTTP Retry-After into milliseconds, clamped to the bounds above.
     *
     * RFC 9110 defines the value as delay-SECONDS. Note that
     * GeminiChallengeService reads the same header as milliseconds, which makes
     * its delay a thousand times too short; that is its bug to fix, not this
     * file's, and it is reported rather than copied.
     *
     * The HTTP-date form is legal but this gateway does not emit it, so it is
     * treated as unusable rather than guessed at. Returns null whenever there is
     * nothing dependable to use, which tells the caller to fall back to its own
     * exponential delay.
     */
    internal fun parseRetryAfterMs(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        if (seconds <= 0L) return null
        // Bound before multiplying: a header of Long.MAX_VALUE would otherwise
        // overflow into a negative delay.
        val capSeconds = RETRY_AFTER_MAX_MS / 1_000L
        val bounded = if (seconds > capSeconds) capSeconds else seconds
        return (bounded * 1_000L).coerceAtLeast(RETRY_AFTER_MIN_MS)
    }

    /**
     * How long to wait before retrying a 429: the server's figure when it gave a
     * usable one, otherwise the ladder's own exponential delay.
     */
    internal fun rateLimitWaitMs(retryAfterMs: Long?, exponentialMs: Long): Long =
        retryAfterMs ?: exponentialMs

    /**
     * Whether sleeping [waitMs] and trying again still lands inside the report's
     * wall-clock budget. Waiting past the deadline only to be refused by the next
     * budget check wastes the student's time for nothing.
     */
    internal fun rateLimitFitsBudget(
        waitMs: Long,
        nowNanos: Long,
        deadlineNanos: Long?
    ): Boolean {
        if (deadlineNanos == null) return true
        return nowNanos + waitMs * 1_000_000L <= deadlineNanos
    }

    /**
     * Maps a failed HTTP status onto the exception that says what to do about it:
     * wait and repeat (429), stop asking (other 4xx), or retry generically
     * (everything else, which is what a 502 from the tunnel needs).
     */
    internal fun httpFailureFor(code: Int, retryAfterHeader: String?): Exception = when {
        code == 429 -> RateLimitedException(
            "Rate limited by the gateway (429)",
            parseRetryAfterMs(retryAfterHeader)
        )
        code in 400..499 -> ClientErrorException("API call failed: $code")
        else -> Exception("API call failed: $code")
    }

    /**
     * Where the separately-generated question feedback is spliced into the
     * model's report body. The model is told to emit this exact comment and
     * nothing else for that section.
     */
    private val FEEDBACK_PLACEHOLDER = "<!-- QUESTION_FEEDBACK_INSERT_POINT -->"

    /** Where the separately-generated 12-week roadmap is spliced in. */
    private val ROADMAP_PLACEHOLDER = "<!-- ROADMAP_INSERT_POINT -->"

    /** Completion caps, so one call cannot outrun the 180s read timeout. */
    private val ADJUDICATION_MAX_TOKENS = 2048
    private val ROADMAP_MAX_TOKENS = 4096

    /** How far above its measured coverage the model may lift a score. Stops a
     *  generous grader turning a thin answer into a good one. */
    private val MAX_MODEL_SCORE_LIFT = 25

    // internal rather than private so the escaping lanes and score
    // reconciliation can be unit-tested; both are easy to get silently wrong.
    /**
     * Tags the model is allowed to emit, with NO attributes.
     *
     * The roadmap is meant to BE markup, so esc() would show students literal
     * &lt;h3&gt;. But it is model output reaching a document that is persisted to
     * Firestore and re-rendered on every later view, so it cannot go in raw
     * either - the endpoint it comes from is a public ngrok tunnel. Allowlisting
     * bare tags keeps the formatting and removes every attribute, which is where
     * the event handlers and javascript: URIs live.
     */
    private val ESCAPED_ALLOWED_TAG = Regex(
        "&lt;(/?)(p|br|ul|ol|li|h3|h4|h5|strong|em|b|i|code|pre)&gt;",
        RegexOption.IGNORE_CASE
    )

    internal fun sanitizeModelHtml(html: String): String {
        // Escape everything, then restore only BARE allowed tags. Doing it in
        // this order is what makes attributes impossible: "<p onclick=x>"
        // escapes to "&lt;p onclick=x&gt;" and never matches the bare-tag
        // pattern, so it stays inert text rather than becoming an element.
        val escaped = esc(html)
            // esc() turns a pre-escaped "&amp;" into "&amp;amp;", which renders
            // to the student as literal "&amp;". Collapse the one level of
            // over-escaping back. This cannot re-enable markup: "&lt;" renders
            // as the character "<", never as the start of a tag.
            .replace("&amp;lt;", "&lt;")
            .replace("&amp;gt;", "&gt;")
            .replace("&amp;amp;", "&amp;")
            .replace("&amp;quot;", "&quot;")
            .replace("&amp;#39;", "&#39;")
        return ESCAPED_ALLOWED_TAG.replace(escaped) { m ->
            "<" + m.groupValues[1] + m.groupValues[2].lowercase() + ">"
        }
    }

    internal data class Adjudicated(
        val id: Int,
        val score: Int,
        val feedbackText: String
    )

    /**
     * The id to key the answer key by. Falls back to the display ordinal for
     * QuestionAnswer values built before [QuestionAnswer.id] existed, which
     * matches the pre-existing behaviour for the contiguous 1..50 asset.
     */
    internal val QuestionAnswer.keyId: Int
        get() = id

    /**
     * Build the report from the precomputed key.
     *
     * Caller must have checked [AssessmentAnswerKey.reviewed]: this serves
     * authored reference answers directly to the student, so it must not run
     * against a key no human has checked.
     */
    suspend fun generateReportFromKey(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>,
        graded: List<AnswerGrader.Result>,
        key: com.example.embeddedsystemscareerguide.models.AssessmentAnswerKey,
        topicPercentages: Map<String, Int>,
        progressCallback: ProgressCallback? = null
    ): ReportResult = withContext(Dispatchers.IO) {

        // One budget for the whole report. Without it, three attempts on each
        // of ten chunks plus backoff can reach ~99 minutes behind a full-screen
        // overlay - far past anything a student will wait for. The overlay is
        // now cancellable too, so the budget is a ceiling rather than the only
        // way out; both bounds are wanted, for different reasons.
        val deadline = reportDeadlineNanos(System.nanoTime())

        val entries = key.entries.associateBy { it.id }
        val verdicts = graded.associateBy { it.questionId }

        // Question ids and the 1-based n used by the report are not guaranteed
        // to agree; index by the id the grader actually used.
        val needsModel = questions.filter { q ->
            verdicts[q.keyId]?.verdict == AnswerGrader.Verdict.NEEDS_MODEL
        }
        val chunks = needsModel.chunked(ADJUDICATION_CHUNK)
        val totalPhases = chunks.size + 2
        var degraded = false

        val adjudicated = mutableMapOf<Int, Adjudicated>()
        chunks.forEachIndexed { index, chunk ->
            withContext(Dispatchers.Main) {
                progressCallback?.onProgress(
                    index + 1, totalPhases,
                    "Reviewing your answers (${index + 1}/${chunks.size})...",
                    QUOTES.random()
                )
            }
            try {
                val raw = callGeminiAPIWithRetry(
                    buildAdjudicationPrompt(chunk, verdicts, entries),
                    maxTokens = ADJUDICATION_MAX_TOKENS,
                    temperature = 0.3,
                    deadlineNanos = deadline
                )
                parseAdjudication(raw, chunk.map { it.keyId }).forEach {
                    // An entry with no feedback text would otherwise satisfy the
                    // omitted-id check below, render as an empty paragraph, and
                    // still carry its score lift - a blank gap where the
                    // personalised feedback should be, reported as a success.
                    if (it.feedbackText.isBlank()) {
                        Log.w(TAG, "Adjudication returned no feedback for id ${it.id}")
                        degraded = true
                    } else {
                        adjudicated[it.id] = it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One failed chunk must not become a whole fallback report, and
                // must not pass silently either.
                Log.w(TAG, "Adjudication chunk ${index + 1} failed", e)
                degraded = true
            }
        }
        // A chunk that returned but omitted questions is also degradation.
        if (needsModel.any { it.keyId !in adjudicated }) degraded = true
        // So is a question the key does not cover at all: it renders with no
        // reference answer and no score, and the omission check above cannot
        // see it because it never entered `verdicts` in the first place.
        if (questions.any { entries[it.keyId] == null }) {
            Log.w(TAG, "Answer key does not cover every question in this assessment")
            degraded = true
        }

        withContext(Dispatchers.Main) {
            progressCallback?.onProgress(
                totalPhases - 1, totalPhases,
                "Building your roadmap...", QUOTES.random()
            )
        }
        val roadmap = try {
            callGeminiAPIWithRetry(
                buildRoadmapPrompt(userName, topicPercentages),
                maxTokens = ROADMAP_MAX_TOKENS,
                temperature = 0.5,
                deadlineNanos = deadline
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Roadmap generation failed", e)
            degraded = true
            ""
        }
        // A call that succeeds and returns nothing - Ollama returning an empty
        // response, or output that was entirely a stripped <think> block - is
        // still a missing roadmap. Without this the report prints "could not be
        // generated" while isDegraded stays false, so the human-readable and
        // machine-readable signals contradict each other.
        if (roadmap.isBlank()) degraded = true

        withContext(Dispatchers.Main) {
            progressCallback?.onProgress(
                totalPhases, totalPhases, "Assembling your report...", QUOTES.random()
            )
        }

        val blocks = questions.joinToString("\n") { q ->
            renderQuestionBlock(q, verdicts[q.keyId], entries[q.keyId], adjudicated[q.keyId])
        }
        ReportResult(
            html = wrapReport(userName, userEmail, blocks, roadmap, topicPercentages, degraded),
            isDegraded = degraded
        )
    }

    /**
     * The student's answer and the criteria - not the reference answer, and not
     * the stylesheet. The client already holds both.
     */
    private fun buildAdjudicationPrompt(
        chunk: List<QuestionAnswer>,
        verdicts: Map<Int, AnswerGrader.Result>,
        entries: Map<Int, com.example.embeddedsystemscareerguide.models.AnswerKeyEntry>
    ): String {
        val body = chunk.joinToString("\n\n") { q ->
            val points = entries[q.keyId]?.keyPoints
                ?.joinToString("; ") { kp -> kp.any.firstOrNull().orEmpty() }
                .orEmpty()
            val coverage = verdicts[q.keyId]?.coverage ?: 0.0
            val flagged = verdicts[q.keyId]?.matchedMisconceptions.orEmpty()
            // Framed as a hypothesis, never a verdict. These triggers are short
            // substrings that demonstrably fire on correct answers, so the model
            // must check the claim rather than act on it - but sending nothing
            // at all made every authored note in the key dead weight and let a
            // student holding a documented misconception score full marks.
            val misconceptionLine = if (flagged.isEmpty()) "" else
                "\n            POSSIBLE MISCONCEPTION (verify against the answer, " +
                    "do not assume it is present): " + flagged.joinToString("; ")
            """
            ID: ${q.keyId}
            QUESTION: ${q.q}
            STUDENT ANSWER: ${InputSanitizer.sanitizeForApi(q.u, 1200)}
            EXPECTED POINTS: $points
            AUTOMATED COVERAGE: ${(coverage * 100).toInt()}%${misconceptionLine}
            """.trimIndent()
        }
        return """
You are grading embedded systems assessment answers.

For EACH item below, judge the student's answer against the expected points.

Return ONLY a JSON array, no prose, no markdown fence:
[{"id": <the ID given>, "score": <0-100>, "feedback": "<2-3 sentences>"}]

Rules:
- Use the exact ID given for each item. Include every ID, once.
- feedback is PLAIN TEXT addressed to the student. No HTML, no markdown.
- Say what is missing or wrong and how to fix it. Do not restate their answer.
- The automated coverage is a hint, not a verdict - a well-explained answer
  using different words deserves credit.

$body
""".trimIndent()
    }

    private fun buildRoadmapPrompt(userName: String, topics: Map<String, Int>): String {
        val table = topics.entries.sortedBy { it.value }
            .joinToString("\n") { "- ${it.key}: ${it.value}%" }
        // Deliberately the topic table and not the 50-question transcript: the
        // fine-tuned model was trained on score tables, so this is also closer
        // to its training distribution than the transcript ever was.
        return """
Student: ${esc(userName)}

Assessment results by topic (lowest first):
$table

Write a focused 12-week learning roadmap for this student in HTML.
Use <h3> for each phase and <ul><li> for its goals. Weight the early weeks
toward the lowest-scoring topics. No <html>, <head> or <body> wrapper, no
markdown, no preamble.
""".trimIndent()
    }

    internal fun parseAdjudication(raw: String, expectedIds: List<Int>): List<Adjudicated> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) {
            Log.w(TAG, "Adjudication response contained no JSON array")
            return emptyList()
        }
        return try {
            val arr = gson.fromJson(raw.substring(start, end + 1), JsonArray::class.java)
            arr.mapNotNull { el ->
                // Per element: a single malformed entry (a string score, a
                // non-object) used to throw out to the outer catch and discard
                // all five gradings in the chunk, which with no retry meant they
                // were simply lost.
                runCatching {
                val o = el.asJsonObject
                val id = o.get("id")?.asInt ?: return@runCatching null
                // Ignore ids we did not ask about rather than letting the model
                // overwrite a question it was never shown.
                if (id !in expectedIds) return@runCatching null
                Adjudicated(
                    id = id,
                    score = (o.get("score")?.asInt ?: 0).coerceIn(0, 100),
                    feedbackText = o.get("feedback")?.asString.orEmpty()
                )
                }.getOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Adjudication JSON did not parse", e)
            emptyList()
        }
    }

    /**
     * One question's block.
     *
     * Escaping has two lanes here and reversing them is either a stored-XSS
     * sink or double-escaped code samples. Anything derived from the student or
     * produced by the model goes through [esc]; only the authored
     * modelAnswerHtml, which ships in the APK and has already been sanitised,
     * is emitted as markup.
     */
    internal fun renderQuestionBlock(
        q: QuestionAnswer,
        verdict: AnswerGrader.Result?,
        entry: com.example.embeddedsystemscareerguide.models.AnswerKeyEntry?,
        model: Adjudicated?
    ): String = buildString {
        append("<div class=\"question-feedback\">")
        append("<h3>Question ${q.n}</h3>")
        append("<p>").append(esc(q.q)).append("</p>")

        if (verdict?.verdict == AnswerGrader.Verdict.UNANSWERED) {
            append("<p class=\"user-answer\"><em>Not answered.</em></p>")
        } else {
            append("<div class=\"user-answer\"><strong>Your answer:</strong> ")
                .append(esc(q.u)).append("</div>")
        }

        when {
            verdict?.verdict == AnswerGrader.Verdict.UNANSWERED ->
                append("<p>Study the reference answer below, then try this question again.</p>")

            model != null ->
                // Model prose, escaped. It has read the student's free text and
                // could echo anything out of it.
                append("<p>").append(esc(model.feedbackText)).append("</p>")

            verdict?.verdict == AnswerGrader.Verdict.CORRECT ->
                // Authored by us, deterministic, makes no claim about this answer.
                append(entry?.correctFeedbackHtml.orEmpty())

            else ->
                append("<p>Compare your answer with the reference answer below.</p>")
        }

        entry?.modelAnswerHtml?.takeIf { it.isNotBlank() }?.let {
            append("<div class=\"correct-answer\"><strong>Reference answer:</strong>")
            append(it)
            append("</div>")
        }

        // Only show a number when the model actually judged the answer.
        //
        // Without one, the score is weighted keyword coverage, and that is not a
        // grade: run the key's own reference answers through it and 8 of 50
        // score below 80, the worst at 43, because ".data"/".bss" normalise to
        // "data"/"bss" and never contain the literal stem "data section". A
        // student writing the canonical answer would be shown "Score: 43/100".
        // Coverage is a good enough signal to decide whether to spend a model
        // call; it is not good enough to print as a mark.
        when {
            verdict?.verdict == AnswerGrader.Verdict.UNANSWERED ->
                append("<div class=\"rating\">Not answered</div>")

            model != null ->
                append("<div class=\"rating\">Score: ")
                    .append(finalScore(verdict, model)).append("/100</div>")

            verdict?.verdict == AnswerGrader.Verdict.CORRECT ->
                append("<div class=\"rating\">Covers the expected points</div>")
        }
        append("</div>")
    }

    /**
     * Reconciles the measured coverage with the model's opinion.
     *
     * The model may raise a score - a correct answer phrased in words the key
     * does not list should not be punished for it - but only so far, so a
     * generous grader cannot turn a thin answer into a strong one. An
     * unanswered question is never scored by the model at all; it did not see
     * one.
     */
    internal fun finalScore(verdict: AnswerGrader.Result?, model: Adjudicated?): Int? {
        if (verdict == null) return null
        if (verdict.verdict == AnswerGrader.Verdict.UNANSWERED) return 0
        val floor = verdict.score
        // A matched misconception deliberately does NOT cap the score.
        //
        // It used to cap at 50. But these triggers are short substrings, and
        // several are ordinary topic vocabulary a correct answer would contain -
        // "synchronous communication" appears in the correct statement that
        // UART is asynchronous unlike SPI, and Q1's own reference answer tripped
        // three of Q1's own triggers. A keyword heuristic that can fire on a
        // right answer must not be allowed to hard-cap a grade. Its job is to
        // withhold automatic certification and defer to the model, which
        // AnswerGrader already does by forcing NEEDS_MODEL - and the model, which
        // has actually read the answer, then sets the score.
        return (model?.let {
            maxOf(floor, minOf(it.score, floor + MAX_MODEL_SCORE_LIFT))
        } ?: floor).coerceIn(0, 100)
    }

    private fun wrapReport(
        userName: String,
        userEmail: String,
        blocks: String,
        roadmapHtml: String,
        topics: Map<String, Int>,
        degraded: Boolean
    ): String {
        val topicRows = topics.entries.sortedBy { it.value }.joinToString("") {
            "<li>${esc(it.key)}: ${it.value}%</li>"
        }
        val notice = if (degraded) {
            "<p class=\"user-answer\"><strong>Note:</strong> part of this report could " +
                "not be generated and uses generic content. Retake the assessment when " +
                "you are back online for a full personalised report.</p>"
        } else ""
        val roadmapSection = if (roadmapHtml.isBlank()) {
            "<p>Your roadmap could not be generated this time. The feedback above still applies.</p>"
        } else sanitizeModelHtml(roadmapHtml)
        return generateFallbackReport(
            buildString {
                append("<p>Prepared for ").append(esc(userName))
                if (userEmail.isNotBlank()) append(" (").append(esc(userEmail)).append(")")
                append("</p>")
                append(notice)
                if (topicRows.isNotEmpty()) {
                    // Labelled for what it is. These are keyword-coverage
                    // figures used to steer the learning path, not marks, and
                    // presenting them as "Topic Breakdown: 43%" reads as a grade.
                    append("<h2>Where to focus</h2>")
                    append("<p>Ranked by how much of each topic's expected points ")
                    append("your answers covered. This guides your learning path.</p>")
                    append("<ul>").append(topicRows).append("</ul>")
                }
                append(blocks)
                append("<h2>Your 12-Week Roadmap</h2>")
                append(roadmapSection)
            }
        )
    }

    private fun generateFallbackReport(feedbackContent: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Assessment Report</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
            background-color: #0f172a; 
            color: #e2e8f0; 
            padding: 1rem;
            line-height: 1.6;
        }
        .container { 
            max-width: 800px; 
            margin: 0 auto; 
            padding: 1rem;
        }
        h1 { 
            color: #a5b4fc; 
            margin-bottom: 1.5rem;
            font-size: 1.5rem;
        }
        h2 { 
            color: #818cf8; 
            margin: 1.5rem 0 1rem;
            font-size: 1.25rem;
        }
        h3 { 
            color: #c4b5fd; 
            margin: 1rem 0 0.5rem;
            font-size: 1.1rem;
        }
        .question-feedback { 
            background-color: #1e293b; 
            border-radius: 0.5rem; 
            padding: 1rem; 
            margin-bottom: 1rem;
            border-left: 4px solid #6366f1;
        }
        .user-answer { 
            background-color: #334155; 
            padding: 0.75rem; 
            border-radius: 0.25rem; 
            margin: 0.5rem 0;
        }
        .correct-answer { 
            background-color: #1e3a5f; 
            padding: 0.75rem; 
            border-radius: 0.25rem; 
            margin: 0.5rem 0;
            border-left: 3px solid #22c55e;
        }
        .rating { 
            color: #fbbf24; 
            font-weight: bold;
            margin-top: 0.5rem;
        }
        pre { 
            background-color: #020617; 
            padding: 0.75rem; 
            border-radius: 0.5rem; 
            overflow-x: auto;
            white-space: pre-wrap;
            font-size: 0.85rem;
            margin: 0.5rem 0;
        }
        blockquote {
            background-color: #1e293b;
            padding: 0.5rem;
            border-left: 3px solid #6366f1;
            margin: 0.5rem 0;
        }
        p { margin: 0.5rem 0; }
        ul, ol { margin: 0.5rem 0; padding-left: 1.5rem; }
        li { margin: 0.25rem 0; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Your Assessment Report</h1>
        
        <h2>Question Feedback</h2>
        $feedbackContent
        
        <h2>📌 Next Steps</h2>
        <p>Review the feedback for each question above and focus on the areas where improvement is needed.</p>
        
    </div>
</body>
</html>
        """.trimIndent()
    }
}

