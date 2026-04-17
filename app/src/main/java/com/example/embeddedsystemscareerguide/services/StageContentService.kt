package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StageContentService - AI-Powered Learning Content Generator
 * 
 * Generates comprehensive learning content for each stage on-demand.
 * Content is generated once and cached in Firestore for future access.
 * Provides Kindle-style reading experience with rich educational material.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class StageContentService(private val context: Context) {

    companion object {
        private const val TAG = "StageContentService"
        
        @Volatile
        private var instance: StageContentService? = null
        
        fun getInstance(context: Context): StageContentService {
            return instance ?: synchronized(this) {
                instance ?: StageContentService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = OllamaService.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Callback interface for content generation progress
     * NOTE: All callbacks are guaranteed to run on the main thread
     */
    interface ContentCallback {
        fun onProgress(message: String)
        fun onSuccess(content: StageContent)
        fun onError(error: String)
    }

    /**
     * Get or generate content for a stage
     * First checks Firestore cache, generates if not found
     * 
     * IMPORTANT: All callbacks are dispatched on Main thread for UI safety
     */
    suspend fun getStageContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) {
        try {
            Log.d(TAG, "Getting content for stage ${stage.id}: ${stage.title}")
            
            // Update progress on main thread
            withContext(Dispatchers.Main) {
                callback.onProgress("Loading content...")
            }

            // Check if content already exists in Firestore (on IO thread)
            val existingContent = withContext(Dispatchers.IO) {
                firestoreManager.getStageContent(stage.id)
            }
            
            if (existingContent.isSuccess && existingContent.getOrNull() != null) {
                Log.d(TAG, "Found cached content for stage ${stage.id}")
                withContext(Dispatchers.Main) {
                    callback.onSuccess(existingContent.getOrThrow()!!)
                }
                return
            }

            // Generate new content
            withContext(Dispatchers.Main) {
                callback.onProgress("Generating learning content with AI...")
            }
            generateAndCacheContent(stage, callback)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting stage content", e)
            withContext(Dispatchers.Main) {
                callback.onError("Failed to load content: ${e.message}")
            }
        }
    }

    // ==================== RETRY HELPER ====================
    
    /**
     * Generate content with retry logic for failed parsing
     * Tries up to maxRetries times with exponential backoff
     */
    private suspend fun <T> generateWithRetry(
        promptGenerator: () -> String,
        parser: (String) -> T?,
        validator: (T?) -> Boolean,
        maxRetries: Int = 3,
        onRetry: (Int) -> Unit = {}
    ): T? = withContext(Dispatchers.IO) {
        var lastResult: T? = null
        var retryCount = 0
        
        while (retryCount < maxRetries) {
            try {
                val prompt = promptGenerator()
                val result = geminiService.generateContent(prompt, maxOutputTokens = 8192)
                
                if (result.isSuccess) {
                    val response = result.getOrNull() ?: ""
                    val parsed = parser(response)
                    
                    if (validator(parsed)) {
                        return@withContext parsed
                    }
                    
                    lastResult = parsed
                    Log.d(TAG, "Parsing returned invalid result, retrying (${retryCount + 1}/$maxRetries)")
                } else {
                    Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                }
                
                retryCount++
                if (retryCount < maxRetries) {
                    onRetry(retryCount)
                    // Exponential backoff: 1s, 2s, 4s
                    kotlinx.coroutines.delay(1000L * (1 shl (retryCount - 1)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateWithRetry", e)
                retryCount++
            }
        }
        
        // Return last result even if invalid (parsers have fallback mechanisms)
        lastResult
    }
    
    /**
     * Create simplified retry prompt with explicit JSON formatting instructions
     */
    private fun createSimplifiedKeyPointsPrompt(stageName: String, topics: List<String>): String = """
Create 8 key learning points about "$stageName" for embedded systems.
Topics: ${topics.joinToString(", ")}

Return ONLY a simple JSON array of strings, nothing else.
Example: ["Point 1 here", "Point 2 here", "Point 3 here"]

Your response must:
- Start with [ and end with ]
- Have exactly 8 items
- Each item is a string in double quotes
- No markdown, no explanation, just the array
"""
    
    /**
     * Create simplified code prompt for retry
     */
    private fun createSimplifiedCodePrompt(stageName: String, topics: List<String>, difficulty: String): String = """
Create a C code example for "$stageName" (${difficulty} level).
Topics: ${topics.joinToString(", ")}

Return ONLY valid JSON in this exact format:
{"language": "c", "code": "your code here", "explanation": "explanation here"}

CRITICAL RULES:
- Put ACTUAL newlines in code, not \n escape sequences
- Use single quotes inside strings if needed
- No markdown code blocks
- Start with { and end with }
"""

    /**
     * Create simplified tips prompt for retry
     */
    private fun createSimplifiedTipsPrompt(stageName: String, topics: List<String>): String = """
Create tips and challenge for "$stageName".

Return ONLY valid JSON:
{"commonMistakes": [{"mistake": "...", "solution": "..."}], "proTips": ["tip1", "tip2"], "miniChallenge": {"task": "...", "hint": "..."}}

- Keep strings short (under 200 chars each)
- No newlines inside strings
- Start with { and end with }
"""

    /**
     * Generate content using AI in FOUR separate API calls to avoid truncation
     * Each part is focused and detailed to maintain Stanford/MIT/IIT quality
     * 
     * Part 1: Theory (1500-2000 words, detailed academic content)
     * Part 2: Key Points (8-10 detailed points with specifics)
     * Part 3: Code Example with line-by-line explanation
     * Part 4: Common Mistakes, Pro Tips, and Mini Challenge
     */
    private suspend fun generateAndCacheContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) {
        try {
            val stageName = stage.title
            val topics = stage.topics

            // ========== PART 1: DETAILED THEORY (1500-2000 words) ==========
            withContext(Dispatchers.Main) {
                callback.onProgress("Generating detailed academic theory (Part 1/4)...")
            }
            
            val theoryPrompt = buildTheoryOnlyPrompt(stageName, topics)
            val theoryResult = withContext(Dispatchers.IO) {
                geminiService.generateContent(theoryPrompt, maxOutputTokens = 8192)
            }
            
            val theory = if (theoryResult.isSuccess) {
                extractTheoryText(theoryResult.getOrNull() ?: "")
            } else {
                Log.e(TAG, "Theory generation failed: ${theoryResult.exceptionOrNull()?.message}")
                null
            }

            // ========== PART 2: KEY POINTS (8-10 detailed points) WITH RETRY ==========
            withContext(Dispatchers.Main) {
                callback.onProgress("Generating key points (Part 2/4)...")
            }
            
            var keyPoints: List<String> = emptyList()
            var retryAttempt = 0
            
            // Try with main prompt first
            val keyPointsPrompt = buildKeyPointsPrompt(stageName, topics)
            val keyPointsResult = withContext(Dispatchers.IO) {
                geminiService.generateContent(keyPointsPrompt, maxOutputTokens = 4096)
            }
            
            if (keyPointsResult.isSuccess) {
                keyPoints = parseKeyPointsResponse(keyPointsResult.getOrNull() ?: "")
            }
            
            // Retry with simplified prompt if needed
            while (keyPoints.isEmpty() && retryAttempt < 2) {
                retryAttempt++
                Log.d(TAG, "Retrying key points with simplified prompt (attempt $retryAttempt)")
                withContext(Dispatchers.Main) {
                    callback.onProgress("Retrying key points generation...")
                }
                kotlinx.coroutines.delay(1000)
                
                val retryResult = withContext(Dispatchers.IO) {
                    geminiService.generateContent(createSimplifiedKeyPointsPrompt(stageName, topics), maxOutputTokens = 2048)
                }
                if (retryResult.isSuccess) {
                    keyPoints = parseKeyPointsResponse(retryResult.getOrNull() ?: "")
                }
            }
            
            // Final fallback: generate default key points
            if (keyPoints.isEmpty()) {
                Log.w(TAG, "Using fallback key points for stage ${stage.id}")
                keyPoints = generateFallbackKeyPoints(stageName, topics)
            }

            // ========== PART 3: CODE EXAMPLE WITH EXPLANATION + RETRY ==========
            withContext(Dispatchers.Main) {
                callback.onProgress("Generating production-quality code example (Part 3/4)...")
            }
            
            var codeExample: CodeExample? = null
            retryAttempt = 0
            
            // Try with main prompt first
            val codePrompt = buildCodeOnlyPrompt(stageName, topics, stage.difficulty)
            val codeResult = withContext(Dispatchers.IO) {
                geminiService.generateContent(codePrompt, maxOutputTokens = 8192)
            }
            
            if (codeResult.isSuccess) {
                codeExample = parseCodeExampleResponse(codeResult.getOrNull() ?: "")
            }
            
            // Retry with simplified prompt if needed
            while (codeExample == null && retryAttempt < 2) {
                retryAttempt++
                Log.d(TAG, "Retrying code example with simplified prompt (attempt $retryAttempt)")
                withContext(Dispatchers.Main) {
                    callback.onProgress("Retrying code generation...")
                }
                kotlinx.coroutines.delay(1000)
                
                val retryResult = withContext(Dispatchers.IO) {
                    geminiService.generateContent(createSimplifiedCodePrompt(stageName, topics, stage.difficulty), maxOutputTokens = 4096)
                }
                if (retryResult.isSuccess) {
                    codeExample = parseCodeExampleResponse(retryResult.getOrNull() ?: "")
                }
            }
            
            // Final fallback: use default code example
            if (codeExample == null) {
                Log.w(TAG, "Using fallback code example for stage ${stage.id}")
                codeExample = generateFallbackCodeExample(stageName, topics, stage.difficulty)
            }

            // ========== PART 4: MISTAKES, TIPS, AND CHALLENGE + RETRY ==========
            withContext(Dispatchers.Main) {
                callback.onProgress("Generating pro tips and challenges (Part 4/4)...")
            }
            
            var tipsData: TipsData? = null
            retryAttempt = 0
            
            // Try with main prompt first
            val tipsPrompt = buildTipsAndChallengePrompt(stageName, topics)
            val tipsResult = withContext(Dispatchers.IO) {
                geminiService.generateContent(tipsPrompt, maxOutputTokens = 8192)
            }
            
            if (tipsResult.isSuccess) {
                tipsData = parseTipsAndChallengeResponse(tipsResult.getOrNull() ?: "")
            }
            
            // Retry with simplified prompt if needed
            while (tipsData == null && retryAttempt < 2) {
                retryAttempt++
                Log.d(TAG, "Retrying tips with simplified prompt (attempt $retryAttempt)")
                withContext(Dispatchers.Main) {
                    callback.onProgress("Retrying tips generation...")
                }
                kotlinx.coroutines.delay(1000)
                
                val retryResult = withContext(Dispatchers.IO) {
                    geminiService.generateContent(createSimplifiedTipsPrompt(stageName, topics), maxOutputTokens = 4096)
                }
                if (retryResult.isSuccess) {
                    tipsData = parseTipsAndChallengeResponse(retryResult.getOrNull() ?: "")
                }
            }
            
            // Final fallback: use default tips
            if (tipsData == null) {
                Log.w(TAG, "Using fallback tips for stage ${stage.id}")
                tipsData = generateFallbackTipsData(stageName, topics)
            }

            // ========== COMBINE ALL PARTS ==========
            val content = createFullContent(
                stageId = stage.id,
                theory = theory,
                keyPoints = keyPoints,
                codeExample = codeExample,
                tipsData = tipsData,
                stage = stage
            )

            // Save to Firestore on IO thread
            withContext(Dispatchers.IO) {
                firestoreManager.saveStageContent(stage.id, content)
            }
            Log.d(TAG, "Successfully generated and cached content for stage ${stage.id}")
            
            withContext(Dispatchers.Main) {
                callback.onSuccess(content)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            val fallback = createFallbackContent(stage)
            withContext(Dispatchers.Main) {
                callback.onSuccess(fallback)
            }
        }
    }

    // ==================== PART 1: THEORY ONLY PROMPT (1500-2000 words) ====================
    
    /**
     * Build prompt for PART 1: Detailed Theory Section (Stanford/MIT/IIT level)
     * This is a SEPARATE API call to ensure full 1500-2000 word content without truncation
     */
    private fun buildTheoryOnlyPrompt(stageName: String, topics: List<String>): String = """
You are a world-class Embedded Systems professor from Stanford/MIT/IIT creating ultra-comprehensive learning content.

TOPIC: "$stageName"
SUBTOPICS: ${topics.joinToString(", ")}

Generate ONLY the THEORY section. This must be at university lecture quality (1500-2000 words).

=== THEORY STRUCTURE (Use these exact markdown headers) ===

## 1. Foundational Concepts
- Mathematical and theoretical foundations with formulas where applicable
- WHY these concepts exist and WHAT problems they solve
- Historical context and evolution of these techniques
- First principles explanations suitable for understanding from scratch

## 2. Hardware Architecture Deep Dive
- Register-level operations and bit manipulation techniques
- Memory hierarchy: registers, SRAM, Flash, external memory
- Clock domains, timing diagrams, and setup/hold times
- Physical layer details: voltage levels, current requirements, signal integrity
- Specific chip/MCU examples (ARM Cortex-M, AVR, PIC, STM32, ESP32)

## 3. Software-Hardware Interface
- Memory-mapped I/O vs port-mapped I/O with address calculations
- Volatile keyword usage and why it matters
- Compiler barriers and optimization prevention
- Interrupt handling: priority levels, ISR design, context saving
- DMA operations and when to use them

## 4. Industry Applications & Standards
- Automotive: AUTOSAR, CAN/LIN protocols, ISO 26262 safety
- Medical: IEC 62304, FDA requirements, fail-safe design
- IoT: Low-power modes, wireless protocols, security considerations
- Industrial: Real-time requirements, safety standards

## 5. Advanced Considerations
- Edge cases and common failure modes
- Security vulnerabilities and mitigations
- Performance optimization strategies
- Power consumption analysis and optimization
- Debug and test strategies

CRITICAL: This is ONE focused API call. Write the FULL 1500-2000 words.

Return ONLY the theory text with markdown headers. No JSON wrapping needed.
Start directly with "## 1. Foundational Concepts"
"""

    // ==================== PART 2: KEY POINTS PROMPT ====================
    
    /**
     * Build prompt for PART 2: Key Points (8-10 detailed points with specifics)
     */
    private fun buildKeyPointsPrompt(stageName: String, topics: List<String>): String = """
You are a world-class Embedded Systems professor creating key points for: "$stageName"

TOPICS: ${topics.joinToString(", ")}

Generate exactly 8 KEY POINTS that students MUST remember.

REQUIREMENTS:
- Each point should be 1-2 sentences (50-100 words)
- Include specific numbers, timing values, or memory sizes where applicable
- Reference real hardware examples when relevant

⚠️ CRITICAL JSON FORMATTING RULES - MUST FOLLOW EXACTLY:
1. Return ONLY a valid JSON array - NO markdown, NO code blocks, NO explanations
2. Each string must use ESCAPED quotes for any inner quotes: use \" not "
3. Use \n for newlines inside strings, NEVER actual line breaks
4. NO trailing commas after the last element
5. Start with [ and end with ] - nothing else before or after

✅ CORRECT FORMAT EXAMPLE:
["Point one here with numbers like 16MHz and 4KB RAM.","Point two about ARM Cortex-M registers.","Point three with debugging tips.","Point four about timing.","Point five with specifics.","Point six with examples.","Point seven about best practices.","Point eight summary."]

❌ WRONG (DO NOT DO THIS):
- ```json [...] ``` (no code blocks!)
- [...], (no trailing comma!)
- ["Text with "quotes" inside"] (must escape inner quotes!)

Now generate exactly 8 key points as a JSON array:
"""

    // ==================== PART 3: CODE EXAMPLE PROMPT ====================
    
    /**
     * Build prompt for PART 3: Code Example
     * DYNAMIC based on difficulty level:
     * - Beginner: 15-25 lines, simple code, VERY detailed simple explanations
     * - Intermediate: 25-40 lines, moderate complexity, detailed explanations
     * - Advanced: 40-80 lines, production-quality, comprehensive explanations
     */
    private fun buildCodeOnlyPrompt(stageName: String, topics: List<String>, difficulty: String): String {
        val (codeLines, complexityDescription, explanationStyle) = when (difficulty.lowercase()) {
            "beginner" -> Triple(
                "15-25",
                """BEGINNER-FRIENDLY CODE:
- Keep it SIMPLE and SHORT (15-25 lines max)
- Use basic concepts only - no advanced optimizations
- Include lots of comments explaining EVERY line
- Avoid complex bit manipulations or nested structures
- Focus on ONE clear concept demonstration
- Use simple variable names that explain purpose""",
                """SUPER DETAILED EXPLANATION FOR BEGINNERS:
- Explain like teaching a complete beginner
- Use everyday analogies (e.g., "like a light switch...")
- Break down each line into simple words
- Explain what EACH keyword does (volatile, const, etc.)
- Don't assume ANY prior knowledge
- Make it feel like a patient teacher explaining step by step"""
            )
            "intermediate" -> Triple(
                "25-40",
                """INTERMEDIATE CODE:
- Moderate complexity (25-40 lines)
- Include proper error handling
- Show register-level operations
- Include timing considerations
- Use proper embedded C practices""",
                """DETAILED EXPLANATION:
- Explain key concepts clearly
- Use analogies where helpful
- Describe why each section matters
- Include debugging tips
- Connect to real-world applications"""
            )
            else -> Triple( // Advanced
                "40-80",
                """PRODUCTION-QUALITY CODE:
- Complex, production-ready code (40-80 lines)
- Include hardware register definitions with actual addresses
- Use proper embedded C practices: volatile, const correctness
- Show error handling and edge case management
- Include timing and performance considerations
- Must be compilable (syntactically correct)""",
                """COMPREHENSIVE EXPLANATION:
- Line-by-line breakdown
- Technical depth with hardware-level understanding
- Performance implications
- Debug strategies and common pitfalls
- Industry best practices"""
            )
        }
        
        return """
You are a senior Embedded Systems engineer creating learning content for: "$stageName" (${difficulty.uppercase()} level)

TOPICS: ${topics.joinToString(", ")}

Generate a complete embedded C code example appropriate for ${difficulty.uppercase()} learners.

=== CODE REQUIREMENTS ($codeLines lines) ===
$complexityDescription

=== EXPLANATION REQUIREMENTS ===
$explanationStyle

CRITICAL: The explanation must be VERY DETAILED and use SIMPLE WORDS that anyone can understand.
Every line of code should be explained with WHY it's needed and WHAT would happen without it.

⚠️ CRITICAL JSON FORMATTING RULES - MANDATORY:
1. Return ONLY valid JSON - NO markdown, NO code blocks (no ``` anywhere)
2. Start with { and end with } - NOTHING else before or after
3. Use exactly 3 fields: "language", "code", "explanation"

4. FOR THE "code" FIELD - ESCAPE EVERYTHING:
   - Replace ALL newlines with literal \n (two characters: backslash + n)
   - Replace ALL double quotes with \" (backslash + quote)
   - Replace ALL backslashes with \\\\ (double escaped)
   - The entire code must be ONE continuous string with NO actual line breaks

5. FOR THE "explanation" FIELD:
   - Replace newlines with \n
   - Keep it as ONE continuous string

6. NO trailing commas (no comma before } or ])

✅ CORRECT EXAMPLE (note: all on one line, \n not actual newlines):
{"language":"c","code":"#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello\");\n    return 0;\n}","explanation":"Line 1: Include standard I/O library for printf function.\nLine 3: Main function entry point.\nLine 4: Print message to console.\nLine 5: Return success code."}

❌ WRONG EXAMPLES - DO NOT DO:
- ```json {...} ``` (NO code blocks ever!)
- {"code":"#include
<stdio.h>"} (NO actual newlines inside strings - use \n!)
- {"code":"char *s = "hello";"} (quotes inside MUST be escaped: "char *s = \"hello\";")
- Missing closing bracket or brace

Generate the JSON response now (single line, properly escaped):
"""
    }

    // ==================== PART 4: TIPS AND CHALLENGE PROMPT ====================
    
    /**
     * Build prompt for PART 4: Common Mistakes, Pro Tips, and Mini Challenge
     */
    private fun buildTipsAndChallengePrompt(stageName: String, topics: List<String>): String = """
You are a senior Embedded Systems engineer creating tips content for: "$stageName"

TOPICS: ${topics.joinToString(", ")}

Generate THREE sections with the EXACT structure below:

1. commonMistakes: Array of 4-5 objects, each with "mistake" and "solution" fields
2. proTips: Array of 4-5 simple strings (practical advice)
3. miniChallenge: Object with "task" and "hint" fields

⚠️ CRITICAL JSON FORMATTING RULES - MANDATORY:
1. Return ONLY valid JSON - NO markdown, NO code blocks (no ``` anywhere)
2. Start with { and end with } - NOTHING else before or after
3. Use EXACTLY these 3 top-level fields: "commonMistakes", "proTips", "miniChallenge"

4. ESCAPE ALL SPECIAL CHARACTERS:
   - ALL double quotes inside strings: use \"
   - ALL newlines: use \n (avoid newlines, keep text short)
   - ALL backslashes: use \\

5. NO trailing commas:
   - Wrong: ["a","b",]  or  {"x":1,}
   - Right: ["a","b"]   or  {"x":1}

6. STRUCTURE MUST BE EXACTLY:
   {
     "commonMistakes": [{"mistake":"...", "solution":"..."}],
     "proTips": ["tip1", "tip2", "tip3"],
     "miniChallenge": {"task":"...", "hint":"..."}
   }

✅ CORRECT EXAMPLE (all on ONE line):
{"commonMistakes":[{"mistake":"Forgetting volatile for hardware registers","solution":"Always use volatile keyword when accessing memory-mapped I/O"},{"mistake":"Not checking return values","solution":"Always verify function return codes for errors"}],"proTips":["Use a logic analyzer to debug timing issues","Set breakpoints on ISR entry to trace interrupts","Check clock configuration first when debugging"],"miniChallenge":{"task":"Implement a debounced button reader using timer interrupts","hint":"Sample the button at 10ms intervals and require 3 consistent readings"}}

❌ WRONG - DO NOT DO:
- ```json {...} ``` (NO code blocks!)
- {"proTips":["tip1","tip2",]} (NO trailing comma before ])
- {"mistake": text} (ALL string values need quotes!)
- Missing any of the 3 required fields

Generate the JSON response now (single line):
"""

    // ==================== PARSERS FOR 4-PART SYSTEM ====================
    
    /**
     * Extract theory text from Part 1 response (plain markdown, no JSON)
     */
    private fun extractTheoryText(response: String): String? {
        return try {
            var text = response.trim()
            // Remove any JSON wrapping if present
            if (text.startsWith("{") && text.contains("\"theory\"")) {
                val jsonObject = gson.fromJson(text, JsonObject::class.java)
                text = jsonObject.get("theory")?.asString ?: text
            }
            // Remove markdown code blocks if wrapped
            text = text.replace("```markdown", "").replace("```", "").trim()
            
            // Validate we have substantial content
            if (text.length < 500) {
                Log.w(TAG, "Theory too short: ${text.length} chars")
                return null
            }
            text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract theory", e)
            null
        }
    }

    /**
     * Parse Part 2 response: Key Points array
     * Uses extractJsonArray and multiple fallback strategies
     */
    private fun parseKeyPointsResponse(response: String): List<String> {
        return try {
            // Strategy 1: Try extracting as JSON array
            val cleaned = extractJsonArray(response)
            
            // Check if it's wrapped in an object
            if (cleaned.startsWith("{")) {
                try {
                    val jsonObject = gson.fromJson(cleaned, JsonObject::class.java)
                    val array = jsonObject.getAsJsonArray("keyPoints")
                    if (array != null) {
                        return array.mapNotNull { 
                            try { it.asString?.trim() } catch (e: Exception) { null }
                        }.filter { it.isNotEmpty() }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Not a wrapped object, trying direct array")
                }
            }
            
            // Strategy 2: Direct array parsing
            try {
                val result = gson.fromJson(cleaned, Array<String>::class.java).toList()
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                Log.d(TAG, "Direct array parse failed, trying fallback extraction")
            }
            
            // Strategy 3: Regex-based extraction of quoted strings
            val points = mutableListOf<String>()
            val pattern = "\"([^\"]+)\"".toRegex()
            pattern.findAll(response).forEach { match ->
                val value = match.groupValues.getOrNull(1)?.trim()
                if (value != null && value.length > 20 && !value.startsWith("{") && !value.contains(":")) {
                    points.add(value)
                }
            }
            
            if (points.size >= 3) {
                Log.d(TAG, "Fallback extraction got ${points.size} key points")
                return points.take(10)
            }
            
            // Strategy 4: Line-based extraction (looking for bullet points)
            val lines = response.split("\n")
                .map { it.trim() }
                .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") || it.matches(Regex("^\\d+\\..*")) }
                .map { it.replace(Regex("^[-•*]\\s*"), "").replace(Regex("^\\d+\\.\\s*"), "").trim() }
                .filter { it.length > 20 }
            
            if (lines.isNotEmpty()) {
                Log.d(TAG, "Line-based extraction got ${lines.size} key points")
                return lines.take(10)
            }
            
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse key points", e)
            emptyList()
        }
    }

    /**
     * Parse Part 3 response: Code Example
     * Uses extractJsonObject with fallback code extraction
     */
    private fun parseCodeExampleResponse(response: String): CodeExample? {
        return try {
            // Strategy 1: Standard JSON extraction
            val cleaned = extractJsonObject(response)
            
            try {
                val jsonObject = gson.fromJson(cleaned, JsonObject::class.java)
                val code = jsonObject.get("code")?.asString
                val explanation = jsonObject.get("explanation")?.asString
                
                if (!code.isNullOrEmpty()) {
                    return CodeExample(
                        language = jsonObject.get("language")?.asString ?: "c",
                        code = code,
                        explanation = explanation ?: "Code example for the topic"
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Standard JSON parse failed for code, trying fallback")
            }
            
            // Strategy 2: Extract code from markdown code blocks
            val codeBlockPattern = "```(?:c|cpp|C|CPP)?\\s*([\\s\\S]*?)```".toRegex()
            val codeMatch = codeBlockPattern.find(response)
            
            if (codeMatch != null) {
                val code = codeMatch.groupValues.getOrNull(1)?.trim()
                if (!code.isNullOrEmpty() && code.length > 20) {
                    // Extract explanation from remaining text
                    val remainingText = response.replace(codeMatch.value, "").trim()
                    val explanation = if (remainingText.length > 50) {
                        remainingText.take(2000)
                    } else {
                        "Code example demonstrating the embedded systems concept."
                    }
                    
                    Log.d(TAG, "Extracted code from markdown block")
                    return CodeExample(
                        language = "c",
                        code = code,
                        explanation = explanation
                    )
                }
            }
            
            // Strategy 3: Look for code-like content
            val lines = response.split("\n")
            val codeLines = lines.filter { line ->
                line.contains("#include") || line.contains("#define") ||
                line.contains("void ") || line.contains("int ") ||
                line.contains("//") || line.contains("/*") ||
                line.trim().endsWith(";") || line.trim().endsWith("{") || line.trim().endsWith("}")
            }
            
            if (codeLines.size >= 5) {
                val extractedCode = codeLines.joinToString("\n")
                Log.d(TAG, "Extracted ${codeLines.size} lines of code-like content")
                return CodeExample(
                    language = "c",
                    code = extractedCode,
                    explanation = "Code example for the embedded systems topic."
                )
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse code example", e)
            null
        }
    }

    /**
     * Parse Part 4 response: Tips and Challenge
     * Uses extractJsonObject with fallback line extraction
     */
    private fun parseTipsAndChallengeResponse(response: String): TipsData? {
        return try {
            // Strategy 1: Standard JSON extraction
            val cleaned = extractJsonObject(response)
            
            try {
                val jsonObject = gson.fromJson(cleaned, JsonObject::class.java)
                
                val commonMistakes = try {
                    jsonObject.getAsJsonArray("commonMistakes")?.mapNotNull { elem ->
                        try {
                            val obj = elem.asJsonObject
                            Mistake(
                                mistake = obj.get("mistake")?.asString ?: obj.get("error")?.asString ?: "",
                                solution = obj.get("solution")?.asString ?: obj.get("correctSolution")?.asString ?: obj.get("fix")?.asString ?: ""
                            )
                        } catch (e: Exception) { null }
                    }?.filter { it.mistake.isNotEmpty() } ?: emptyList()
                } catch (e: Exception) { emptyList() }
                
                val proTips = try {
                    jsonObject.getAsJsonArray("proTips")?.mapNotNull { 
                        try { it.asString?.trim() } catch (e: Exception) { null }
                    }?.filter { it.isNotEmpty() } ?: emptyList()
                } catch (e: Exception) { emptyList() }
                
                val challengeObj = try { jsonObject.getAsJsonObject("miniChallenge") } catch (e: Exception) { null }
                val miniChallenge = if (challengeObj != null) {
                    Challenge(
                        task = challengeObj.get("task")?.asString ?: "",
                        hint = challengeObj.get("hint")?.asString ?: ""
                    )
                } else {
                    Challenge(task = "", hint = "")
                }
                
                if (commonMistakes.isNotEmpty() || proTips.isNotEmpty() || miniChallenge.task.isNotEmpty()) {
                    return TipsData(commonMistakes, proTips, miniChallenge)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Standard JSON parse failed for tips, trying fallback")
            }
            
            // Strategy 2: Line-based extraction
            val lines = response.split("\n").map { it.trim() }
            
            // Extract mistakes (look for "mistake" or "error" patterns)
            val mistakes = mutableListOf<Mistake>()
            val tips = mutableListOf<String>()
            var challengeTask = ""
            var challengeHint = ""
            
            var i = 0
            while (i < lines.size) {
                val line = lines[i].lowercase()
                
                when {
                    line.contains("mistake") || line.contains("error") || line.contains("wrong") -> {
                        val mistakeText = lines.getOrNull(i)?.replace(Regex("^[-•*\\d.]+\\s*"), "") ?: ""
                        val solutionText = lines.getOrNull(i + 1)?.replace(Regex("^[-•*\\d.]+\\s*"), "") ?: ""
                        if (mistakeText.length > 10) {
                            mistakes.add(Mistake(mistake = mistakeText, solution = solutionText))
                            i++
                        }
                    }
                    line.contains("tip:") || line.contains("pro tip") -> {
                        val tipText = lines.getOrNull(i)?.replace(Regex("^[-•*\\d.]+\\s*"), "")?.replace(Regex("(?i)pro tip:?"), "")?.trim() ?: ""
                        if (tipText.length > 10) {
                            tips.add(tipText)
                        }
                    }
                    line.contains("challenge") || line.contains("exercise") -> {
                        challengeTask = lines.getOrNull(i + 1)?.replace(Regex("^[-•*\\d.]+\\s*"), "") ?: ""
                        challengeHint = lines.getOrNull(i + 2)?.replace(Regex("^[-•*\\d.]+\\s*"), "") ?: ""
                        i += 2
                    }
                }
                i++
            }
            
            if (mistakes.isNotEmpty() || tips.isNotEmpty() || challengeTask.isNotEmpty()) {
                Log.d(TAG, "Fallback extraction got ${mistakes.size} mistakes, ${tips.size} tips")
                return TipsData(
                    commonMistakes = mistakes,
                    proTips = tips,
                    miniChallenge = Challenge(task = challengeTask, hint = challengeHint)
                )
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tips and challenge", e)
            null
        }
    }

    // ==================== FALLBACK CONTENT GENERATORS ====================
    
    /**
     * Generate fallback key points when parsing fails completely
     * Creates reasonable default content based on topic names
     */
    private fun generateFallbackKeyPoints(stageName: String, topics: List<String>): List<String> {
        Log.d(TAG, "Generating fallback key points for $stageName")
        val points = mutableListOf<String>()
        
        // Generate points based on topics
        topics.take(4).forEachIndexed { index, topic ->
            points.add("Understanding $topic is essential for embedded systems development. This concept forms the foundation for reliable hardware-software interaction.")
            points.add("When working with $topic, always consider timing constraints, memory limitations, and power consumption requirements specific to embedded platforms.")
        }
        
        // Add generic embedded systems key points
        points.add("Always test your code on actual hardware - simulators may not accurately reflect real-world timing and behavior.")
        points.add("Use volatile keyword for hardware registers to prevent compiler optimizations that could cause incorrect behavior.")
        points.add("Document your code thoroughly, especially register configurations and timing-critical sections.")
        points.add("Consider edge cases and failure modes - embedded systems often operate in harsh or unpredictable environments.")
        
        return points.take(8)
    }
    
    /**
     * Generate fallback code example when parsing fails completely
     * Creates a basic but valid embedded C code example
     */
    private fun generateFallbackCodeExample(stageName: String, topics: List<String>, difficulty: String): CodeExample {
        Log.d(TAG, "Generating fallback code example for $stageName")
        
        val topicKeyword = topics.firstOrNull()?.split(" ")?.firstOrNull()?.lowercase() ?: "embedded"
        
        val code = when {
            topics.any { it.lowercase().contains("gpio") } -> """
// GPIO Example - Basic LED Control
#include <stdint.h>

#define GPIO_PORT_BASE  0x40020000
#define GPIO_MODER      (*(volatile uint32_t*)(GPIO_PORT_BASE + 0x00))
#define GPIO_ODR        (*(volatile uint32_t*)(GPIO_PORT_BASE + 0x14))
#define LED_PIN         5

void gpio_init(void) {
    // Configure pin as output (mode = 01)
    GPIO_MODER &= ~(0x3 << (LED_PIN * 2));  // Clear mode bits
    GPIO_MODER |=  (0x1 << (LED_PIN * 2));  // Set as output
}

void led_on(void) {
    GPIO_ODR |= (1 << LED_PIN);  // Set bit high
}

void led_off(void) {
    GPIO_ODR &= ~(1 << LED_PIN); // Clear bit
}

int main(void) {
    gpio_init();
    while(1) {
        led_on();
        for(volatile int i = 0; i < 100000; i++); // Simple delay
        led_off();
        for(volatile int i = 0; i < 100000; i++);
    }
    return 0;
}
""".trim()
            topics.any { it.lowercase().contains("interrupt") } -> """
// Interrupt Example - Button with ISR
#include <stdint.h>

#define NVIC_ISER       (*(volatile uint32_t*)0xE000E100)
#define EXTI_IMR        (*(volatile uint32_t*)0x40013C00)
#define EXTI_PR         (*(volatile uint32_t*)0x40013C14)
#define BUTTON_IRQ      6

volatile uint8_t button_pressed = 0;

void EXTI0_IRQHandler(void) {
    if (EXTI_PR & (1 << 0)) {  // Check pending bit
        EXTI_PR |= (1 << 0);   // Clear by writing 1
        button_pressed = 1;     // Set flag
    }
}

void interrupt_init(void) {
    EXTI_IMR |= (1 << 0);           // Unmask EXTI line 0
    NVIC_ISER |= (1 << BUTTON_IRQ); // Enable in NVIC
}

int main(void) {
    interrupt_init();
    while(1) {
        if (button_pressed) {
            button_pressed = 0;
            // Handle button press
        }
    }
    return 0;
}
""".trim()
            else -> """
// Basic Embedded C Structure
#include <stdint.h>

// Hardware register definitions
#define PERIPH_BASE     0x40000000
#define CONTROL_REG     (*(volatile uint32_t*)(PERIPH_BASE + 0x00))
#define STATUS_REG      (*(volatile uint32_t*)(PERIPH_BASE + 0x04))
#define DATA_REG        (*(volatile uint32_t*)(PERIPH_BASE + 0x08))

// Configuration bits
#define ENABLE_BIT      (1 << 0)
#define READY_BIT       (1 << 1)

void peripheral_init(void) {
    // Enable the peripheral
    CONTROL_REG |= ENABLE_BIT;
    
    // Wait for ready
    while (!(STATUS_REG & READY_BIT));
}

uint32_t read_data(void) {
    return DATA_REG;
}

void write_data(uint32_t value) {
    DATA_REG = value;
}

int main(void) {
    peripheral_init();
    uint32_t data = read_data();
    write_data(data + 1);
    return 0;
}
""".trim()
        }
        
        val explanation = """
## Code Explanation for $stageName

This code demonstrates fundamental embedded systems concepts related to ${topics.joinToString(", ")}.

**Key Components:**

1. **Hardware Register Definitions**: We use `volatile` pointers to access memory-mapped hardware registers. The `volatile` keyword prevents the compiler from optimizing away register reads/writes.

2. **Bit Manipulation**: Embedded systems rely heavily on bit operations (`|=`, `&=`, `^=`, `~`) to set, clear, and toggle individual bits in registers.

3. **Initialization Functions**: Proper peripheral initialization is crucial before use. This typically involves enabling clocks, configuring pins, and setting operating modes.

4. **Main Loop**: Embedded systems typically run forever in a main loop, responding to events or polling for conditions.

**Best Practices Demonstrated:**
- Using `#define` for register addresses improves readability
- Using `volatile` for all hardware registers
- Waiting for hardware ready states before proceeding
- Clear, documented code structure
""".trim()

        return CodeExample(
            language = "c",
            code = code,
            explanation = explanation
        )
    }
    
    /**
     * Generate fallback tips data when parsing fails completely
     * Creates helpful tips based on general embedded systems best practices
     */
    private fun generateFallbackTipsData(stageName: String, topics: List<String>): TipsData {
        Log.d(TAG, "Generating fallback tips for $stageName")
        
        return TipsData(
            commonMistakes = listOf(
                Mistake(
                    mistake = "Forgetting to use `volatile` for hardware registers",
                    solution = "Always mark memory-mapped I/O registers as `volatile` to prevent compiler optimizations that could skip reads or writes."
                ),
                Mistake(
                    mistake = "Not checking hardware status before operations",
                    solution = "Always poll status registers or use interrupts to confirm the peripheral is ready before reading/writing data."
                ),
                Mistake(
                    mistake = "Using blocking delays in time-critical code",
                    solution = "Use timer interrupts or non-blocking state machines instead of busy-wait loops for responsive systems."
                )
            ),
            proTips = listOf(
                "Use a logic analyzer or oscilloscope to verify timing when debugging communication protocols.",
                "Always read the errata sheet for your microcontroller - it documents known hardware bugs and workarounds.",
                "Create header files with all register definitions for cleaner, more maintainable code.",
                "Use `const` for lookup tables to place them in Flash memory instead of RAM on memory-constrained devices."
            ),
            miniChallenge = Challenge(
                task = "Modify the example code to add a timeout mechanism that prevents infinite waiting if the hardware fails to respond.",
                hint = "Use a counter variable in the while loop and exit if it exceeds a maximum value. Consider what error handling should occur on timeout."
            )
        )
    }

    // ==================== JSON REPAIR UTILITIES ====================
    
    /**
     * Fix common JSON malformation issues from AI responses
     * Handles: unterminated strings, unclosed brackets/braces, invalid escapes
     */
    private fun fixMalformedJson(json: String): String {
        var fixed = json.trim()
        
        // 1. Fix unescaped control characters in strings
        fixed = fixed.replace("\t", "\\t")
            .replace("\r", "\\r")
        
        // 2. Fix unescaped newlines within strings (but preserve JSON structure newlines)
        fixed = fixNewlinesInStrings(fixed)
        
        // 3. Fix unescaped quotes within strings
        fixed = fixUnescapedQuotes(fixed)
        
        // 4. Balance brackets and braces
        fixed = balanceBracketsAndBraces(fixed)
        
        // 5. Remove trailing commas before closing brackets/braces
        fixed = fixed.replace(Regex(",\\s*\\}"), "}")
            .replace(Regex(",\\s*\\]"), "]")
        
        // 6. Fix unclosed strings at end of truncated JSON
        fixed = fixUnterminatedStrings(fixed)
        
        return fixed
    }
    
    /**
     * Fix newlines that are inside strings (need to be escaped)
     */
    private fun fixNewlinesInStrings(json: String): String {
        val result = StringBuilder()
        var inString = false
        var i = 0
        
        while (i < json.length) {
            val ch = json[i]
            
            when {
                ch == '"' && (i == 0 || json[i - 1] != '\\') -> {
                    inString = !inString
                    result.append(ch)
                }
                ch == '\n' && inString -> {
                    result.append("\\n")
                }
                else -> {
                    result.append(ch)
                }
            }
            i++
        }
        
        return result.toString()
    }
    
    /**
     * Fix unescaped quotes inside strings
     */
    private fun fixUnescapedQuotes(json: String): String {
        val result = StringBuilder()
        var inString = false
        var i = 0
        
        while (i < json.length) {
            val ch = json[i]
            val prevCh = if (i > 0) json[i - 1] else ' '
            val nextCh = if (i < json.length - 1) json[i + 1] else ' '
            
            when {
                ch == '"' && prevCh != '\\' -> {
                    if (!inString) {
                        // Starting a string
                        inString = true
                        result.append(ch)
                    } else {
                        // Check if this is end of string or an unescaped quote
                        if (nextCh == ',' || nextCh == ':' || nextCh == '}' || nextCh == ']' || nextCh == '\n') {
                            // End of string
                            inString = false
                            result.append(ch)
                        } else if (nextCh == ' ' && i + 2 < json.length && json[i + 2] in listOf(',', ':', '}', ']')) {
                            // End of string with trailing space
                            inString = false
                            result.append(ch)
                        } else {
                            // Unescaped quote inside string - escape it
                            result.append("\\\"")
                        }
                    }
                }
                else -> result.append(ch)
            }
            i++
        }
        
        return result.toString()
    }
    
    /**
     * Balance brackets and braces by adding missing closing ones
     */
    private fun balanceBracketsAndBraces(json: String): String {
        var openBraces = 0
        var openBrackets = 0
        var inString = false
        
        for (i in json.indices) {
            val ch = json[i]
            val prevCh = if (i > 0) json[i - 1] else ' '
            
            when {
                ch == '"' && prevCh != '\\' -> inString = !inString
                !inString && ch == '{' -> openBraces++
                !inString && ch == '}' -> openBraces--
                !inString && ch == '[' -> openBrackets++
                !inString && ch == ']' -> openBrackets--
            }
        }
        
        // Add missing closing brackets/braces
        val result = StringBuilder(json.trimEnd())
        
        // Remove incomplete trailing content (like unfinished strings)
        while (result.isNotEmpty() && result.last() !in listOf('}', ']', '"', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'e', 'l', 's')) {
            result.deleteCharAt(result.length - 1)
        }
        
        // Add closing brackets first, then braces
        repeat(maxOf(0, openBrackets)) { result.append(']') }
        repeat(maxOf(0, openBraces)) { result.append('}') }
        
        return result.toString()
    }
    
    /**
     * Fix unterminated strings at end of truncated JSON
     */
    private fun fixUnterminatedStrings(json: String): String {
        var result = json
        var inString = false
        var lastQuoteIndex = -1
        
        for (i in json.indices) {
            val ch = json[i]
            val prevCh = if (i > 0) json[i - 1] else ' '
            
            if (ch == '"' && prevCh != '\\') {
                inString = !inString
                lastQuoteIndex = i
            }
        }
        
        // If we're still in a string at the end, close it
        if (inString) {
            result = result.trimEnd() + "\""
        }
        
        return result
    }
    
    /**
     * Extract JSON object from response with multiple fallback strategies
     */
    private fun extractJsonObject(response: String): String {
        // Strategy 1: Remove markdown code blocks
        var cleaned = response
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        
        // Strategy 2: Find JSON object boundaries
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1)
        }
        
        return fixMalformedJson(cleaned)
    }
    
    /**
     * Extract JSON array from response with multiple fallback strategies
     */
    private fun extractJsonArray(response: String): String {
        // Strategy 1: Remove markdown code blocks
        var cleaned = response
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        
        // Strategy 2: Find JSON array boundaries
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1)
        }
        
        return fixMalformedJson(cleaned)
    }

    // ==================== COMBINE ALL 4 PARTS ====================
    
    /**
     * Create full StageContent from all 4 API call results
     */
    private fun createFullContent(
        stageId: Int,
        theory: String?,
        keyPoints: List<String>,
        codeExample: CodeExample?,
        tipsData: TipsData?,
        stage: PersonalizedStage
    ): StageContent {
        val fallback = createFallbackContent(stage)
        
        return StageContent(
            stageId = stageId,
            theory = theory?.takeIf { it.length > 500 } ?: fallback.theory,
            keyPoints = keyPoints.takeIf { it.isNotEmpty() } ?: fallback.keyPoints,
            codeExample = codeExample?.takeIf { it.code.isNotBlank() } ?: fallback.codeExample,
            commonMistakes = tipsData?.commonMistakes?.takeIf { it.isNotEmpty() } ?: fallback.commonMistakes,
            proTips = tipsData?.proTips?.takeIf { it.isNotEmpty() } ?: fallback.proTips,
            miniChallenge = tipsData?.miniChallenge?.takeIf { it.task.isNotBlank() } ?: fallback.miniChallenge,
            generatedAt = System.currentTimeMillis()
        )
    }

    // Helper data classes for 4-part generation
    private data class TipsData(
        val commonMistakes: List<Mistake>,
        val proTips: List<String>,
        val miniChallenge: Challenge
    )

    /**
     * Parse AI response into StageContent object
     * Enhanced parsing with robust error handling for malformed JSON
     */
    private fun parseContentFromResponse(response: String, stageId: Int): StageContent? {
        return try {
            // Step 1: Clean up response - remove markdown code blocks
            var cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            // Step 2: Try to extract JSON object from response if wrapped in other text
            val jsonStart = cleanedResponse.indexOf('{')
            val jsonEnd = cleanedResponse.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanedResponse = cleanedResponse.substring(jsonStart, jsonEnd + 1)
            }
            
            // Step 3: Try to fix common JSON issues
            // Sometimes the AI truncates the response or has unclosed strings
            cleanedResponse = fixMalformedJson(cleanedResponse)

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)

            // Parse key points
            val keyPoints = try {
                jsonObject.getAsJsonArray("keyPoints")?.mapNotNull { 
                    it.asString?.trim()?.takeIf { s -> s.isNotBlank() }
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse keyPoints, using empty list")
                emptyList()
            }

            // Parse code example with null safety
            val codeExampleObj = jsonObject.getAsJsonObject("codeExample")
            val codeExample = CodeExample(
                language = codeExampleObj?.get("language")?.asString ?: "c",
                code = codeExampleObj?.get("code")?.asString ?: "",
                explanation = codeExampleObj?.get("explanation")?.asString ?: ""
            )

            // Parse common mistakes with error handling
            val commonMistakes = try {
                jsonObject.getAsJsonArray("commonMistakes")?.mapNotNull { elem ->
                    try {
                        val obj = elem.asJsonObject
                        Mistake(
                            mistake = obj.get("mistake")?.asString ?: "",
                            solution = obj.get("solution")?.asString ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse commonMistakes, using empty list")
                emptyList()
            }

            // Parse pro tips with error handling
            val proTips = try {
                jsonObject.getAsJsonArray("proTips")?.mapNotNull { 
                    it.asString?.trim()?.takeIf { s -> s.isNotBlank() }
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse proTips, using empty list")
                emptyList()
            }

            // Parse mini challenge
            val challengeObj = jsonObject.getAsJsonObject("miniChallenge")
            val miniChallenge = Challenge(
                task = challengeObj?.get("task")?.asString ?: "",
                hint = challengeObj?.get("hint")?.asString ?: ""
            )

            // Get theory content
            val theory = jsonObject.get("theory")?.asString ?: ""
            
            // Validate we have meaningful content
            if (theory.isBlank() || theory.length < 100) {
                Log.w(TAG, "Theory content is too short (${theory.length} chars), marking as failed")
                return null
            }

            StageContent(
                stageId = stageId,
                theory = theory,
                keyPoints = keyPoints,
                codeExample = codeExample,
                commonMistakes = commonMistakes,
                proTips = proTips,
                miniChallenge = miniChallenge,
                generatedAt = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse content response", e)
            null
        }
    }

    /**
     * Create fallback content if AI generation fails
     */
    private fun createFallbackContent(stage: PersonalizedStage): StageContent {
        val topicsText = stage.topics.joinToString(", ")
        
        return StageContent(
            stageId = stage.id,
            theory = """
                # ${stage.title}
                
                ${stage.description}
                
                ## Overview
                
                This stage covers the following topics: $topicsText.
                
                Embedded systems are specialized computing systems designed to perform dedicated functions
                within larger mechanical or electrical systems. Unlike general-purpose computers, embedded
                systems are optimized for specific tasks, often with real-time computing constraints.
                
                ## Key Concepts
                
                In this module, you'll learn about the fundamental concepts related to ${stage.title.lowercase()}.
                These concepts form the building blocks for more advanced embedded systems development.
                
                Understanding these principles is essential for:
                - Writing efficient, reliable embedded code
                - Interfacing with hardware peripherals
                - Debugging and troubleshooting embedded systems
                - Building production-ready embedded applications
                
                ## Practical Applications
                
                The concepts covered in this stage are widely used in:
                - Consumer electronics (smartphones, smartwatches)
                - Automotive systems (engine control, infotainment)
                - Industrial automation (PLCs, robotics)
                - Medical devices (pacemakers, monitors)
                - IoT applications (smart home, wearables)
                
                Take your time to understand each concept thoroughly before moving on.
                Practice with the code examples and complete the mini-challenge to reinforce your learning.
            """.trimIndent(),
            keyPoints = listOf(
                "Embedded systems are designed for specific, dedicated functions",
                "Resource constraints (memory, processing power) require optimized code",
                "Real-time requirements often influence design decisions",
                "Hardware-software integration is a key aspect of embedded development",
                "Testing and debugging embedded systems requires specialized tools",
                "Documentation and code quality are essential for maintainability"
            ),
            codeExample = CodeExample(
                language = "c",
                code = """
// Example: Basic embedded systems pattern
#include <stdint.h>

// Define hardware register addresses
#define GPIO_BASE    0x40020000
#define GPIO_MODER   (*(volatile uint32_t *)(GPIO_BASE + 0x00))
#define GPIO_ODR     (*(volatile uint32_t *)(GPIO_BASE + 0x14))

// Pin definitions
#define LED_PIN      5

// Initialize GPIO for LED
void gpio_init(void) {
    // Set pin as output (mode = 01)
    GPIO_MODER &= ~(0x3 << (LED_PIN * 2));  // Clear bits
    GPIO_MODER |= (0x1 << (LED_PIN * 2));   // Set output mode
}

// Toggle LED state
void led_toggle(void) {
    GPIO_ODR ^= (1 << LED_PIN);  // XOR to toggle
}

// Simple delay (not production-ready)
void delay(volatile uint32_t count) {
    while(count--);
}

int main(void) {
    gpio_init();
    
    while(1) {
        led_toggle();
        delay(1000000);
    }
    
    return 0;  // Never reached
}
                """.trimIndent(),
                explanation = "This code demonstrates a basic embedded systems pattern: initializing hardware registers and implementing a simple control loop. Notice the use of volatile for hardware registers and the infinite main loop, which is typical in embedded systems."
            ),
            commonMistakes = listOf(
                Mistake(
                    mistake = "Forgetting to use 'volatile' for hardware registers",
                    solution = "Always declare hardware register pointers as volatile to prevent compiler optimizations that could skip reads/writes"
                ),
                Mistake(
                    mistake = "Using blocking delays in production code",
                    solution = "Use timer interrupts or RTOS delays instead of busy-wait loops for better CPU utilization"
                ),
                Mistake(
                    mistake = "Not initializing peripherals before use",
                    solution = "Always configure GPIO mode, clock, and other settings before accessing peripheral registers"
                )
            ),
            proTips = listOf(
                "Always check the datasheet for exact register addresses and bit positions",
                "Use header files provided by chip manufacturers when available",
                "Create abstraction layers to make code portable across different MCUs",
                "Use debugging tools like JTAG/SWD to step through code and inspect registers"
            ),
            miniChallenge = Challenge(
                task = "Modify the LED toggle example to blink the LED at exactly 1 Hz (1 second on, 1 second off) using a timer interrupt instead of a delay loop.",
                hint = "Look up the timer peripheral for your target MCU. You'll need to configure the timer prescaler and period to generate 1-second intervals."
            ),
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Force regenerate content for a stage
     */
    suspend fun regenerateContent(
        stage: PersonalizedStage,
        callback: ContentCallback
    ) {
        withContext(Dispatchers.Main) {
            callback.onProgress("Regenerating content with AI...")
        }
        generateAndCacheContent(stage, callback)
    }

    /**
     * Preload content for next few stages (background loading)
     */
    suspend fun preloadNextStages(currentStageId: Int, count: Int = 2) = withContext(Dispatchers.IO) {
        try {
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: return@withContext
            
            // Get next few stages after current
            val nextStages = stages.filter { it.id > currentStageId }.take(count)
            
            nextStages.forEach { stage ->
                // Check if already cached
                val existing = firestoreManager.getStageContent(stage.id)
                if (existing.isFailure || existing.getOrNull() == null) {
                    // Generate in background
                    Log.d(TAG, "Preloading content for stage ${stage.id}")
                    generateAndCacheContent(stage, object : ContentCallback {
                        override fun onProgress(message: String) {}
                        override fun onSuccess(content: StageContent) {
                            Log.d(TAG, "Preloaded stage ${stage.id}")
                        }
                        override fun onError(error: String) {
                            Log.w(TAG, "Failed to preload stage ${stage.id}: $error")
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading stages", e)
        }
    }
}
