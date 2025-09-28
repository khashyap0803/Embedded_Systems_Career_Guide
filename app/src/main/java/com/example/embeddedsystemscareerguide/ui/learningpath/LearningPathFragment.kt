package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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

class LearningPathFragment : Fragment() {

    private var _binding: FragmentLearningPathBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LearningPathViewModel
    private val stages = mutableListOf<LearningStage>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningPathBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[LearningPathViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadStagesFromAssets()
        createGamePath()
        startBackgroundAnimation()
    }

    private fun setupUI() {
        // Setup floating action button
        binding.fabHelp.setOnClickListener {
            showHelpDialog()
        }

        // Update user stats (these would come from ViewModel in real implementation)
        updateUserStats(1250, 5, 15)
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
                // Safely cast topics array
                val topicsArray = stageMap["topics"] as? List<*>
                val topics = topicsArray?.mapNotNull { it as? String } ?: emptyList()

                val stage = LearningStage(
                    id = (stageMap["id"] as Double).toInt().toString(),
                    title = stageMap["title"] as String,
                    subtitle = stageMap["subtitle"] as String,
                    description = stageMap["description"] as String,
                    icon = stageMap["icon"] as String,
                    iconRes = getIconResourceForStage((stageMap["id"] as Double).toInt()),
                    color = stageMap["color"] as String,
                    xpReward = (stageMap["xp_reward"] as Double).toInt(),
                    topics = topics,
                    unlockRequirement = stageMap["unlock_requirement"] as String,
                    estimatedDuration = stageMap["estimated_duration"] as String,
                    order = (stageMap["id"] as Double).toInt(),
                    type = getStageType((stageMap["id"] as Double).toInt()),
                    // These would come from user's actual progress
                    isUnlocked = (stageMap["id"] as Double).toInt() <= 5, // First 5 stages unlocked for demo
                    isCompleted = (stageMap["id"] as Double).toInt() <= 3, // First 3 stages completed for demo
                    starsEarned = if ((stageMap["id"] as Double).toInt() <= 3) (1..3).random() else 0,
                    progress = if ((stageMap["id"] as Double).toInt() <= 3) 100 else if ((stageMap["id"] as Double).toInt() <= 5) 50 else 0
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
        // Create a simplified version if JSON loading fails
        for (i in 1..15) {
            stages.add(
                LearningStage(
                    id = i.toString(), // Convert Int to String
                    title = "Stage $i",
                    subtitle = "Learning Topic $i",
                    description = "Description for stage $i",
                    icon = "ic_foundations",
                    iconRes = getIconResourceForStage(i),
                    color = "#818CF8",
                    xpReward = 100 + (i * 20),
                    topics = listOf("Topic 1", "Topic 2"),
                    unlockRequirement = if (i == 1) "none" else "stage_${i - 1}_completed",
                    estimatedDuration = "3 hours",
                    order = i,
                    type = getStageType(i),
                    isUnlocked = i <= 5,
                    isCompleted = i <= 3,
                    starsEarned = if (i <= 3) (1..3).random() else 0,
                    progress = if (i <= 3) 100 else if (i <= 5) 50 else 0
                )
            )
        }
    }

    private fun createGamePath() {
        val stagesContainer = binding.stagesContainer
        stagesContainer.removeAllViews()

        // Create stages in REVERSE order (15 to 1) for bottom-to-top progression
        val reversedStages = stages.reversed()

        reversedStages.forEachIndexed { index, stage ->
            val stageView = createStageNode(stage, index == reversedStages.size - 1) // Last item (stage 1) is at bottom
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
        stageNumber.text = "Stage ${stage.id}"
        stageTitle.text = stage.title
        stageSubtitle.text = stage.subtitle
        stageXP.text = "+${stage.xpReward} XP"

        // Set stage icon (use fallback if specific icon not found)
        val iconResId = getIconResourceId(stage.icon)
        stageIcon.setImageResource(iconResId)

        // Configure stage state (locked/unlocked/completed)
        when {
            !stage.isUnlocked -> {
                // Locked stage
                lockOverlay.visibility = View.VISIBLE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.slate_600)
                stageCard.alpha = 0.6f
            }
            stage.isCompleted -> {
                // Completed stage
                lockOverlay.visibility = View.GONE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.emerald_400)
                starsContainer.visibility = View.VISIBLE
                showStars(starsContainer, stage.starsEarned)

                // Add sparkle effects for completed stages
                stageView.findViewById<ImageView>(R.id.sparkle_left).visibility = View.VISIBLE
                stageView.findViewById<ImageView>(R.id.sparkle_right).visibility = View.VISIBLE
            }
            else -> {
                // Available/current stage
                lockOverlay.visibility = View.GONE
                stageCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.indigo_400)
            }
        }

        // Hide connection lines appropriately
        if (stage.id.toInt() == 15) { // Convert String id to Int for comparison
            connectionLineTop.visibility = View.GONE
        }
        if (isFirstStage) { // Last stage at bottom
            connectionLineBottom.visibility = View.GONE
        }

        // Set click listener
        stageCard.setOnClickListener {
            onStageClicked(stage)
        }

        return stageView
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

    private fun onStageClicked(stage: LearningStage) {
        when {
            !stage.isUnlocked -> {
                Toast.makeText(requireContext(),
                    "🔒 Complete previous stages to unlock \"${stage.title}\"",
                    Toast.LENGTH_LONG).show()
            }
            stage.isCompleted -> {
                Toast.makeText(requireContext(),
                    "✅ Stage completed! You earned ${stage.starsEarned} stars",
                    Toast.LENGTH_SHORT).show()
                // Here you would navigate to stage review or restart options
            }
            else -> {
                Toast.makeText(requireContext(),
                    "🚀 Starting \"${stage.title}\" - ${stage.estimatedDuration}",
                    Toast.LENGTH_SHORT).show()
                // Here you would navigate to the actual learning content
            }
        }
    }

    private fun updateUserStats(totalXP: Int, currentStage: Int, streak: Int) {
        binding.textTotalXp.text = totalXP.toString()
        binding.textCurrentLevel.text = currentStage.toString()
        binding.textStreak.text = streak.toString()

        // Calculate and update progress
        val totalStages = stages.size.takeIf { it > 0 } ?: 15
        val completedStages = stages.count { it.isCompleted }
        val progressPercentage = (completedStages * 100) / totalStages

        binding.progressOverall.progress = progressPercentage

        // Update progress text
        val progressText = "$progressPercentage% Complete • $completedStages of $totalStages stages"
        // Note: You'd need to add this TextView to the layout if it doesn't exist
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

    private fun showHelpDialog() {
        Toast.makeText(requireContext(),
            "💡 Complete stages from bottom to top!\n🔒 Unlock new stages by completing previous ones\n⭐ Earn stars based on your performance",
            Toast.LENGTH_LONG).show()
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
            12 -> R.drawable.ic_iot
            13 -> R.drawable.ic_project
            14 -> R.drawable.ic_project
            15 -> R.drawable.ic_industry_ready
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
            12, 13 -> "iot"
            14 -> "advanced"
            15 -> "industry"
            else -> "foundation"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
