package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * AnalyticsService - AI-Powered Learning Analytics
 * 
 * Provides comprehensive insights into learning progress, 
 * identifies knowledge gaps, and generates personalized recommendations.
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class AnalyticsService(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsService"
        
        @Volatile
        private var instance: AnalyticsService? = null
        
        fun getInstance(context: Context): AnalyticsService {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val geminiService = GeminiServiceV2.getInstance(context)
    private val firestoreManager = FirestoreManager.getInstance(context)
    private val gson = Gson()

    /**
     * Learning analytics summary
     */
    data class LearningAnalytics(
        val totalXP: Int = 0,
        val totalStages: Int = 0,
        val completedStages: Int = 0,
        val averageStars: Float = 0f,
        val totalTimeMinutes: Int = 0,
        val currentStreak: Int = 0,
        val longestStreak: Int = 0,
        val strongTopics: List<String> = emptyList(),
        val weakTopics: List<String> = emptyList(),
        val progressPercentage: Int = 0,
        val estimatedDaysToComplete: Int = 0,
        val learningPace: String = "steady"  // slow, steady, fast
    )

    /**
     * AI-generated recommendation
     */
    data class Recommendation(
        val type: String = "",  // focus, practice, review, rest
        val title: String = "",
        val description: String = "",
        val priority: Int = 0  // 1 = highest
    )

    /**
     * Callback interface for analytics
     */
    interface AnalyticsCallback {
        fun onProgress(message: String)
        fun onSuccess(analytics: LearningAnalytics, recommendations: List<Recommendation>)
        fun onError(error: String)
    }

    /**
     * Generate comprehensive learning analytics
     */
    suspend fun generateAnalytics(callback: AnalyticsCallback) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating learning analytics")
            callback.onProgress("Analyzing your progress...")

            // Collect data
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            val progress = firestoreManager.getProgress().getOrNull()

            if (stages.isEmpty()) {
                callback.onError("No learning path found. Please complete the assessment first.")
                return@withContext
            }

            // Calculate basic analytics
            val totalStages = stages.size
            val completedStages = stages.count { it.isCompleted }
            val totalXP = progress?.totalXp ?: stages.filter { it.isCompleted }.sumOf { it.xpReward }
            val totalStars = stages.sumOf { it.starsEarned }
            val averageStars = if (completedStages > 0) {
                totalStars.toFloat() / completedStages
            } else 0f
            
            val progressPercentage = (completedStages * 100) / totalStages.coerceAtLeast(1)
            
            callback.onProgress("Identifying strengths and areas for improvement...")

            // Categorize topics based on stage performance
            val (strongTopics, weakTopics) = categorizeTopics(stages)

            // Estimate completion time
            val estimatedDays = estimateCompletionDays(stages, completedStages, progress)
            
            // Determine learning pace
            val learningPace = determinePace(progress)

            val analytics = LearningAnalytics(
                totalXP = totalXP,
                totalStages = totalStages,
                completedStages = completedStages,
                averageStars = averageStars,
                totalTimeMinutes = 0,  // Would need to track this separately
                currentStreak = progress?.currentStreak ?: 0,
                longestStreak = progress?.longestStreak ?: 0,
                strongTopics = strongTopics,
                weakTopics = weakTopics,
                progressPercentage = progressPercentage,
                estimatedDaysToComplete = estimatedDays,
                learningPace = learningPace
            )

            callback.onProgress("Generating personalized recommendations...")

            // Generate AI recommendations
            val recommendations = generateRecommendations(analytics, stages)

            // Save analytics report using correct data class
            firestoreManager.saveAnalyticsReport(AnalyticsReport(
                overallAssessment = "Progress: $progressPercentage%",
                strengthsAnalysis = "Strong topics: ${analytics.strongTopics.joinToString(", ")}",
                improvementAreas = "Areas to improve: ${analytics.weakTopics.joinToString(", ")}",
                recommendations = recommendations.map { it.title },
                motivationalMessage = getMotivationalMessage(progressPercentage),
                predictedCompletionDays = estimatedDays,
                weeklyGoal = getWeeklyGoal(progressPercentage),
                timestamp = System.currentTimeMillis()
            ))

            Log.d(TAG, "Analytics generated: $progressPercentage% complete, ${recommendations.size} recommendations")
            callback.onSuccess(analytics, recommendations)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating analytics", e)
            callback.onError("Failed to generate analytics: ${e.message}")
        }
    }

    private fun getMotivationalMessage(progressPercentage: Int): String {
        return when {
            progressPercentage < 20 -> "Every expert was once a beginner. Keep going!"
            progressPercentage < 50 -> "Great momentum! You're building strong foundations."
            progressPercentage < 80 -> "Impressive progress! The finish line is in sight."
            else -> "You're almost there! Push through to mastery!"
        }
    }

    private fun getWeeklyGoal(progressPercentage: Int): String {
        return when {
            progressPercentage < 20 -> "Complete 3 new stages this week"
            progressPercentage < 50 -> "Finish 2 stages and review flashcards"
            progressPercentage < 80 -> "Complete 2 stages and retake weak quizzes"
            else -> "Finish remaining stages and start capstone project"
        }
    }

    /**
     * Categorize topics based on star performance
     */
    private fun categorizeTopics(stages: List<PersonalizedStage>): Pair<List<String>, List<String>> {
        val strongTopics = mutableListOf<String>()
        val weakTopics = mutableListOf<String>()
        
        stages.filter { it.isCompleted }.forEach { stage ->
            if (stage.starsEarned >= 3) {
                strongTopics.add(stage.title)
            } else if (stage.starsEarned == 1) {
                weakTopics.add(stage.title)
            }
        }
        
        return Pair(strongTopics.take(5), weakTopics.take(5))
    }

    /**
     * Estimate days to complete remaining stages
     */
    private fun estimateCompletionDays(
        stages: List<PersonalizedStage>,
        completedStages: Int,
        progress: UserProgress?
    ): Int {
        val remainingStages = stages.size - completedStages
        
        // Assume average of 1-2 stages per day based on pace
        val stagesPerDay = when {
            completedStages == 0 -> 1
            progress?.currentStreak ?: 0 >= 7 -> 2
            else -> 1
        }
        
        return (remainingStages / stagesPerDay.coerceAtLeast(1)).coerceAtLeast(1)
    }

    /**
     * Determine learning pace based on activity
     */
    private fun determinePace(progress: UserProgress?): String {
        return when {
            progress?.currentStreak ?: 0 >= 5 -> "fast"
            progress?.currentStreak ?: 0 >= 3 -> "steady"
            else -> "slow"
        }
    }

    /**
     * Generate AI-powered recommendations
     */
    private suspend fun generateRecommendations(
        analytics: LearningAnalytics,
        stages: List<PersonalizedStage>
    ): List<Recommendation> {
        // Try AI generation first
        try {
            val prompt = GeminiServiceV2.PromptTemplates.analytics(
                progressPercentage = analytics.progressPercentage,
                strongTopics = analytics.strongTopics,
                weakTopics = analytics.weakTopics,
                streakDays = analytics.currentStreak,
                avgQuizScore = (analytics.averageStars * 33).toInt()  // Convert 3-star to percentage
            )

            val result = geminiService.generateContent(prompt, maxOutputTokens = 2048)
            
            if (result.isSuccess) {
                val aiRecommendations = parseRecommendations(result.getOrNull() ?: "")
                if (aiRecommendations.isNotEmpty()) {
                    return aiRecommendations
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI recommendation failed, using rule-based: ${e.message}")
        }
        
        // Fallback to rule-based recommendations
        return generateRuleBasedRecommendations(analytics, stages)
    }

    /**
     * Parse AI response into recommendations
     */
    private fun parseRecommendations(response: String): List<Recommendation> {
        return try {
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            val recommendationsArray = jsonObject.getAsJsonArray("recommendations")
            
            val recommendations = mutableListOf<Recommendation>()
            
            recommendationsArray?.forEachIndexed { index, element ->
                try {
                    val obj = element.asJsonObject
                    recommendations.add(Recommendation(
                        type = obj.get("type")?.asString ?: "focus",
                        title = obj.get("title")?.asString ?: "Recommendation ${index + 1}",
                        description = obj.get("description")?.asString ?: "",
                        priority = obj.get("priority")?.asInt ?: (index + 1)
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse recommendation at index $index")
                }
            }
            
            recommendations
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse recommendations", e)
            emptyList()
        }
    }

    /**
     * Generate rule-based recommendations as fallback
     */
    private fun generateRuleBasedRecommendations(
        analytics: LearningAnalytics,
        stages: List<PersonalizedStage>
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        // Recommendation based on progress
        when {
            analytics.progressPercentage < 20 -> {
                recommendations.add(Recommendation(
                    type = "focus",
                    title = "Build Your Foundation",
                    description = "Focus on completing the early stages to build a solid understanding of embedded systems fundamentals. Aim to complete 2-3 stages this week.",
                    priority = 1
                ))
            }
            analytics.progressPercentage < 50 -> {
                recommendations.add(Recommendation(
                    type = "practice",
                    title = "Keep Up the Momentum",
                    description = "You're making great progress! Continue with your current pace and try to review completed stages using flashcards.",
                    priority = 1
                ))
            }
            analytics.progressPercentage < 80 -> {
                recommendations.add(Recommendation(
                    type = "review",
                    title = "Time for Deep Review",
                    description = "You've covered significant ground. Consider revisiting weak areas and taking the quizzes again to improve your scores.",
                    priority = 1
                ))
            }
            else -> {
                recommendations.add(Recommendation(
                    type = "practice",
                    title = "Approaching Mastery",
                    description = "Excellent progress! Focus on the remaining advanced topics and consider working on the capstone project.",
                    priority = 1
                ))
            }
        }
        
        // Recommendation for weak topics
        if (analytics.weakTopics.isNotEmpty()) {
            recommendations.add(Recommendation(
                type = "focus",
                title = "Strengthen Weak Areas",
                description = "Consider reviewing: ${analytics.weakTopics.joinToString(", ")}. Use flashcards and retake quizzes to improve your understanding.",
                priority = 2
            ))
        }
        
        // Streak recommendation
        when {
            analytics.currentStreak == 0 -> {
                recommendations.add(Recommendation(
                    type = "focus",
                    title = "Start a New Streak",
                    description = "Complete at least one stage today to begin building your learning streak. Consistency is key to retention!",
                    priority = 2
                ))
            }
            analytics.currentStreak >= 7 -> {
                recommendations.add(Recommendation(
                    type = "rest",
                    title = "Amazing Streak!",
                    description = "You've maintained a ${analytics.currentStreak}-day streak! Take a moment to celebrate, but don't forget to rest too.",
                    priority = 3
                ))
            }
        }
        
        // Average stars recommendation
        if (analytics.averageStars < 2f && analytics.completedStages > 3) {
            recommendations.add(Recommendation(
                type = "practice",
                title = "Aim for 3 Stars",
                description = "Your average is ${String.format("%.1f", analytics.averageStars)} stars. Try retaking completed quizzes to improve your scores and understanding.",
                priority = 2
            ))
        }
        
        return recommendations.sortedBy { it.priority }
    }

    /**
     * Get quick stats for dashboard display
     */
    suspend fun getQuickStats(): LearningAnalytics = withContext(Dispatchers.IO) {
        try {
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            val progress = firestoreManager.getProgress().getOrNull()
            
            val completedStages = stages.count { it.isCompleted }
            val totalStages = stages.size
            val totalXP = progress?.totalXp ?: 0
            val averageStars = if (completedStages > 0) {
                stages.sumOf { it.starsEarned }.toFloat() / completedStages
            } else 0f
            
            LearningAnalytics(
                totalXP = totalXP,
                totalStages = totalStages,
                completedStages = completedStages,
                averageStars = averageStars,
                currentStreak = progress?.currentStreak ?: 0,
                longestStreak = progress?.longestStreak ?: 0,
                progressPercentage = (completedStages * 100) / totalStages.coerceAtLeast(1)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting quick stats", e)
            LearningAnalytics()
        }
    }
}
