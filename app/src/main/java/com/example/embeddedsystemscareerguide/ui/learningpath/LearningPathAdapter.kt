package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.*
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ViewStageNodeBinding

// Data class for GameStage (matching the one in LearningPathFragment)
data class GameStage(
    val id: Int,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val isUnlocked: Boolean,
    val progress: Int,
    val type: StageType,
    val xpReward: Int = 100,
    val description: String = "",
    val isCompleted: Boolean = progress == 100
)

enum class StageType {
    FOUNDATION, HARDWARE, PROGRAMMING, COMMUNICATION,
    SYSTEM, IOT, PROJECT, CAREER
}

class LearningPathAdapter(
    private val stages: List<GameStage>,
    private val onStageClick: (GameStage) -> Unit
) : RecyclerView.Adapter<LearningPathAdapter.StageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StageViewHolder {
        val binding = ViewStageNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StageViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: StageViewHolder, position: Int) {
        holder.bind(stages[position], position)
    }

    override fun getItemCount(): Int = stages.size

    inner class StageViewHolder(
        private val binding: ViewStageNodeBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        private var pulseAnimator: ObjectAnimator? = null

        fun bind(stage: GameStage, position: Int) {
            setupStageContent(stage)
            configureStageState(stage)
            setupClickListener(stage)
            startContinuousAnimations(stage)
        }

        private fun setupStageContent(stage: GameStage) {
            binding.textStageTitle.text = stage.title
            binding.textStageSubtitle.text = stage.subtitle
            binding.textXpReward.text = "+${stage.xpReward} XP"
            binding.imageStageIcon.setImageResource(stage.iconRes)
        }

        private fun configureStageState(stage: GameStage) {
            when {
                stage.isCompleted -> {
                    // Completed stage
                    binding.cardStage.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.emerald_600)
                    )
                    binding.iconComplete.visibility = View.VISIBLE
                    binding.iconLocked.visibility = View.GONE
                    binding.cardStage.alpha = 1f
                    startCompletionGlow()
                }
                stage.isUnlocked -> {
                    // Available stage
                    binding.cardStage.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.indigo_600)
                    )
                    binding.iconComplete.visibility = View.GONE
                    binding.iconLocked.visibility = View.GONE
                    binding.cardStage.alpha = 1f
                }
                else -> {
                    // Locked stage
                    binding.cardStage.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.slate_700)
                    )
                    binding.iconComplete.visibility = View.GONE
                    binding.iconLocked.visibility = View.VISIBLE
                    binding.cardStage.alpha = 0.6f
                }
            }
        }

        private fun setupClickListener(stage: GameStage) {
            binding.cardStage.setOnClickListener {
                if (stage.isUnlocked || stage.isCompleted) {
                    // Add touch animation
                    it.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            it.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()

                    onStageClick(stage)
                }
            }
        }

        private fun startContinuousAnimations(stage: GameStage) {
            if (stage.isCompleted) {
                startCompletionGlow()
            } else if (stage.isUnlocked && stage.progress > 0) {
                startProgressPulse()
            }
        }

        private fun startCompletionGlow() {
            pulseAnimator?.cancel()
            pulseAnimator = ObjectAnimator.ofFloat(binding.cardStage, "alpha", 1f, 0.7f, 1f).apply {
                duration = 2000
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }

        private fun startProgressPulse() {
            pulseAnimator?.cancel()
            pulseAnimator = ObjectAnimator.ofFloat(binding.imageStageIcon, "scaleX", 1f, 1.1f, 1f).apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE

                val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f, 1f)
                val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f, 1f)

                ObjectAnimator.ofPropertyValuesHolder(binding.imageStageIcon, scaleX, scaleY).apply {
                    duration = 1500
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
            }
        }

        // Custom positioning for the map layout
        fun setPosition(x: Float, y: Float) {
            val layoutParams = binding.root.layoutParams as? ConstraintLayout.LayoutParams
            layoutParams?.let { params ->
                params.leftMargin = x.toInt()
                params.topMargin = y.toInt()
                binding.root.layoutParams = params
            }
        }

        fun animateEntrance(delay: Long) {
            binding.root.alpha = 0f
            binding.root.scaleX = 0.5f
            binding.root.scaleY = 0.5f

            binding.root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }
}
