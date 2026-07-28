package com.example.embeddedsystemscareerguide.ui.practice

import com.example.embeddedsystemscareerguide.services.ThemeManager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityPracticeContentBinding
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.FlashcardService
import com.example.embeddedsystemscareerguide.services.InterviewPrepService
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.services.Project
import com.example.embeddedsystemscareerguide.services.ProjectSuggestionService
import kotlinx.coroutines.launch

/**
 * Generic host for the three Practice-tab content services: flashcards,
 * interview prep questions, and project suggestions.
 *
 * These three services (FlashcardService, InterviewPrepService,
 * ProjectSuggestionService) previously had zero UI callers - the Practice
 * tab's cards showed "coming soon" toasts instead of using them. This Activity
 * is the single real entry point for all three, since their content shares
 * the same shape (a scrollable list of expandable cards) closely enough that
 * building three near-identical Activities would just be duplication.
 */
class PracticeContentActivity : AppCompatActivity() {

    enum class Mode { FLASHCARDS, INTERVIEW, PROJECTS }

    companion object {
        const val EXTRA_MODE = "mode"

        fun intentFor(context: Context, mode: Mode): Intent =
            Intent(context, PracticeContentActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
            }
    }

    private lateinit var binding: ActivityPracticeContentBinding
    private lateinit var mode: Mode

    private lateinit var firestoreManager: FirestoreManager
    private lateinit var flashcardService: FlashcardService
    private lateinit var interviewPrepService: InterviewPrepService
    private lateinit var projectSuggestionService: ProjectSuggestionService

    private var adapter: PracticeContentAdapter? = null
    private var currentStage: PersonalizedStage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTo(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.FLASHCARDS.name)

        firestoreManager = FirestoreManager.getInstance(this)
        flashcardService = FlashcardService.getInstance(this)
        interviewPrepService = InterviewPrepService.getInstance(this)
        projectSuggestionService = ProjectSuggestionService.getInstance(this)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = when (mode) {
            Mode.FLASHCARDS -> "🎯 Flashcards"
            Mode.INTERVIEW -> "🎤 Interview Prep"
            Mode.PROJECTS -> "🛠️ Project Challenges"
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerPracticeContent.layoutManager = LinearLayoutManager(this)
        binding.swipeRefresh.setOnRefreshListener { loadContent() }

        loadContent()
    }

    private fun loadContent() {
        showLoading(loadingMessage())
        lifecycleScope.launch {
            try {
                when (mode) {
                    Mode.FLASHCARDS -> loadFlashcards()
                    Mode.INTERVIEW -> loadInterviewQuestions()
                    Mode.PROJECTS -> loadProjects()
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeContent", "Failed to load content for $mode", e)
                showEmpty(getString(R.string.practice_error_generic))
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private suspend fun loadFlashcards() {
        val stage = resolveCurrentStage()
        if (stage == null) {
            showEmpty(getString(R.string.practice_needs_assessment))
            return
        }
        currentStage = stage

        var result: List<com.example.embeddedsystemscareerguide.services.Flashcard>? = null
        var errorMessage: String? = null
        flashcardService.getFlashcards(stage, object : FlashcardService.FlashcardCallback {
            // FlashcardService.getFlashcards() wraps its whole body in
            // withContext(Dispatchers.IO), so this callback fires on a background
            // thread, not Main - touching a view directly here throws
            // CalledFromWrongThreadException. runOnUiThread hops back explicitly.
            override fun onProgress(message: String) {
                runOnUiThread { showLoading(message) }
            }
            override fun onSuccess(flashcards: List<com.example.embeddedsystemscareerguide.services.Flashcard>) {
                result = flashcards
            }
            override fun onError(error: String) {
                errorMessage = error
            }
        })

        val cards = result
        if (cards.isNullOrEmpty()) {
            showEmpty(errorMessage ?: getString(R.string.practice_empty_flashcards))
            return
        }
        showItems(cards.map { PracticeContentItem.FlashcardItem(it) })
    }

    private suspend fun loadInterviewQuestions() {
        val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
        val completedTopics = stages.filter { it.isCompleted }.map { it.title }
        val topics = completedTopics.ifEmpty { listOf("Embedded Systems Fundamentals") }

        var result: Result<List<InterviewPrepService.InterviewQuestion>>? = null
        interviewPrepService.generateInterviewQuestions(topics = topics, difficulty = "medium", count = 8) {
            result = it
        }

        val questions = result?.getOrNull()
        if (questions.isNullOrEmpty()) {
            showEmpty(getString(R.string.practice_empty_interview))
            return
        }
        showItems(questions.map { PracticeContentItem.InterviewItem(it) })
    }

    private suspend fun loadProjects() {
        var result: List<Project>? = null
        var errorMessage: String? = null
        projectSuggestionService.generateProjectSuggestions(object : ProjectSuggestionService.ProjectCallback {
            // Same cross-thread issue as loadFlashcards() above.
            override fun onProgress(message: String) {
                runOnUiThread { showLoading(message) }
            }
            override fun onSuccess(projects: List<Project>) {
                result = projects
            }
            override fun onError(error: String) {
                errorMessage = error
            }
        })

        val projects = result
        if (projects.isNullOrEmpty()) {
            showEmpty(errorMessage ?: getString(R.string.practice_empty_projects))
            return
        }
        showItems(projects.map { PracticeContentItem.ProjectItem(it) })
    }

    /** The user's current stage: first unlocked-but-incomplete, else the first stage, else null if none exist yet. */
    private suspend fun resolveCurrentStage(): PersonalizedStage? {
        val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
        if (stages.isEmpty()) return null
        return stages.firstOrNull { it.isUnlocked && !it.isCompleted } ?: stages.first()
    }

    private fun showItems(items: List<PracticeContentItem>) {
        val mutable = items.toMutableList()
        adapter = PracticeContentAdapter(
            items = mutable,
            onFlashcardReview = { card, needsReview -> onFlashcardReviewed(card, needsReview) },
            onProjectStatusCycle = { project -> onProjectStatusCycled(project) }
        )
        binding.recyclerPracticeContent.adapter = adapter
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerPracticeContent.visibility = View.VISIBLE
    }

    private fun onFlashcardReviewed(card: com.example.embeddedsystemscareerguide.services.Flashcard, needsReview: Boolean) {
        val stage = currentStage ?: return
        lifecycleScope.launch {
            flashcardService.updateFlashcardReview(stage.id, card.id, needsReview)
        }
        Toast.makeText(
            this,
            if (needsReview) "Marked for review" else "Nice - marked as known",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onProjectStatusCycled(project: Project) {
        val nextStatus = when (project.status) {
            "not_started" -> "in_progress"
            "in_progress" -> "completed"
            else -> return
        }
        lifecycleScope.launch {
            val result = projectSuggestionService.updateProjectStatus(project.id, nextStatus)
            if (result.isSuccess) {
                // Simplest correct way to reflect the new status: reload the list.
                loadContent()
            } else {
                Toast.makeText(
                    this@PracticeContentActivity,
                    "Couldn't update project status",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(message: String) {
        binding.textLoadingMessage.text = message
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.recyclerPracticeContent.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        binding.textEmptyMessage.text = message
        binding.layoutLoading.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.recyclerPracticeContent.visibility = View.GONE
    }

    private fun loadingMessage(): String = when (mode) {
        Mode.FLASHCARDS -> getString(R.string.practice_loading_flashcards)
        Mode.INTERVIEW -> getString(R.string.practice_loading_interview)
        Mode.PROJECTS -> getString(R.string.practice_loading_projects)
    }
}
