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
        // Process 15 questions per chunk for detailed feedback
        private const val CHUNK_SIZE = 15
        
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
            <p><strong>Student:</strong> $userName</p>
            <p><strong>Email:</strong> $userEmail</p>
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
        val response = callGeminiAPI(prompt)

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

        val prompt = buildReportPrompt(userName, userEmail, questions)
        val response = callGeminiAPI(prompt)

        return@withContext response
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

**CRITICAL: HTML STRUCTURE & STYLING FOR MOBILE**
Your output MUST be a single, complete HTML document starting with `<!DOCTYPE html>`.
It MUST include the following `<style>` block inside the `<head>` section optimized for mobile viewing.

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
        
        <div class="user-info">
            <p><strong>Student:</strong> $userName</p>
            <p><strong>Email:</strong> $userEmail</p>
            <p><strong>Assessment Date:</strong> ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}</p>
        </div>

    </div>
</body>
</html>

**CRITICAL: REPORT STRUCTURE ORDER**
Inside the `<div class="container">`, after the user info, the final HTML report must follow this exact order of sections:
1. Overall Summary
2. Topic-by-Topic Analysis (your strengths and weaknesses by topic)
3. Detailed Question Feedback Section (IMPORTANT: For this section, you MUST output ONLY this exact HTML comment with a heading: `<h2>📝 Detailed Question-by-Question Analysis</h2><!-- QUESTION_FEEDBACK_INSERT_POINT -->` - DO NOT generate any actual question feedback here, the detailed feedback will be inserted automatically at this placeholder)
4. Your Personalized 12-Week Embedded Systems Roadmap
5. Final Recommendations & Conclusion

**CRITICAL: DO NOT GENERATE DUPLICATE QUESTION FEEDBACK**
The question-by-question feedback is generated separately and will be injected at the placeholder. You MUST NOT create your own question feedback section. Only place the heading and the exact comment `<!-- QUESTION_FEEDBACK_INSERT_POINT -->`.

**CRITICAL INSTRUCTIONS FOR TOPIC-BY-TOPIC ANALYSIS:**
This section must be comprehensive and directly reflect the assessment results. Based on the user's answers, provide a detailed breakdown of their knowledge gaps across all major embedded systems categories. For each category (e.g., "Embedded Systems Fundamentals & Architecture", "C Programming for Embedded Systems", "Microcontroller Peripherals & Drivers", "Real-Time Operating Systems (RTOS)", etc.), list the specific concepts where the user showed weakness AND strength in bullet points. The goal is to give the student a clear overview of their performance before they see the detailed question feedback.

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

Continue this format for all 12 weeks, ensuring mobile readability with proper spacing and concise but detailed content.

**Hardware Recommendations:** At the beginning of the roadmap, recommend specific, affordable microcontroller boards (e.g., STM32 Nucleo, Arduino, ESP32) with approximate prices in Indian Rupees (₹) and mention where to purchase them in India (like Amazon.in, Robu.in, or electronics stores).

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
                    throw Exception("API call failed: ${response.code}")
                }

                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
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
     * Assemble final report by injecting feedback chunks into report shell
     * With fallback handling for blank or truncated content
     */
    private fun assembleReport(reportShell: String, feedbackChunks: List<String>): ReportResult {
        val combinedFeedback = feedbackChunks.joinToString("\n\n")

        // Check if the report shell is truncated (has DOCTYPE but no closing html tag)
        val isTruncated = reportShell.contains("<!DOCTYPE html>") && !reportShell.contains("</html>")

        if (isTruncated) {
            Log.w(TAG, "Report shell is truncated (has opening but no closing HTML tags)")
            // Don't try to use truncated content - use the fallback with actual feedback
            return ReportResult(generateFallbackReport(combinedFeedback), isDegraded = true)
        }

        // Validate the report shell is not blank or malformed
        var degraded = false
        val validatedShell = if (reportShell.isBlank() || !reportShell.contains("<!DOCTYPE html>")) {
            Log.w(TAG, "Report shell was blank or malformed, using fallback template")
            degraded = true
            generateFallbackReport(combinedFeedback)
        } else {
            reportShell.replace(
                "<!-- QUESTION_FEEDBACK_INSERT_POINT -->",
                combinedFeedback
            )
        }

        // Final validation - ensure we have valid complete HTML
        return if (validatedShell.contains("<html") && validatedShell.contains("</html>") && validatedShell.contains("</body>")) {
            ReportResult(validatedShell, isDegraded = degraded)
        } else {
            Log.w(TAG, "Final report validation failed, using emergency fallback")
            // Use fallback with feedback content instead of trying to wrap partial content
            ReportResult(generateFallbackReport(combinedFeedback), isDegraded = true)
        }
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

    /** Completion caps, so one call cannot outrun the 180s read timeout. */
    private val ADJUDICATION_MAX_TOKENS = 1024
    private val ROADMAP_MAX_TOKENS = 1800

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
                val raw = callGeminiAPI(
                    buildAdjudicationPrompt(chunk, verdicts, entries),
                    maxTokens = ADJUDICATION_MAX_TOKENS,
                    temperature = 0.3
                )
                parseAdjudication(raw, chunk.map { it.keyId }).forEach {
                    // An entry with no feedback text would otherwise satisfy the
                    // omitted-id check below, render as an empty paragraph, and
                    // still carry its score lift - a blank gap where the
                    // personalised feedback should be, reported as a success.
                    if (it.feedbackText.isBlank()) {
                        Log.w(TAG, "Adjudication returned no feedback for id ${'$'}{it.id}")
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

        withContext(Dispatchers.Main) {
            progressCallback?.onProgress(
                totalPhases - 1, totalPhases,
                "Building your roadmap...", QUOTES.random()
            )
        }
        val roadmap = try {
            callGeminiAPI(
                buildRoadmapPrompt(userName, topicPercentages),
                maxTokens = ROADMAP_MAX_TOKENS,
                temperature = 0.5
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
            """
            ID: ${q.keyId}
            QUESTION: ${q.q}
            STUDENT ANSWER: ${q.u.take(1200)}
            EXPECTED POINTS: $points
            AUTOMATED COVERAGE: ${(coverage * 100).toInt()}%
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
Student: $userName

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
                val o = el.asJsonObject
                val id = o.get("id")?.asInt ?: return@mapNotNull null
                // Ignore ids we did not ask about rather than letting the model
                // overwrite a question it was never shown.
                if (id !in expectedIds) return@mapNotNull null
                Adjudicated(
                    id = id,
                    score = (o.get("score")?.asInt ?: 0).coerceIn(0, 100),
                    feedbackText = o.get("feedback")?.asString.orEmpty()
                )
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
        return model?.let {
            maxOf(floor, minOf(it.score, floor + MAX_MODEL_SCORE_LIFT))
        } ?: floor
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

