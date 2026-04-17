package com.example.embeddedsystemscareerguide.services

import android.util.Log
import com.example.embeddedsystemscareerguide.models.QuestionAnswer
import com.google.gson.Gson
import com.google.gson.JsonObject
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
     * Generate complete assessment report using two-phase approach
     * With robust error handling to prevent blank reports
     * @param progressCallback Optional callback to report progress updates
     */
    suspend fun generateReport(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>,
        progressCallback: ProgressCallback? = null
    ): String = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "Starting report generation for ${questions.size} questions")
            
            val chunks = questions.chunked(CHUNK_SIZE)
            val totalChunks = chunks.size
            // Total phases: chunks + 1 (structuring) + 1 (finalizing)
            val totalPhases = totalChunks + 2

            // Phase 1 to N: Generate feedback for chunks
            val feedbackChunks = try {
                generateDetailedFeedbackWithProgress(questions, totalPhases, progressCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate detailed feedback, using minimal feedback", e)
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate report shell, using fallback", e)
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
            Log.d(TAG, "Report generation completed, length: ${completeReport.length} chars")
            
            if (completeReport.length < 500) {
                Log.w(TAG, "Report seems too short, may be incomplete")
            }

            return@withContext completeReport

        } catch (e: Exception) {
            Log.e(TAG, "Critical error generating report, using emergency fallback", e)
            // Emergency fallback - return basic report
            return@withContext generateEmergencyReport(userName, userEmail, questions)
        }
    }
    
    /**
     * Generate minimal feedback when API fails
     */
    private fun generateMinimalFeedback(questions: List<QuestionAnswer>): String {
        return questions.joinToString("\n") { qa ->
            """
            <div class="question-feedback">
                <h4>Question ${qa.n}: ${qa.q}</h4>
                <div class="user-answer">
                    <strong>Your Answer:</strong>
                    <blockquote>${qa.u.ifBlank { "[No answer provided]" }}</blockquote>
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
                <h4>Question ${qa.n}: ${qa.q}</h4>
                <div class="user-answer"><strong>Your Answer:</strong> ${qa.u.ifBlank { "[No answer]" }}</div>
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
    private suspend fun callGeminiAPI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBody = JsonObject().apply {
                addProperty("model", NetworkModule.DEFAULT_MODEL)
                addProperty("prompt", prompt)
                addProperty("stream", false)
                add("options", JsonObject().apply {
                    addProperty("temperature", 0.7)
                    addProperty("num_predict", 16384)
                    addProperty("top_p", 0.95)
                })
            }

            val request = Request.Builder()
                .url(NetworkModule.getOllamaGenerateUrl())
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} - $responseBody")
                throw Exception("API call failed: ${response.code}")
            }

            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val content = jsonResponse.get("response")?.asString
                ?: throw Exception("No response text from Ollama")
            
            // Strip Qwen3 <think>...</think> reasoning blocks before returning HTML content
            val cleaned = content.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
            
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
    private fun assembleReport(reportShell: String, feedbackChunks: List<String>): String {
        val combinedFeedback = feedbackChunks.joinToString("\n\n")
        
        // Check if the report shell is truncated (has DOCTYPE but no closing html tag)
        val isTruncated = reportShell.contains("<!DOCTYPE html>") && !reportShell.contains("</html>")
        
        if (isTruncated) {
            Log.w(TAG, "Report shell is truncated (has opening but no closing HTML tags)")
            // Don't try to use truncated content - use the fallback with actual feedback
            return generateFallbackReport(combinedFeedback)
        }
        
        // Validate the report shell is not blank or malformed
        val validatedShell = if (reportShell.isBlank() || !reportShell.contains("<!DOCTYPE html>")) {
            Log.w(TAG, "Report shell was blank or malformed, using fallback template")
            generateFallbackReport(combinedFeedback)
        } else {
            reportShell.replace(
                "<!-- QUESTION_FEEDBACK_INSERT_POINT -->",
                combinedFeedback
            )
        }
        
        // Final validation - ensure we have valid complete HTML
        return if (validatedShell.contains("<html") && validatedShell.contains("</html>") && validatedShell.contains("</body>")) {
            validatedShell
        } else {
            Log.w(TAG, "Final report validation failed, using emergency fallback")
            // Use fallback with feedback content instead of trying to wrap partial content
            generateFallbackReport(combinedFeedback)
        }
    }
    
    /**
     * Generate a fallback report if the main generation fails
     */
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

