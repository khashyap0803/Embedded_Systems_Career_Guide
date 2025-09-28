package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.models.LearningStage
import com.example.embeddedsystemscareerguide.models.StageType
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class GameifiedStagesAdapter(
    private val stages: List<LearningStage>,
    private val onStageClick: (LearningStage) -> Unit
) : RecyclerView.Adapter<GameifiedStagesAdapter.StageViewHolder>() {

    inner class StageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stageCard: MaterialCardView = itemView.findViewById(R.id.stageCard)
        val stageIcon: ImageView = itemView.findViewById(R.id.stageIcon)
        val stageTitle: TextView = itemView.findViewById(R.id.stageTitle)
        val stageDescription: TextView = itemView.findViewById(R.id.stageDescription)
        val stageProgress: ProgressBar = itemView.findViewById(R.id.stageProgress)
        val progressText: TextView = itemView.findViewById(R.id.progressText)
        val lockIcon: ImageView = itemView.findViewById(R.id.lockIcon)
        val completionIcon: ImageView = itemView.findViewById(R.id.completionIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_learning_stage, parent, false)
        return StageViewHolder(view)
    }

    override fun onBindViewHolder(holder: StageViewHolder, position: Int) {
        val stage = stages[position]

        holder.stageTitle.text = stage.title
        holder.stageDescription.text = stage.subtitle
        holder.stageIcon.setImageResource(stage.iconRes)

        when {
            !stage.isUnlocked -> {
                // Locked state
                holder.stageCard.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.slate_600)
                holder.stageCard.alpha = 0.6f
                holder.lockIcon.visibility = View.VISIBLE
                holder.completionIcon.visibility = View.GONE
                holder.stageProgress.visibility = View.GONE
                holder.progressText.text = "🔒 Locked"
            }
            stage.isCompleted -> {
                // Completed state
                holder.stageCard.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.emerald_400)
                holder.stageCard.alpha = 1.0f
                holder.lockIcon.visibility = View.GONE
                holder.completionIcon.visibility = View.VISIBLE
                holder.stageProgress.visibility = View.VISIBLE
                holder.stageProgress.progress = 100
                holder.progressText.text = "✅ Complete"
            }
            else -> {
                // Available/In Progress state
                holder.stageCard.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.indigo_400)
                holder.stageCard.alpha = 1.0f
                holder.lockIcon.visibility = View.GONE
                holder.completionIcon.visibility = View.GONE
                holder.stageProgress.visibility = View.VISIBLE
                holder.stageProgress.progress = stage.progress
                holder.progressText.text = if (stage.progress > 0) "${stage.progress}% Complete" else "Ready to Start"
            }
        }

        // Set background color based on stage type
        val backgroundColor = when (stage.type) {
            "foundation" -> ContextCompat.getColor(holder.itemView.context, R.color.emerald_400)
            "microcontroller" -> ContextCompat.getColor(holder.itemView.context, R.color.indigo_400)
            "programming" -> ContextCompat.getColor(holder.itemView.context, R.color.purple_400)
            "communication" -> ContextCompat.getColor(holder.itemView.context, R.color.amber_400)
            "realtime" -> ContextCompat.getColor(holder.itemView.context, R.color.red_400)
            "iot" -> ContextCompat.getColor(holder.itemView.context, R.color.cyan_400)
            "advanced" -> ContextCompat.getColor(holder.itemView.context, R.color.orange_400)
            "industry" -> ContextCompat.getColor(holder.itemView.context, R.color.green_400)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.slate_400)
        }

        // Apply subtle tint to card background
        holder.stageCard.setCardBackgroundColor(backgroundColor)

        // Set click listener
        holder.stageCard.setOnClickListener {
            onStageClick(stage)
        }
    }

    override fun getItemCount(): Int = stages.size
}
