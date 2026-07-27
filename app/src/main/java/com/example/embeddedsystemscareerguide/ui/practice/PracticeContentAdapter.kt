package com.example.embeddedsystemscareerguide.ui.practice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ItemPracticeContentBinding
import com.example.embeddedsystemscareerguide.services.Flashcard
import com.example.embeddedsystemscareerguide.services.InterviewPrepService
import com.example.embeddedsystemscareerguide.services.Project

/**
 * One item in the Practice tab's content list: a flashcard, an interview
 * question, or a project suggestion. A single item layout is reused for all
 * three so [PracticeContentAdapter] doesn't need three near-identical view
 * holders.
 */
sealed class PracticeContentItem {
    data class FlashcardItem(val card: Flashcard) : PracticeContentItem()
    data class InterviewItem(val question: InterviewPrepService.InterviewQuestion) : PracticeContentItem()
    data class ProjectItem(val project: Project) : PracticeContentItem()
}

/**
 * Renders flashcards, interview questions, and project suggestions from a
 * single flexible card layout ([R.layout.item_practice_content]).
 *
 * Flashcards and interview questions start collapsed (question only) and
 * reveal their answer on tap, matching spaced-repetition study UX. Projects
 * are shown expanded, since there is no "answer" to hide.
 */
class PracticeContentAdapter(
    private val items: List<PracticeContentItem>,
    private val onFlashcardReview: (Flashcard, needsReview: Boolean) -> Unit,
    private val onProjectStatusCycle: (Project) -> Unit
) : RecyclerView.Adapter<PracticeContentAdapter.ViewHolder>() {

    /** Positions the user has tapped to reveal the answer/back side. */
    private val revealed = mutableSetOf<Int>()

    inner class ViewHolder(val binding: ItemPracticeContentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPracticeContentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PracticeContentItem.FlashcardItem -> bindFlashcard(holder, item.card, position)
            is PracticeContentItem.InterviewItem -> bindInterview(holder, item.question, position)
            is PracticeContentItem.ProjectItem -> bindProject(holder, item.project)
        }
    }

    private fun bindFlashcard(holder: ViewHolder, card: Flashcard, position: Int) {
        val b = holder.binding
        val isRevealed = position in revealed

        b.textItemTag.text = "${card.difficulty.uppercase()} · ${card.category.uppercase()}"
        b.textItemMeta.visibility = android.view.View.GONE
        b.textItemTitle.text = card.front

        b.textItemBody.visibility = if (isRevealed) android.view.View.VISIBLE else android.view.View.GONE
        b.textItemBody.text = card.back
        b.textItemHint.visibility = if (isRevealed) android.view.View.GONE else android.view.View.VISIBLE

        b.layoutFlashcardActions.visibility = if (isRevealed) android.view.View.VISIBLE else android.view.View.GONE
        b.layoutProjectActions.visibility = android.view.View.GONE

        b.cardPracticeItem.setOnClickListener {
            toggleReveal(position)
        }
        b.btnKnowIt.setOnClickListener { onFlashcardReview(card, false) }
        b.btnNeedsReview.setOnClickListener { onFlashcardReview(card, true) }
    }

    private fun bindInterview(holder: ViewHolder, q: InterviewPrepService.InterviewQuestion, position: Int) {
        val b = holder.binding
        val isRevealed = position in revealed

        b.textItemTag.text = "${q.difficulty.uppercase()} · ${q.category.uppercase()}"
        b.textItemMeta.visibility = android.view.View.GONE
        b.textItemTitle.text = q.question

        val answerBody = buildString {
            append(q.idealAnswer)
            if (q.keyPoints.isNotEmpty()) {
                append("\n\nKey points:\n")
                append(q.keyPoints.joinToString("\n") { "• $it" })
            }
        }
        b.textItemBody.visibility = if (isRevealed) android.view.View.VISIBLE else android.view.View.GONE
        b.textItemBody.text = answerBody
        b.textItemHint.visibility = if (isRevealed) android.view.View.GONE else android.view.View.VISIBLE

        b.layoutFlashcardActions.visibility = android.view.View.GONE
        b.layoutProjectActions.visibility = android.view.View.GONE

        b.cardPracticeItem.setOnClickListener {
            toggleReveal(position)
        }
    }

    private fun bindProject(holder: ViewHolder, project: Project) {
        val b = holder.binding

        b.textItemTag.text = project.difficulty.uppercase()
        b.textItemMeta.visibility = android.view.View.VISIBLE
        b.textItemMeta.text = "~${project.estimatedHours}h"
        b.textItemTitle.text = project.title

        b.textItemBody.visibility = android.view.View.VISIBLE
        b.textItemHint.visibility = android.view.View.GONE
        b.textItemBody.text = buildString {
            append(project.description)
            if (project.skills.isNotEmpty()) {
                append("\n\nSkills: ")
                append(project.skills.joinToString(", "))
            }
            if (project.components.isNotEmpty()) {
                append("\n\nComponents: ")
                append(project.components.joinToString(", "))
            }
            if (project.steps.isNotEmpty()) {
                append("\n\nSteps:\n")
                append(project.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
            }
        }

        b.layoutFlashcardActions.visibility = android.view.View.GONE
        b.layoutProjectActions.visibility = android.view.View.VISIBLE
        b.btnProjectStatus.text = when (project.status) {
            "in_progress" -> b.root.context.getString(R.string.project_status_in_progress)
            "completed" -> b.root.context.getString(R.string.project_status_completed)
            else -> b.root.context.getString(R.string.project_status_not_started)
        }
        b.btnProjectStatus.isEnabled = project.status != "completed"
        b.btnProjectStatus.setOnClickListener { onProjectStatusCycle(project) }

        b.cardPracticeItem.setOnClickListener(null)
    }

    private fun toggleReveal(position: Int) {
        if (!revealed.add(position)) revealed.remove(position)
        notifyItemChanged(position)
    }
}
