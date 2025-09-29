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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiReportService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // REPLACE THIS WITH YOUR ACTUAL GEMINI API KEY
    private val GEMINI_API_KEY = "AIzaSyBmKAQiHvJUHL8q8B9n_bSebTfv9RAulyA"
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$GEMINI_API_KEY"

    companion object {
        private const val TAG = "GeminiReportService"
        private const val CHUNK_SIZE = 20
    }

    /**
     * Generate complete assessment report using two-phase approach
     */
    suspend fun generateReport(
        userName: String,
        userEmail: String,
        questions: List<QuestionAnswer>
    ): String = withContext(Dispatchers.IO) {

        try {
            Log.d(TAG, "Starting report generation for ${questions.size} questions")

            // Phase 1: Generate feedback for chunks concurrently
            val feedbackChunks = generateDetailedFeedback(questions)

            // Phase 2: Generate overall report structure
            val reportShell = generateOverallReport(userName, userEmail, questions)

            // Final Assembly: Inject feedback into report
            val completeReport = assembleReport(reportShell, feedbackChunks)

            Log.d(TAG, "Report generation completed successfully")
            return@withContext completeReport

        } catch (e: Exception) {
            Log.e(TAG, "Error generating report", e)
            throw e
        }
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
2. Topic-by-Topic Analysis
3. Detailed Question Feedback (Here, you will place ONLY this exact HTML comment: <!-- QUESTION_FEEDBACK_INSERT_POINT -->)
4. Your Personalized 12-Week Embedded Systems Roadmap
5. Final Recommendations

**CRITICAL INSTRUCTIONS FOR TOPIC-BY-TOPIC ANALYSIS:**
This section must be comprehensive and directly reflect the assessment results. Based on the user's answers, provide a detailed breakdown of their knowledge gaps across all major embedded systems categories. For each category (e.g., "Embedded Systems Fundamentals & Architecture", "C Programming for Embedded Systems", "Microcontroller Peripherals & Drivers", "Real-Time Operating Systems (RTOS)", etc.), list the specific concepts where the user showed weakness in bullet points. The goal is to give the student a clear overview of their weak areas before they see the detailed question feedback.

**CRITICAL INSTRUCTIONS FOR THE 12-WEEK ROADMAP:**
This is the most important part of the report. It must be HYPER-DETAILED, SPECIFIC, PRECISE, and PRACTICAL. Do not give vague advice.

**MOBILE-OPTIMIZED FORMATTING:** You MUST NOT use tables. Instead, use the structure with `<h3>` tags for weeks and nested `<ul>` lists for daily tasks.

- **Resources:** You MUST provide specific resources. Include names of the best books and specify chapters. Include full, clickable links to the best YouTube channels or specific YouTube videos for learning the concepts for that week.
- **Projects:** Break down into concrete daily steps with specific instructions.
- **Concepts:** Be precise with technical details and practical examples.

The roadmap should follow this structure for each week:

<h3>Week 1: [Topic Name]</h3>
<p><strong>Goal:</strong> [Clear learning objective]</p>
<ul>
    <li><strong>Day 1-2: [Subtopic]</strong>
        <ul>
            <li>[Specific resource with chapter/link]</li>
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

**Hardware Recommendations:** At the beginning of the roadmap, recommend specific, affordable microcontroller boards (e.g., STM32 Nucleo, Arduino, ESP32) with links.

USER'S FULL Q&A TRANSCRIPT:
---
$userInputText
        """.trimIndent()
    }

    /**
     * Make API call to Gemini
     */
    private suspend fun callGeminiAPI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val requestBody = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray()
                val contentObject = JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray()
                    val partObject = JsonObject().apply {
                        addProperty("text", prompt)
                    }
                    partsArray.add(partObject)
                    add("parts", partsArray)
                }
                contentsArray.add(contentObject)
                add("contents", contentsArray)

                // Add generation config for better responses
                val generationConfig = JsonObject().apply {
                    addProperty("temperature", 0.7)
                    addProperty("topK", 40)
                    addProperty("topP", 0.95)
                    addProperty("maxOutputTokens", 8192)
                }
                add("generationConfig", generationConfig)
            }

            val jsonBody = gson.toJson(requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(GEMINI_API_URL)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} - $responseBody")
                throw Exception("API call failed: ${response.code}")
            }

            // Parse response
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            val content = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")[0].asJsonObject
                .get("text").asString

            return@withContext content

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            throw e
        }
    }

    /**
     * Assemble final report by injecting feedback chunks into report shell
     */
    private fun assembleReport(reportShell: String, feedbackChunks: List<String>): String {
        val combinedFeedback = feedbackChunks.joinToString("\n\n")
        return reportShell.replace(
            "<!-- QUESTION_FEEDBACK_INSERT_POINT -->",
            combinedFeedback
        )
    }
}

