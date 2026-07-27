package com.example.embeddedsystemscareerguide.models

import com.example.embeddedsystemscareerguide.services.UserProgressSyncService.UserProgress

/**
 * A milestone badge, computed purely from cloud progress data - no separate
 * Firestore collection or write path is needed since every condition below is
 * already loaded whenever [UserProgress] is loaded.
 *
 * Replaces HomeFragment's achievements section, which previously called
 * setupAchievements() to unconditionally hide the RecyclerView and show the
 * "no achievements yet" empty state regardless of actual progress -
 * permanently, for every user, no matter what they'd done.
 */
data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val isEarned: (UserProgress) -> Boolean
)

object Achievements {

    /** Ordered roughly by how early a user is expected to reach each one. */
    val ALL: List<Achievement> = listOf(
        Achievement(
            id = "first_stage",
            emoji = "🎯",
            title = "First Steps",
            description = "Complete your first stage",
            isEarned = { it.completedStages.isNotEmpty() }
        ),
        Achievement(
            id = "streak_7",
            emoji = "🔥",
            title = "Week Warrior",
            description = "Reach a 7-day streak",
            isEarned = { it.bestStreak >= 7 }
        ),
        Achievement(
            id = "xp_500",
            emoji = "⭐",
            title = "Getting Started",
            description = "Earn 500 total XP",
            isEarned = { it.totalXP >= 500 }
        ),
        Achievement(
            id = "stages_half",
            emoji = "🏆",
            title = "Halfway There",
            description = "Complete 8 stages",
            isEarned = { it.completedStages.size >= 8 }
        ),
        Achievement(
            id = "streak_14",
            emoji = "🚀",
            title = "Momentum",
            description = "Reach a 14-day streak",
            isEarned = { it.bestStreak >= 14 }
        ),
        Achievement(
            id = "perfect_stage",
            emoji = "✨",
            title = "Perfectionist",
            description = "Earn 3 stars on a stage",
            isEarned = { progress -> progress.stageStars.values.any { it >= 3 } }
        ),
        Achievement(
            id = "xp_2500",
            emoji = "💎",
            title = "XP Master",
            description = "Earn 2,500 total XP",
            isEarned = { it.totalXP >= 2500 }
        ),
        Achievement(
            id = "streak_30",
            emoji = "👑",
            title = "Dedicated",
            description = "Reach a 30-day streak",
            isEarned = { it.bestStreak >= 30 }
        ),
        Achievement(
            id = "all_stages",
            emoji = "🎓",
            title = "Graduate",
            description = "Complete all 16 stages",
            isEarned = { it.completedStages.size >= 16 }
        )
    )

    fun earned(progress: UserProgress): List<Achievement> = ALL.filter { it.isEarned(progress) }
}
