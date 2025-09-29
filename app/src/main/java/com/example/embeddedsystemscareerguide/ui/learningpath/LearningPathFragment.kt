package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentLearningPathBinding
import com.example.embeddedsystemscareerguide.models.LearningStage
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class LearningPathFragment : Fragment() {

    private var _binding: FragmentLearningPathBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LearningPathViewModel
    private val stages = mutableListOf<LearningStage>()
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "learning_progress"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_CURRENT_STAGE = "current_stage"
        private const val KEY_STREAK = "streak"
        private const val KEY_LAST_VISIT_DATE = "last_visit_date"
        private const val KEY_COMPLETED_STAGES = "completed_stages"
        private const val KEY_STAGE_STARS = "stage_stars_"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningPathBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[LearningPathViewModel::class.java]
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadStagesFromAssets()
        initializeProgressForNewUsers()
        loadProgressFromPreferences()
        updateStreakSystem()
        createGamePath()
        startBackgroundAnimation()
        updateHomePageProgress() // Sync progress with home page
    }

    private fun setupUI() {
        // FAB help removed as per user request
    }

    /**
     * Initialize progress for first-time users with proper defaults
     */
    private fun initializeProgressForNewUsers() {
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            // Clear any existing progress and set proper defaults
            prefs.edit()
                .putInt(KEY_TOTAL_XP, 0)           // Start with 0 XP
                .putInt(KEY_CURRENT_STAGE, 1)      // Start at stage 1
                .putInt(KEY_STREAK, 1)             // Start with 1 day streak (today)
                .putStringSet(KEY_COMPLETED_STAGES, emptySet()) // No completed stages
                .putString(KEY_LAST_VISIT_DATE, SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                .putBoolean(KEY_FIRST_LAUNCH, false)
                // Also reset home page values to ensure consistency
                .putInt("home_total_xp", 0)
                .putInt("home_current_level", 1)
                .putInt("home_streak", 1)
                .putInt("home_progress_percentage", 0)
                .putInt("home_completed_stages", 0)
                .putInt("home_total_stages", 16)
                .apply()
        }
    }

    private fun loadProgressFromPreferences() {
        // Load saved progress with proper defaults
        val totalXP = prefs.getInt(KEY_TOTAL_XP, 0)
        val currentStage = prefs.getInt(KEY_CURRENT_STAGE, 1)
        val streak = prefs.getInt(KEY_STREAK, 1)
        val completedStagesSet = prefs.getStringSet(KEY_COMPLETED_STAGES, emptySet()) ?: emptySet()

        // Update user progress display
        updateUserStats(totalXP, currentStage, streak)

        // Apply progress to stages with STRICT unlocking logic
        stages.forEach { stage ->
            val stageId = stage.id
            val stageNumber = stageId.toIntOrNull() ?: 1

            stage.isCompleted = completedStagesSet.contains(stageId)

            // STRICT unlocking logic: ONLY stage 1 unlocked initially
            stage.isUnlocked = when {
                stageNumber == 1 -> true // ONLY Stage 1 is unlocked at start
                else -> {
                    // All other stages require previous stage to be completed
                    val prevStageId = (stageNumber - 1).toString()
                    completedStagesSet.contains(prevStageId)
                }
            }

            // Load stars for completed stages only
            if (stage.isCompleted) {
                stage.starsEarned = prefs.getInt(KEY_STAGE_STARS + stageId, 3)
            } else {
                stage.starsEarned = 0
            }
        }
    }

    private fun updateStreakSystem() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastVisit = prefs.getString(KEY_LAST_VISIT_DATE, "")
        val currentStreak = prefs.getInt(KEY_STREAK, 1)

        val newStreak = when {
            lastVisit.isNullOrEmpty() -> 1 // First visit
            lastVisit == today -> currentStreak // Same day, keep streak
            isYesterday(lastVisit) -> currentStreak + 1 // Consecutive day, increase streak
            else -> 1 // Streak broken, reset to 1
        }

        // Save updated streak and visit date
        prefs.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_VISIT_DATE, today)
            .apply()
    }

    private fun isYesterday(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val lastDate = format.parse(dateString)
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.time

            format.format(lastDate) == format.format(yesterday)
        } catch (e: Exception) {
            false
        }
    }

    private fun loadStagesFromAssets() {
        try {
            val inputStream = requireContext().assets.open("learning_stages_curriculum.json")
            val reader = InputStreamReader(inputStream)
            val gson = Gson()

            val curriculumType = object : TypeToken<Map<String, Any>>() {}.type
            val curriculum = gson.fromJson<Map<String, Any>>(reader, curriculumType)

            val stagesArray = curriculum["stages"] as List<Map<String, Any>>

            stages.clear()
            stagesArray.forEach { stageMap ->
                // Safely cast topics array with proper type handling
                val topicsRaw = stageMap["topics"]
                val topics = when (topicsRaw) {
                    is List<*> -> topicsRaw.mapNotNull { it as? String }
                    else -> emptyList()
                }

                val stage = LearningStage(
                    id = when (val idRaw = stageMap["id"]) {
                        is Double -> idRaw.toInt().toString()
                        is Int -> idRaw.toString()
                        is String -> idRaw
                        else -> "1"
                    },
                    title = stageMap["title"] as? String ?: "Unknown Stage",
                    subtitle = stageMap["subtitle"] as? String ?: "Learning Content",
                    description = stageMap["description"] as? String ?: "Stage description",
                    icon = stageMap["icon"] as? String ?: "ic_foundations",
                    iconRes = getIconResourceForStage(when (val idRaw = stageMap["id"]) {
                        is Double -> idRaw.toInt()
                        is Int -> idRaw
                        is String -> idRaw.toIntOrNull() ?: 1
                        else -> 1
                    }),
                    color = stageMap["color"] as? String ?: "#818CF8",
                    xpReward = when (val xpRaw = stageMap["xp_reward"]) {
                        is Double -> xpRaw.toInt()
                        is Int -> xpRaw
                        else -> 100
                    },
                    topics = topics,
                    unlockRequirement = stageMap["unlock_requirement"] as? String ?: "none",
                    estimatedDuration = stageMap["estimated_duration"] as? String ?: "3 hours",
                    order = when (val idRaw = stageMap["id"]) {
                        is Double -> idRaw.toInt()
                        is Int -> idRaw
                        is String -> idRaw.toIntOrNull() ?: 1
                        else -> 1
                    },
                    type = getStageType(when (val idRaw = stageMap["id"]) {
                        is Double -> idRaw.toInt()
                        is Int -> idRaw
                        is String -> idRaw.toIntOrNull() ?: 1
                        else -> 1
                    }),
                    // Progress will be loaded from preferences
                    isUnlocked = false,
                    isCompleted = false,
                    starsEarned = 0,
                    progress = 0
                )
                stages.add(stage)
            }

            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to creating stages programmatically if JSON fails
            createFallbackStages()
        }
    }

    private fun createFallbackStages() {
        stages.clear()
        // Create a simplified version if JSON loading fails (now with 16 stages)
        for (i in 1..16) {
            stages.add(
                LearningStage(
                    id = i.toString(),
                    title = if (i == 16) "Final Assessment" else "Stage $i",
                    subtitle = if (i == 16) "Industry Readiness Test" else "Learning Topic $i",
                    description = if (i == 16) "Complete comprehensive assessment" else "Description for stage $i",
                    icon = if (i == 16) "ic_industry_ready" else "ic_foundations",
                    iconRes = getIconResourceForStage(i),
                    color = if (i == 16) "#DC2626" else "#818CF8",
                    xpReward = if (i == 16) 500 else (100 + (i * 20)),
                    topics = listOf("Topic 1", "Topic 2"),
                    unlockRequirement = if (i == 1) "none" else "stage_${i - 1}_completed",
                    estimatedDuration = if (i == 16) "2 hours" else "3 hours",
                    order = i,
                    type = getStageType(i),
                    isUnlocked = i == 1, // Only Stage 1 unlocked initially
                    isCompleted = false,
                    starsEarned = 0,
                    progress = 0
                )
            )
        }
    }

    private fun createGamePath() {
        val stagesContainer = binding.stagesContainer
        stagesContainer.removeAllViews()

        // Create stages in REVERSE order (16 to 1) for bottom-to-top progression
        val reversedStages = stages.sortedByDescending { it.order }

        reversedStages.forEachIndexed { index, stage ->
            val stageView = createStageNode(stage, index == reversedStages.size - 1)
            stagesContainer.addView(stageView)

            // Add entrance animation with delay
            stageView.alpha = 0f
            stageView.translationY = 50f
            stageView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
        }

        // Auto-scroll to bottom (stage 1) after stages are created
        binding.scrollViewMap.post {
            binding.scrollViewMap.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createStageNode(stage: LearningStage, isFirstStage: Boolean): View {
        val stageView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_stage_node, binding.stagesContainer, false)

        // Configure stage content
        val stageCard = stageView.findViewById<MaterialCardView>(R.id.stage_card)
        val stageIcon = stageView.findViewById<ImageView>(R.id.stage_icon)
        val stageNumber = stageView.findViewById<TextView>(R.id.stage_number)
        val stageTitle = stageView.findViewById<TextView>(R.id.stage_title)
        val stageSubtitle = stageView.findViewById<TextView>(R.id.stage_subtitle)
        val stageXP = stageView.findViewById<TextView>(R.id.stage_xp)
        val lockOverlay = stageView.findViewById<ImageView>(R.id.lock_overlay)
        val starsContainer = stageView.findViewById<LinearLayout>(R.id.stars_container)
        val connectionLineTop = stageView.findViewById<View>(R.id.connection_line_top)
        val connectionLineBottom = stageView.findViewById<View>(R.id.connection_line_bottom)

        // Set stage data
        stageNumber.text = if (stage.id == "16") "Final" else "Stage ${stage.id}"
        stageTitle.text = stage.title
        stageSubtitle.text = stage.subtitle
        stageXP.text = "+${stage.xpReward}XP" // Removed space for better fit

        // Set stage icon
        val iconResId = getIconResourceId(stage.icon)
        stageIcon.setImageResource(iconResId)

        // Configure stage state with improved visual feedback
        when {
            !stage.isUnlocked -> {
                // Locked stage - enhanced visual feedback
                lockOverlay.visibility = View.VISIBLE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.slate_600)
                stageCard.alpha = 0.5f
                stageCard.cardElevation = 4f
            }
            stage.isCompleted -> {
                // Completed stage - enhanced success state
                lockOverlay.visibility = View.GONE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.emerald_400)
                stageCard.alpha = 1f
                stageCard.cardElevation = 16f
                starsContainer.visibility = View.VISIBLE
                showStars(starsContainer, stage.starsEarned)

                // Add sparkle effects for completed stages
                stageView.findViewById<ImageView>(R.id.sparkle_left)?.visibility = View.VISIBLE
                stageView.findViewById<ImageView>(R.id.sparkle_right)?.visibility = View.VISIBLE
            }
            else -> {
                // Available/current stage - enhanced active state
                lockOverlay.visibility = View.GONE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.indigo_400)
                stageCard.alpha = 1f
                stageCard.cardElevation = 12f

                // Add subtle glow effect for current available stage
                if (stage.id.toInt() == prefs.getInt(KEY_CURRENT_STAGE, 1)) {
                    stageCard.strokeWidth = 3
                }
            }
        }

        // Hide connection lines appropriately
        if (stage.id.toInt() == 16) {
            connectionLineTop.visibility = View.GONE
        }
        if (isFirstStage) {
            connectionLineBottom.visibility = View.GONE
        }

        // Set click listener with improved feedback
        stageCard.setOnClickListener {
            onStageClicked(stage)
        }

        return stageView
    }

    private fun onStageClicked(stage: LearningStage) {
        when {
            !stage.isUnlocked -> {
                Toast.makeText(requireContext(),
                    "Complete stages from bottom to top to unlock new stages",
                    Toast.LENGTH_LONG).show()
            }
            stage.isCompleted -> {
                Toast.makeText(requireContext(),
                    "✅ Stage completed! You earned ${stage.starsEarned} stars and ${stage.xpReward} XP",
                    Toast.LENGTH_SHORT).show()
                // Could navigate to stage review or restart options
            }
            else -> {
                Toast.makeText(requireContext(),
                    "🚀 Starting \"${stage.title}\" - ${stage.estimatedDuration}",
                    Toast.LENGTH_SHORT).show()
                // For demo purposes, let's complete the stage automatically after a short delay
                simulateStageCompletion(stage)
            }
        }
    }

    /**
     * Simulate stage completion for demonstration (remove in production)
     */
    private fun simulateStageCompletion(stage: LearningStage) {
        // This is for demo purposes - in real app, completion would happen after actual learning
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            completeStage(stage.id, 3) // Complete with 3 stars
        }, 2000) // 2 second delay to simulate learning
    }

    private fun updateUserStats(totalXP: Int, currentStage: Int, streak: Int) {
        binding.textTotalXp.text = totalXP.toString()
        binding.textCurrentLevel.text = currentStage.toString()
        binding.textStreak.text = streak.toString()

        // Calculate progress based on SharedPreferences data instead of stages list
        val totalStages = 16
        val completedStagesSet = prefs.getStringSet(KEY_COMPLETED_STAGES, emptySet()) ?: emptySet()
        val completedStages = completedStagesSet.size
        val progressPercentage = if (totalStages > 0) (completedStages * 100) / totalStages else 0

        // Update progress bar with calculated percentage
        binding.progressOverall.progress = progressPercentage

        // Update progress text with actual completed stages count
        val progressText = "$progressPercentage% Complete • $completedStages of $totalStages stages"
        binding.textProgressDetail.text = progressText

        // Sync progress to home page for consistency
        updateHomePageProgress()
    }

    /**
     * Complete a stage and update all related progress
     */
    fun completeStage(stageId: String, starsEarned: Int = 3) {
        val stage = stages.find { it.id == stageId }
        if (stage != null && !stage.isCompleted) {
            // Mark stage as completed
            stage.isCompleted = true
            stage.starsEarned = starsEarned

            // Update total XP
            val currentXP = prefs.getInt(KEY_TOTAL_XP, 0)
            val newXP = currentXP + stage.xpReward

            // Update current stage to next available stage
            val nextStageId = (stageId.toInt() + 1).toString()
            val currentStageNumber = if (stages.any { it.id == nextStageId }) nextStageId.toInt() else stageId.toInt()

            // Unlock next stage
            val nextStage = stages.find { it.id == nextStageId }
            nextStage?.isUnlocked = true

            // Save to preferences
            val completedStagesSet = prefs.getStringSet(KEY_COMPLETED_STAGES, mutableSetOf<String>())?.toMutableSet() ?: mutableSetOf()
            completedStagesSet.add(stageId)

            prefs.edit()
                .putInt(KEY_TOTAL_XP, newXP)
                .putInt(KEY_CURRENT_STAGE, currentStageNumber)
                .putStringSet(KEY_COMPLETED_STAGES, completedStagesSet)
                .putInt(KEY_STAGE_STARS + stageId, starsEarned)
                .apply()

            // Update UI
            updateUserStats(newXP, currentStageNumber, prefs.getInt(KEY_STREAK, 1))

            // Refresh the path
            createGamePath()

            // Update home page progress
            updateHomePageProgress()

            // Show completion message
            Toast.makeText(requireContext(),
                "🎉 Stage completed! +${stage.xpReward} XP earned! ⭐×$starsEarned",
                Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Update home page progress to sync with learning path
     */
    private fun updateHomePageProgress() {
        val totalXP = prefs.getInt(KEY_TOTAL_XP, 0)
        val currentStage = prefs.getInt(KEY_CURRENT_STAGE, 1)
        val streak = prefs.getInt(KEY_STREAK, 1)
        val completedStages = stages.count { it.isCompleted }
        val progressPercentage = if (stages.isNotEmpty()) (completedStages * 100) / stages.size else 0

        // Store for home page to read
        prefs.edit()
            .putInt("home_total_xp", totalXP)
            .putInt("home_current_level", currentStage)
            .putInt("home_streak", streak)
            .putInt("home_progress_percentage", progressPercentage)
            .putInt("home_completed_stages", completedStages)
            .putInt("home_total_stages", stages.size)
            .apply()
    }

    private fun showHelpDialog() {
        Toast.makeText(requireContext(),
            "💡 Complete stages from bottom to top!\n🔒 Unlock new stages by completing previous ones\n⭐ Earn stars based on your performance\n🔥 Keep your daily streak alive!",
            Toast.LENGTH_LONG).show()
    }

    private fun getIconResourceId(iconName: String): Int {
        return when (iconName) {
            "ic_foundations" -> R.drawable.ic_foundations
            "ic_circuit" -> R.drawable.ic_circuit
            "ic_cpu" -> R.drawable.ic_cpu
            "ic_microcontroller" -> R.drawable.ic_microcontroller
            "ic_code" -> R.drawable.ic_code
            "ic_sensors" -> R.drawable.ic_sensors
            "ic_clock" -> R.drawable.ic_clock
            "ic_network" -> R.drawable.ic_network
            "ic_circuit_board" -> R.drawable.ic_circuit_board
            "ic_memory" -> R.drawable.ic_microchip
            "ic_rtos" -> R.drawable.ic_rtos
            "ic_iot" -> R.drawable.ic_iot
            "ic_project" -> R.drawable.ic_project
            "ic_industry_ready" -> R.drawable.ic_industry_ready
            else -> R.drawable.ic_foundations // Fallback icon
        }
    }

    private fun showStars(starsContainer: LinearLayout, starsEarned: Int) {
        val star1 = starsContainer.findViewById<ImageView>(R.id.star_1)
        val star2 = starsContainer.findViewById<ImageView>(R.id.star_2)
        val star3 = starsContainer.findViewById<ImageView>(R.id.star_3)

        // Show filled stars based on earned count
        star1.setImageResource(if (starsEarned >= 1) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        star2.setImageResource(if (starsEarned >= 2) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        star3.setImageResource(if (starsEarned >= 3) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
    }

    private fun startBackgroundAnimation() {
        val backgroundView = binding.backgroundAnimation

        // Create a subtle panning animation
        val animator = ObjectAnimator.ofFloat(backgroundView, "translationX", -50f, 50f)
        animator.duration = 10000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
    }

    /**
     * Get icon resource ID for a stage by its number
     */
    private fun getIconResourceForStage(stageId: Int): Int {
        return when (stageId) {
            1 -> R.drawable.ic_foundations
            2 -> R.drawable.ic_circuit
            3 -> R.drawable.ic_cpu
            4 -> R.drawable.ic_microcontroller
            5 -> R.drawable.ic_code
            6 -> R.drawable.ic_sensors
            7 -> R.drawable.ic_clock
            8 -> R.drawable.ic_network
            9 -> R.drawable.ic_circuit_board
            10 -> R.drawable.ic_microchip
            11 -> R.drawable.ic_rtos
            12 -> R.drawable.ic_foundations
            13 -> R.drawable.ic_project
            14 -> R.drawable.ic_network
            15 -> R.drawable.ic_iot
            16 -> R.drawable.ic_industry_ready // Icon for final assessment
            else -> R.drawable.ic_foundations
        }
    }

    /**
     * Get stage type string for categorization
     */
    private fun getStageType(stageId: Int): String {
        return when (stageId) {
            1, 2 -> "foundation"
            3, 4, 5 -> "microcontroller"
            6, 7 -> "programming"
            8, 9 -> "communication"
            10, 11 -> "realtime"
            12, 13 -> "advanced"
            14, 15 -> "iot"
            16 -> "assessment" // Type for final assessment
            else -> "foundation"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
