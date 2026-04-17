package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * InterviewPrepService - AI-Powered Interview Preparation
 * 
 * Generates interview questions, mock interviews, and provides
 * answer evaluation and feedback for embedded systems roles.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class InterviewPrepService(private val context: Context) {

    companion object {
        private const val TAG = "InterviewPrepService"
        
        @Volatile
        private var instance: InterviewPrepService? = null
        
        fun getInstance(context: Context): InterviewPrepService {
            return instance ?: synchronized(this) {
                instance ?: InterviewPrepService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = OllamaService.getInstance(context)
    private val gson = Gson()

    /**
     * Interview question with expected answer
     */
    data class InterviewQuestion(
        val id: Int = 0,
        val question: String = "",
        val category: String = "",  // technical, behavioral, system-design
        val difficulty: String = "",  // easy, medium, hard
        val idealAnswer: String = "",
        val keyPoints: List<String> = emptyList(),
        val followUpQuestions: List<String> = emptyList()
    )

    /**
     * Answer evaluation result
     */
    data class AnswerEvaluation(
        val score: Int = 0,  // 0-100
        val feedback: String = "",
        val strengths: List<String> = emptyList(),
        val improvements: List<String> = emptyList(),
        val isAcceptable: Boolean = false
    )

    /**
     * Callback interface for interview prep operations
     */
    interface InterviewCallback {
        fun onProgress(message: String)
        fun onQuestionGenerated(question: InterviewQuestion)
        fun onEvaluationComplete(evaluation: AnswerEvaluation)
        fun onError(error: String)
    }

    /**
     * Generate interview questions based on user's progress
     */
    suspend fun generateInterviewQuestions(
        topics: List<String>,
        difficulty: String = "medium",
        count: Int = 5,
        callback: (Result<List<InterviewQuestion>>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating $count interview questions for topics: $topics")

            val prompt = OllamaService.PromptTemplates.interviewPrep(
                topics = topics,
                difficulty = difficulty,
                questionCount = count
            )

            val result = geminiService.generateContent(prompt, maxOutputTokens = 4096)
            
            if (result.isFailure) {
                Log.e(TAG, "API call failed: ${result.exceptionOrNull()?.message}")
                callback(Result.success(createFallbackQuestions(topics, difficulty, count)))
                return@withContext
            }

            val responseText = result.getOrNull() ?: ""
            val questions = parseQuestionsFromResponse(responseText)
            
            if (questions.isEmpty()) {
                callback(Result.success(createFallbackQuestions(topics, difficulty, count)))
            } else {
                callback(Result.success(questions))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating interview questions", e)
            callback(Result.failure(e))
        }
    }

    /**
     * Evaluate user's answer to an interview question
     */
    suspend fun evaluateAnswer(
        question: InterviewQuestion,
        userAnswer: String,
        callback: (Result<AnswerEvaluation>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Evaluating answer for question: ${question.question.take(50)}...")

            if (userAnswer.isBlank()) {
                callback(Result.success(AnswerEvaluation(
                    score = 0,
                    feedback = "No answer provided. Please try answering the question.",
                    improvements = listOf("Provide a complete answer addressing the question"),
                    isAcceptable = false
                )))
                return@withContext
            }

            val prompt = """
                You are an experienced embedded systems interviewer. Evaluate this interview answer.
                
                Question: ${question.question}
                Category: ${question.category}
                Difficulty: ${question.difficulty}
                
                Ideal Answer Key Points:
                ${question.keyPoints.joinToString("\n- ", prefix = "- ")}
                
                Candidate's Answer: $userAnswer
                
                Evaluate the answer on:
                1. Technical accuracy
                2. Completeness (covers key points)
                3. Clarity of explanation
                4. Practical examples mentioned
                
                Return JSON with format:
                {
                    "score": 0-100,
                    "feedback": "overall assessment",
                    "strengths": ["strength1", "strength2"],
                    "improvements": ["improvement1", "improvement2"],
                    "isAcceptable": true/false (score >= 60)
                }
            """.trimIndent()

            val result = geminiService.generateContent(prompt, maxOutputTokens = 2048)
            
            if (result.isFailure) {
                // Provide simple rule-based evaluation
                val wordCount = userAnswer.split(" ").size
                val score = when {
                    wordCount < 20 -> 30
                    wordCount < 50 -> 50
                    wordCount < 100 -> 70
                    else -> 80
                }
                
                callback(Result.success(AnswerEvaluation(
                    score = score,
                    feedback = "Your answer has been reviewed. Consider adding more technical details and examples.",
                    strengths = if (wordCount >= 50) listOf("Provided a detailed response") else emptyList(),
                    improvements = listOf("Add specific examples", "Include relevant code or technical details"),
                    isAcceptable = score >= 60
                )))
                return@withContext
            }

            val responseText = result.getOrNull() ?: ""
            val evaluation = parseEvaluationFromResponse(responseText)
            callback(Result.success(evaluation))

        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating answer", e)
            callback(Result.failure(e))
        }
    }

    /**
     * Parse questions from AI response
     */
    private fun parseQuestionsFromResponse(response: String): List<InterviewQuestion> {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val questionsArray = jsonObject.getAsJsonArray("questions")
            
            val questions = mutableListOf<InterviewQuestion>()
            
            questionsArray?.forEachIndexed { index, element ->
                try {
                    val obj = element.asJsonObject
                    questions.add(InterviewQuestion(
                        id = index + 1,
                        question = obj.get("question")?.asString ?: "",
                        category = obj.get("category")?.asString ?: "technical",
                        difficulty = obj.get("difficulty")?.asString ?: "medium",
                        idealAnswer = obj.get("idealAnswer")?.asString ?: "",
                        keyPoints = obj.getAsJsonArray("keyPoints")?.map { it.asString } ?: emptyList(),
                        followUpQuestions = obj.getAsJsonArray("followUpQuestions")?.map { it.asString } ?: emptyList()
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse question at index $index")
                }
            }
            
            questions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse questions response", e)
            emptyList()
        }
    }

    /**
     * Parse evaluation from AI response
     */
    private fun parseEvaluationFromResponse(response: String): AnswerEvaluation {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            
            AnswerEvaluation(
                score = jsonObject.get("score")?.asInt ?: 50,
                feedback = jsonObject.get("feedback")?.asString ?: "Evaluation complete.",
                strengths = jsonObject.getAsJsonArray("strengths")?.map { it.asString } ?: emptyList(),
                improvements = jsonObject.getAsJsonArray("improvements")?.map { it.asString } ?: emptyList(),
                isAcceptable = jsonObject.get("isAcceptable")?.asBoolean ?: true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse evaluation response", e)
            AnswerEvaluation(
                score = 60,
                feedback = "Your answer has been reviewed. Keep practicing!",
                isAcceptable = true
            )
        }
    }

    /**
     * Create fallback interview questions
     */
    private fun createFallbackQuestions(
        topics: List<String>,
        difficulty: String,
        count: Int
    ): List<InterviewQuestion> {
        val allQuestions = listOf(
            InterviewQuestion(
                id = 1,
                question = "Explain the difference between volatile and const keywords in embedded C.",
                category = "technical",
                difficulty = "medium",
                idealAnswer = "Volatile tells the compiler that a variable may change unexpectedly (by hardware or ISR) and should not be optimized. Const indicates the variable's value should not be modified. Both can be combined for hardware registers that the program shouldn't modify but may change.",
                keyPoints = listOf("Volatile prevents optimization", "Const prevents modification", "Can use together for read-only hardware registers"),
                followUpQuestions = listOf("When would you use volatile for a variable shared between main() and an ISR?")
            ),
            InterviewQuestion(
                id = 2,
                question = "What is the priority inversion problem in RTOS and how do you solve it?",
                category = "technical",
                difficulty = "hard",
                idealAnswer = "Priority inversion occurs when a high-priority task is blocked waiting for a resource held by a low-priority task, while a medium-priority task preempts the low-priority task. Solutions include priority inheritance (temporarily raising the low-priority task's priority) and priority ceiling protocol.",
                keyPoints = listOf("High priority blocked by low priority", "Medium priority causes delay", "Priority inheritance solution", "Priority ceiling protocol"),
                followUpQuestions = listOf("How does FreeRTOS handle priority inversion?")
            ),
            InterviewQuestion(
                id = 3,
                question = "Describe the I2C protocol and its advantages over UART.",
                category = "technical",
                difficulty = "medium",
                idealAnswer = "I2C is a synchronous two-wire protocol (SDA/SCL) supporting multiple devices with addressing. Advantages over UART include: multi-device support on same bus, built-in acknowledgment, standard addressing scheme, and only needs 2 wires regardless of device count.",
                keyPoints = listOf("Two wires: SDA and SCL", "7-bit or 10-bit addressing", "Multi-master support", "ACK/NACK mechanism"),
                followUpQuestions = listOf("What happens if two I2C devices have the same address?")
            ),
            InterviewQuestion(
                id = 4,
                question = "How would you debug a hard fault on an ARM Cortex-M processor?",
                category = "technical",
                difficulty = "hard",
                idealAnswer = "Check fault status registers (CFSR, HFSR, MMFAR, BFAR) to identify fault type. Use a hard fault handler to dump stack contents. Analyze stacked PC to find faulting instruction. Common causes include null pointer dereference, stack overflow, unaligned access, and division by zero.",
                keyPoints = listOf("Check fault status registers", "Analyze stacked PC", "Common causes: null pointer, stack overflow", "Use debugger to inspect registers"),
                followUpQuestions = listOf("What's the difference between a hard fault and a bus fault?")
            ),
            InterviewQuestion(
                id = 5,
                question = "Explain how a GPIO pin is configured as input with pull-up in a microcontroller.",
                category = "technical",
                difficulty = "easy",
                idealAnswer = "Configure mode register for input, enable internal pull-up resistor in the pull register. The pull-up ensures a defined HIGH state when the pin is not actively driven. Read state from input data register.",
                keyPoints = listOf("Set mode to input", "Enable pull-up resistor", "Prevents floating state", "Read from input register"),
                followUpQuestions = listOf("When would you use an external pull-up instead of internal?")
            ),
            InterviewQuestion(
                id = 6,
                question = "What is DMA and when would you use it in an embedded system?",
                category = "technical",
                difficulty = "medium",
                idealAnswer = "DMA (Direct Memory Access) allows peripherals to transfer data to/from memory without CPU intervention. Use it for high-throughput data transfers (ADC sampling, UART buffers, SPI/I2C bulk transfers) to free the CPU for other tasks.",
                keyPoints = listOf("Transfers without CPU", "Reduces CPU load", "Use for high-speed transfers", "Configure source, destination, count"),
                followUpQuestions = listOf("What are the limitations of DMA?")
            ),
            InterviewQuestion(
                id = 7,
                question = "How do you ensure atomic access to shared variables between main code and interrupts?",
                category = "technical",
                difficulty = "medium",
                idealAnswer = "Use critical sections (disable/enable interrupts), use atomic types/operations provided by the compiler, use single-word reads/writes that are inherently atomic on the architecture, or use lock-free data structures.",
                keyPoints = listOf("Disable interrupts temporarily", "Use atomic types", "Single-word operations", "volatile keyword is not enough"),
                followUpQuestions = listOf("What are the drawbacks of disabling interrupts for too long?")
            ),
            InterviewQuestion(
                id = 8,
                question = "Explain the boot process of a typical ARM microcontroller.",
                category = "technical",
                difficulty = "hard",
                idealAnswer = "On reset: 1) Load stack pointer from address 0, 2) Load reset vector from address 4, 3) Jump to reset handler, 4) Reset handler initializes .data section, clears .bss, 5) Calls system init (clock config), 6) Calls main(). Boot mode pins may select boot source.",
                keyPoints = listOf("Stack pointer loaded first", "Reset vector at address 4", "Initialize .data and .bss", "Configure system clocks"),
                followUpQuestions = listOf("How would you implement a bootloader?")
            )
        )
        
        return allQuestions.take(count.coerceAtMost(allQuestions.size))
    }

    /**
     * Get question categories for filtering
     */
    fun getCategories(): List<String> = listOf(
        "C Programming",
        "Microcontrollers",
        "Communication Protocols",
        "RTOS",
        "Debugging",
        "System Design",
        "Behavioral"
    )

    /**
     * Get difficulty levels
     */
    fun getDifficultyLevels(): List<String> = listOf("easy", "medium", "hard")
}
