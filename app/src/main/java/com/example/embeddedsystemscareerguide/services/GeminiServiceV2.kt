package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * GeminiServiceV2 - Unified AI Service for V2.0 Features
 * 
 * Provides centralized access to Gemini API with:
 * - Prompt template system for different features
 * - Response caching layer
 * - Token usage tracking
 * - Error handling with retry logic
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class GeminiServiceV2(private val context: Context) {

    companion object {
        private const val TAG = "GeminiServiceV2"
        
        // API Configuration
        private const val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"
        
        // Retry Configuration
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
        
        // Singleton instance
        @Volatile
        private var instance: GeminiServiceV2? = null
        
        fun getInstance(context: Context): GeminiServiceV2 {
            return instance ?: synchronized(this) {
                instance ?: GeminiServiceV2(context.applicationContext).also { instance = it }
            }
        }
    }

    // OkHttp client with appropriate timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Token usage tracking
    private var totalInputTokens = 0L
    private var totalOutputTokens = 0L

    /**
     * Prompt Templates for different AI features
     */
    object PromptTemplates {
        
        /**
         * Generate personalized learning stages based on assessment results
         */
        fun personalizedStages(
            userName: String,
            weakAreas: List<String>,
            strongAreas: List<String>,
            targetStageCount: Int = 40
        ): String = """
You are an expert Embedded Systems curriculum designer. Create a personalized learning path for $userName.

ASSESSMENT RESULTS:
- Weak Areas (needs focus): ${weakAreas.joinToString(", ")}
- Strong Areas (brief review): ${strongAreas.joinToString(", ")}

TASK: Generate exactly $targetStageCount learning stages as a progressive curriculum.

REQUIREMENTS:
1. Start with foundational concepts the student is weak in
2. Build complexity gradually
3. Include practical projects every 5-6 stages
4. Cover: C Programming, Microcontrollers, Protocols, RTOS, IoT, Debugging
5. Allocate more stages to weak areas
6. Each stage should take 1-2 hours to complete

RESPOND WITH ONLY THIS JSON (no markdown, no explanation):
{
  "stages": [
    {
      "id": 1,
      "title": "Stage Title",
      "subtitle": "Brief description",
      "description": "What student will learn in 2-3 sentences",
      "topics": ["topic1", "topic2", "topic3"],
      "difficulty": "beginner|intermediate|advanced",
      "estimatedMinutes": 60,
      "type": "theory|practical|project",
      "xpReward": 100
    }
  ]
}
"""

        /**
         * Generate learning content for a specific stage
         * HIGH-QUALITY ACADEMIC CONTENT: Focused and Complete
         */
        fun stageContent(stageName: String, topics: List<String>): String = """
You are an expert Embedded Systems professor creating focused learning content for: "$stageName"

TOPICS TO COVER: ${topics.joinToString(", ")}

CRITICAL: Your response MUST be valid, complete JSON. Keep content concise but comprehensive.

=== CONTENT REQUIREMENTS ===

1. THEORY (600-800 words, well-structured):
- Start with WHY this topic matters in embedded systems
- Explain core concepts with practical context
- Include register-level details where relevant
- Add real-world application examples (automotive, IoT, medical)
- Cover common pitfalls professionals encounter

2. KEY_POINTS (5-7 actionable bullet points):
- Each point must include specific details (numbers, timing values, etc.)
- Focus on what learners MUST remember

3. CODE_EXAMPLE (20-40 lines of practical C code):
- Complete, runnable embedded C example
- Include register definitions if applicable
- Add clear comments explaining each section
- Show best practices (volatile, proper initialization)

4. CODE_EXPLANATION (3-4 paragraphs):
- Explain line by line in SIMPLE words
- Use analogies where helpful
- Describe what happens when the code runs

5. COMMON_MISTAKES (3-4 mistakes):
- Specific mistake description
- Why it's wrong technically
- How to fix it

6. PRO_TIPS (3-4 professional insights):
- Practical advice from industry experience
- Debugging tips, optimization strategies

7. MINI_CHALLENGE:
- A hands-on task to practice the concept
- Include a helpful hint

=== OUTPUT JSON FORMAT ===
{
  "theory": "Your 600-800 word explanation here...",
  "keyPoints": ["Point 1", "Point 2", "Point 3", "Point 4", "Point 5"],
  "codeExample": {
    "language": "c",
    "code": "// Your embedded C code here",
    "explanation": "Detailed explanation of the code..."
  },
  "commonMistakes": [
    {"mistake": "Description", "solution": "How to fix"}
  ],
  "proTips": ["Tip 1", "Tip 2", "Tip 3"],
  "miniChallenge": {
    "task": "Challenge description",
    "hint": "Helpful hint"
  }
}
"""

        /**
         * Regenerate personalized stages considering user's learning history
         * Used when user retakes assessment - considers their previous progress
         */
        fun regenerateStagesWithHistory(
            userName: String,
            weakAreas: List<String>,
            strongAreas: List<String>,
            performanceData: UserPerformanceData,
            targetStageCount: Int = 40
        ): String = """
You are an expert Embedded Systems curriculum designer. Create a NEW personalized learning path for $userName who is RETAKING their assessment.

PREVIOUS LEARNING HISTORY:
- Completed ${performanceData.completedStageIds.size} stages previously
- Total XP earned: ${performanceData.totalXpEarned}
- Average quiz score: ${performanceData.averageQuizScore}%
- Previous weak topics: ${performanceData.weakTopics.joinToString(", ").ifEmpty { "None identified" }}
- Previous strong topics: ${performanceData.strongTopics.joinToString(", ").ifEmpty { "None identified" }}
${if (performanceData.wrongQuestions.isNotEmpty()) {
    "- Topics with wrong answers: ${performanceData.wrongQuestions.map { it.topic }.distinct().joinToString(", ")}"
} else ""}

NEW ASSESSMENT RESULTS:
- Current Weak Areas: ${weakAreas.joinToString(", ")}
- Current Strong Areas: ${strongAreas.joinToString(", ")}

TASK: Generate exactly $targetStageCount learning stages considering BOTH their history and new assessment.

REGENERATION STRATEGY:
1. Topics they struggled with previously (low stars, wrong answers) need MORE stages
2. Topics they mastered before can have FEWER stages (they remember some)
3. If same weakness appears in both old history and new assessment, prioritize heavily
4. Strong areas from history need only brief review stages
5. Include practical projects every 5-6 stages
6. Progressive difficulty: beginner → intermediate → advanced
7. Each stage should take 1-2 hours

RESPOND WITH ONLY THIS JSON (no markdown, no explanation):
{
  "stages": [
    {
      "id": 1,
      "title": "Stage Title",
      "subtitle": "Brief description",
      "description": "What student will learn in 2-3 sentences",
      "topics": ["topic1", "topic2", "topic3"],
      "difficulty": "beginner|intermediate|advanced",
      "estimatedMinutes": 60,
      "type": "theory|practical|project|review",
      "xpReward": 100
    }
  ]
}
"""

        /**
         * Generate flashcards for a stage
         */
        fun flashcards(stageName: String, topics: List<String>, count: Int = 15): String = """
Generate $count flashcards for studying: "$stageName"
Topics: ${topics.joinToString(", ")}

Create cards that test understanding, not just memorization.
Include a mix of concept, code snippet, and application questions.

RESPOND WITH ONLY THIS JSON:
{
  "flashcards": [
    {
      "id": 1,
      "front": "Question or concept to recall",
      "back": "Answer or explanation",
      "difficulty": "easy|medium|hard",
      "category": "concept|code|application"
    }
  ]
}
"""

        /**
         * Generate quiz with explanations
         */
        fun quizWithExplanations(stageName: String, topics: List<String>, count: Int = 5): String = """
Generate $count MCQ questions for: "$stageName"
Topics: ${topics.joinToString(", ")}

Each question should have a detailed explanation for why the answer is correct.

RESPOND WITH ONLY THIS JSON:
[
  {
    "question": "Question text?",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correctAnswerIndex": 0,
    "explanation": "Detailed explanation (2-3 sentences) of why this answer is correct and others are wrong."
  }
]
"""

        /**
         * Context-aware chat response
         */
        fun contextAwareChat(
            userMessage: String,
            currentStage: String?,
            recentTopics: List<String>,
            conversationHistory: List<Pair<String, String>>
        ): String {
            val context = buildString {
                append("You are EmbedBot 🤖, an expert Embedded Systems tutor.\n\n")
                
                if (currentStage != null) {
                    append("STUDENT CONTEXT:\n")
                    append("- Currently learning: $currentStage\n")
                }
                
                if (recentTopics.isNotEmpty()) {
                    append("- Recent topics studied: ${recentTopics.takeLast(5).joinToString(", ")}\n")
                }
                
                if (conversationHistory.isNotEmpty()) {
                    append("\nRECENT CONVERSATION:\n")
                    conversationHistory.takeLast(3).forEach { (role, msg) ->
                        append("$role: $msg\n")
                    }
                }
                
                append("\nGUIDELINES:\n")
                append("- Be helpful, encouraging, and patient\n")
                append("- Relate answers to what the student is currently learning\n")
                append("- Provide code examples in C when relevant\n")
                append("- Keep responses under 300 words unless detailed explanation needed\n")
                append("- Use emojis sparingly for engagement\n\n")
                
                append("STUDENT'S QUESTION: $userMessage")
            }
            return context
        }

        /**
         * Progress analytics and insights
         */
        fun progressAnalytics(
            userName: String,
            completedStages: Int,
            totalStages: Int,
            totalXp: Int,
            quizScores: Map<String, Int>,
            streakDays: Int,
            weakTopics: List<String>,
            strongTopics: List<String>
        ): String = """
Analyze this student's learning progress and provide personalized insights.

STUDENT: $userName
PROGRESS:
- Completed: $completedStages of $totalStages stages
- Total XP: $totalXp
- Current Streak: $streakDays days
- Quiz Scores by Topic: ${quizScores.entries.joinToString(", ") { "${it.key}: ${it.value}%" }}
- Weak Topics: ${weakTopics.joinToString(", ")}
- Strong Topics: ${strongTopics.joinToString(", ")}

Generate a motivating progress report with actionable recommendations.

RESPOND WITH ONLY THIS JSON:
{
  "overallAssessment": "2-3 sentence summary of progress",
  "strengthsAnalysis": "What they're doing well",
  "improvementAreas": "Where to focus next",
  "recommendations": [
    "Specific action item 1",
    "Specific action item 2",
    "Specific action item 3"
  ],
  "motivationalMessage": "Encouraging message based on their progress",
  "predictedCompletionDays": 30,
  "weeklyGoal": "Specific goal for this week"
}
"""

        /**
         * Interview questions generation
         */
        fun interviewQuestions(
            completedTopics: List<String>,
            difficulty: String = "mixed"
        ): String = """
Generate 10 embedded systems interview questions for a student who has studied:
${completedTopics.joinToString(", ")}

Difficulty level: $difficulty

Include a mix of:
- 3 theoretical/concept questions
- 4 practical/coding questions  
- 3 problem-solving/design questions

RESPOND WITH ONLY THIS JSON:
{
  "questions": [
    {
      "id": 1,
      "question": "Interview question text",
      "type": "theoretical|practical|design",
      "difficulty": "easy|medium|hard",
      "expectedAnswer": "Key points for a good answer",
      "followUp": "A follow-up question interviewer might ask"
    }
  ]
}
"""

        /**
         * Project suggestions based on skills
         */
        fun projectSuggestions(
            completedStages: List<String>,
            skillLevel: String
        ): String = """
Suggest 5 hands-on embedded systems projects for a student who has completed:
${completedStages.joinToString(", ")}

Skill Level: $skillLevel

Projects should be practical, achievable, and progressively challenging.

RESPOND WITH ONLY THIS JSON:
{
  "projects": [
    {
      "id": 1,
      "title": "Project Name",
      "description": "2-3 sentence description",
      "difficulty": "beginner|intermediate|advanced",
      "estimatedHours": 10,
      "skills": ["skill1", "skill2"],
      "components": ["component1", "component2"],
      "learningOutcomes": ["outcome1", "outcome2"],
      "steps": ["step1", "step2", "step3"]
    }
  ]
}
"""

        /**
         * Daily learning tip
         */
        fun dailyTip(currentStage: String?, recentActivity: String): String = """
Generate a brief, helpful learning tip for an embedded systems student.

Current Focus: ${currentStage ?: "General embedded systems"}
Recent Activity: $recentActivity

The tip should be:
- Actionable and specific
- Related to their current learning
- Motivating and encouraging
- Under 100 words

RESPOND WITH ONLY THIS JSON:
{
  "tip": "The tip content",
  "category": "coding|debugging|concept|motivation|productivity",
  "actionItem": "One specific thing to try today"
}
"""

        /**
         * Code review and analysis
         */
        fun codeReview(code: String, language: String = "c"): String = """
Review this $language code for embedded systems and provide detailed feedback.

CODE:
```$language
$code
```

Analyze for:
1. Correctness and logic errors
2. Best practices for embedded systems
3. Memory efficiency
4. Potential runtime issues
5. Code style and readability

RESPOND WITH ONLY THIS JSON:
{
  "overallRating": 1-10,
  "summary": "Brief overall assessment",
  "issues": [
    {
      "severity": "error|warning|suggestion",
      "line": 1,
      "message": "What's wrong",
      "fix": "How to fix it"
    }
  ],
  "positives": ["What's good about this code"],
  "improvedCode": "The corrected/improved version of the code",
  "learningPoints": ["Key takeaways from this review"]
}
"""

        /**
         * Analytics insights for learning progress
         */
        fun analytics(
            progressPercentage: Int,
            strongTopics: List<String>,
            weakTopics: List<String>,
            streakDays: Int,
            avgQuizScore: Int
        ): String = """
Generate personalized learning recommendations for an embedded systems student.

STUDENT PROGRESS:
- Overall Progress: $progressPercentage%
- Strong Topics: ${strongTopics.joinToString(", ").ifEmpty { "None yet" }}
- Weak Topics: ${weakTopics.joinToString(", ").ifEmpty { "None identified" }}
- Current Streak: $streakDays days
- Average Quiz Score: $avgQuizScore%

Generate 3-5 actionable recommendations to help them improve.

RESPOND WITH ONLY THIS JSON:
{
  "recommendations": [
    {
      "type": "focus|practice|review|rest",
      "title": "Short action title",
      "description": "2-3 sentence actionable recommendation",
      "priority": 1
    }
  ]
}
"""

        /**
         * Generate daily learning tips
         */
        fun dailyTips(
            currentTopic: String,
            completedTopics: List<String>,
            count: Int = 7
        ): String = """
Generate $count daily learning tips for an embedded systems student.

Current Focus: $currentTopic
Completed Topics: ${completedTopics.joinToString(", ").ifEmpty { "Just starting" }}

Each tip should be practical, actionable, and related to embedded systems.
Include code snippets where relevant.

RESPOND WITH ONLY THIS JSON:
{
  "tips": [
    {
      "title": "Tip title",
      "content": "Detailed tip content (2-3 sentences)",
      "category": "programming|debugging|hardware|best-practices|career",
      "codeSnippet": "Optional C code example"
    }
  ]
}
"""

        /**
         * Interview preparation questions
         */
        fun interviewPrep(
            topics: List<String>,
            difficulty: String,
            questionCount: Int = 5
        ): String = """
Generate $questionCount embedded systems interview questions for a candidate who has studied:
${topics.joinToString(", ")}

Difficulty level: $difficulty

Include a mix of conceptual and practical questions.

RESPOND WITH ONLY THIS JSON:
{
  "questions": [
    {
      "question": "Interview question text",
      "category": "technical|behavioral|system-design",
      "difficulty": "easy|medium|hard",
      "idealAnswer": "Key points for a good answer",
      "keyPoints": ["point1", "point2"],
      "followUpQuestions": ["follow-up 1"]
    }
  ]
}
"""
    }

    /**
     * Make API call to Gemini with retry logic
     */
    suspend fun generateContent(prompt: String, maxOutputTokens: Int = 4096): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var delayMs = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = callGeminiAPI(prompt, maxOutputTokens)
                return@withContext Result.success(response)
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                lastException = e
                
                if (attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                }
            }
        }

        return@withContext Result.failure(lastException ?: Exception("Unknown error"))
    }

    /**
     * Internal API call method
     */
    private fun callGeminiAPI(prompt: String, maxOutputTokens: Int = 4096): String {
        val requestBody = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf(
                    "parts" to listOf(mapOf("text" to prompt))
                )
            )))
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.7)
                addProperty("maxOutputTokens", maxOutputTokens)
                addProperty("topP", 0.95)
            })
        }

        val request = Request.Builder()
            .url(GEMINI_API_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("API error: ${response.code} - ${response.message}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

            // Track token usage if available
            jsonResponse.getAsJsonObject("usageMetadata")?.let { usage ->
                totalInputTokens += usage.get("promptTokenCount")?.asLong ?: 0
                totalOutputTokens += usage.get("candidatesTokenCount")?.asLong ?: 0
            }

            // Extract text content
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                throw Exception("No candidates in response")
            }

            val content = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.get(0)?.asJsonObject
                ?.get("text")?.asString
                ?: throw Exception("No text in response")

            return content.trim()
        }
    }

    /**
     * Get token usage statistics
     */
    fun getTokenUsage(): Pair<Long, Long> = Pair(totalInputTokens, totalOutputTokens)

    /**
     * Reset token tracking
     */
    fun resetTokenUsage() {
        totalInputTokens = 0
        totalOutputTokens = 0
    }

    /**
     * Estimate cost based on token usage (Gemini 2.5 Flash pricing)
     */
    fun estimateCostINR(): Double {
        val inputCostPer1M = 27.0  // ₹27 per million input tokens
        val outputCostPer1M = 225.0 // ₹225 per million output tokens
        
        return (totalInputTokens * inputCostPer1M / 1_000_000) + 
               (totalOutputTokens * outputCostPer1M / 1_000_000)
    }
}
