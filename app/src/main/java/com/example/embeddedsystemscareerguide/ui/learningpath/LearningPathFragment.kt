package com.example.embeddedsystemscareerguide.ui.learningpath

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.services.PersonalizedStage
import com.example.embeddedsystemscareerguide.ui.content.ContentReadingActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentLearningPathBinding
import com.example.embeddedsystemscareerguide.models.LearningStage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Learning Path Fragment - Main gamified learning journey screen
 *
 * Displays a visual game-like path through 16 learning stages covering
 * embedded systems concepts. Features include:
 * - Visual stage progression with stars and completion status
 * - Cloud-synced progress via Firebase Firestore
 * - AI-generated quizzes for each stage via Gemini API
 * - XP rewards and streak tracking
 *
 * @see UserProgressSyncService for cloud sync functionality
 * @see GeminiQuizService for quiz generation
 */
class LearningPathFragment : Fragment() {

    private var _binding: FragmentLearningPathBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LearningPathViewModel
    private val stages = mutableListOf<LearningStage>()
    private lateinit var prefs: SharedPreferences
    private lateinit var progressSyncService: UserProgressSyncService
    private var cloudProgress: UserProgressSyncService.UserProgress? = null
    
    // Flag to prevent onResume from overwriting optimistic UI updates during stage completion
    private var stageCompletionInProgress = false
    
    // Flag to prevent duplicate stage loading (onViewCreated + onResume both fire on initial load)
    private var isLoadingStages = false

    // The path could not be loaded or built; the student retries with the button, not onResume
    private var pathFailed = false


    // M4 fix: Constants delegate to PrefsKeys for centralization
    companion object {
        private val PREFS_NAME = PrefsKeys.PREFS_LEARNING
        private val KEY_CURRENT_STAGE = PrefsKeys.CURRENT_STAGE

        /** A finished quiz, from the result alone; null when it names no stage. */
        internal fun quizOutcome(stageId: Int, score: Int, total: Int): QuizOutcome? =
            if (stageId < 0) null else QuizOutcome(stageId.toString(), score, total)

        /**
         * Shows [stageId] completed with [stars] and the stage after it unlocked,
         * before the save is confirmed. Returns what puts both back as they were.
         */
        internal fun markCompleted(stages: List<LearningStage>, stageId: String, stars: Int): () -> Unit {
            val stage = stages.find { it.id == stageId } ?: return {}
            val next = stageId.toIntOrNull()?.let { n -> stages.find { it.id == (n + 1).toString() } }
            val wasCompleted = stage.isCompleted
            val hadStars = stage.starsEarned
            val nextWasUnlocked = next?.isUnlocked ?: false
            stage.isCompleted = true
            stage.starsEarned = stars
            next?.isUnlocked = true
            return {
                stage.isCompleted = wasCompleted
                stage.starsEarned = hadStars
                next?.isUnlocked = nextWasUnlocked
            }
        }

        /** What a finished quiz does to [stage], which it was taken for. */
        internal fun quizAction(outcome: QuizOutcome, stage: LearningStage): QuizAction = when {
            outcome.stars < 1 -> QuizAction.NOT_PASSED
            !stage.isCompleted -> QuizAction.COMPLETE
            outcome.stars > stage.starsEarned -> QuizAction.IMPROVE_STARS
            else -> QuizAction.KEEP_BEST
        }

        /**
         * Decides what this screen shows, from the stored path and, if there is
         * none, whether a report exists to rebuild it from.
         *
         * Only a read that succeeded and came back empty means "no path yet", and
         * only a successful "no report" means "take the assessment". Both reads used
         * to turn a failure into false, so an unreadable path sent the student -
         * clearing the back stack - to retake the 50-question assessment. Leaving the
         * screen mid-load did the same: the cancellation was caught as one more error.
         */
        internal suspend fun resolvePathSource(
            loadStages: suspend () -> Result<List<PersonalizedStage>>,
            reportExists: suspend () -> Result<Boolean>
        ): PathSource {
            val stages = loadStages().getOrElse { return unavailable(it) }
            if (stages.isNotEmpty()) return PathSource.Stages(stages)
            val hasReport = reportExists().getOrElse { return unavailable(it) }
            return if (hasReport) PathSource.RegenerateFromReport else PathSource.TakeAssessment
        }

        /** FirestoreManager hands a cancellation back as a failed Result; it is not one. */
        private fun unavailable(cause: Throwable): PathSource {
            if (cause is CancellationException) throw cause
            return PathSource.Unavailable(cause)
        }

        /**
         * Builds the path from the saved report, for [PathSource.RegenerateFromReport].
         *
         * Every failure - the report can't be read, generation reports an error or
         * throws - is [PathBuild.Failed], which the screen answers with a Retry button.
         * Each one used to send the student, clearing the back stack, to retake the
         * assessment, although [resolvePathSource] had just found their report. A
         * null report here is therefore a failed read (getAssessmentReport returns
         * null for both), not a missing one.
         */
        internal suspend fun buildPathFromReport(
            loadReport: suspend () -> Map<String, Any>?,
            generate: suspend (Map<String, Any>) -> Result<Unit>
        ): PathBuild = try {
            val report = loadReport()
            if (report == null) {
                PathBuild.Failed(IllegalStateException("Assessment report could not be read"))
            } else {
                generate(report).fold({ PathBuild.Built }, { PathBuild.Failed(it) })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PathBuild.Failed(e)
        }
    }

    /** What [resolvePathSource] decided. */
    internal sealed class PathSource {
        data class Stages(val stages: List<PersonalizedStage>) : PathSource()
        object RegenerateFromReport : PathSource()
        object TakeAssessment : PathSource()
        data class Unavailable(val cause: Throwable) : PathSource()
    }

    /** What [buildPathFromReport] did. */
    internal sealed class PathBuild {
        object Built : PathBuild()
        data class Failed(val cause: Throwable) : PathBuild()
    }

    /** Why there is no path on screen, as [showPathFailed] words it. Both offer Retry. */
    internal enum class PathFailure(val title: Int, val hint: Int) {
        /** The saved path, or the check for a report, could not be read. */
        LOAD(R.string.learning_path_load_failed, R.string.learning_path_load_failed_hint),
        /** The path could not be built from the saved report. */
        BUILD(R.string.learning_path_build_failed, R.string.learning_path_build_failed_hint)
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningPathBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[LearningPathViewModel::class.java]
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        progressSyncService = UserProgressSyncService(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // L2 fix: Removed empty setupUI() call
        binding.buttonRetryPath.setOnClickListener { loadStagesFromFirestore() }

        // V2: Try to load personalized stages from Firestore first
        loadStagesFromFirestore()
        
        startBackgroundAnimation()
    }

    override fun onResume() {
        super.onResume()
        // Force refresh progress from cloud when returning to this fragment
        // This ensures stage unlock status is always up-to-date after quiz completion
        Log.d("LearningPath", "onResume called, stages count=${stages.size}, completionInProgress=$stageCompletionInProgress")
        
        // Skip refresh if stage completion is in progress (let completeStage handle the refresh)
        if (stageCompletionInProgress) {
            Log.d("LearningPath", "Skipping cloud refresh - stage completion in progress")
            return
        }
        
        // Skip if already loading (prevents duplicate calls from onViewCreated + onResume)
        if (isLoadingStages) {
            Log.d("LearningPath", "Skipping reload - already loading stages")
            return
        }
        
        if (pathFailed) {
            Log.d("LearningPath", "Skipping reload - waiting for Retry")
            return
        }
        
        if (stages.isEmpty()) {
            // First time or after rotation - load stages first
            loadStagesFromFirestore()
        } else {
            // Returning from quiz/content - refresh progress and rebuild UI
            loadProgressFromCloud()
        }
    }
    
    /**
     * V2: Load stages from Firestore (personalized AI-generated stages)
     * If no personalized stages exist:
     * - Check if assessment report exists → regenerate stages from it
     *   (if that fails → "We couldn't build your learning path" with Retry)
     * If the read fails → "We couldn't load your learning path" with Retry
     * - If no report → redirect to assessment page
     * 
     * NO FALLBACK TO 16 HARDCODED STAGES
     */
    private fun loadStagesFromFirestore() {
        // Prevent duplicate calls
        if (isLoadingStages) {
            Log.d("LearningPath", "loadStagesFromFirestore skipped - already loading")
            return
        }
        isLoadingStages = true
        showPathFailed(null)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("LearningPath", "Checking for personalized stages in Firestore...")
                
                val firestoreManager = com.example.embeddedsystemscareerguide.services.FirestoreManager.getInstance(requireContext())
                
                val source = resolvePathSource(
                    loadStages = { firestoreManager.getPersonalizedStages() },
                    reportExists = { firestoreManager.hasAssessmentReport() }
                )
                when (source) {
                    is PathSource.Stages -> {
                        val personalizedStages = source.stages
                        Log.d("LearningPath", "Loaded ${personalizedStages.size} personalized stages from Firestore")
                        
                        // Convert PersonalizedStage to LearningStage for UI compatibility
                        stages.clear()
                        personalizedStages.forEach { pStage ->
                            val stageId = pStage.id
                            stages.add(LearningStage(
                                id = stageId.toString(),
                                title = pStage.title,
                                subtitle = pStage.subtitle,
                                description = pStage.description,
                                iconRes = getIconResourceForStage(stageId),
                                color = getColorForDifficulty(pStage.difficulty),
                                xpReward = pStage.xpReward,
                                topics = pStage.topics,
                                estimatedDuration = "${pStage.estimatedMinutes} mins",
                                order = stageId,
                                type = pStage.type,
                                isUnlocked = pStage.isUnlocked,
                                isCompleted = pStage.isCompleted,
                                starsEarned = pStage.starsEarned,
                                progress = 0
                            ))
                        }
                        
                        // Now load progress and render
                        loadProgressFromCloud()
                        isLoadingStages = false
                    }
                    PathSource.RegenerateFromReport -> {
                        // Report exists - regenerate stages from it
                        Log.d("LearningPath", "Assessment report found, regenerating stages...")
                        regenerateStagesFromReport(firestoreManager)
                    }
                    PathSource.TakeAssessment -> {
                        // No report - redirect to assessment
                        Log.d("LearningPath", "No assessment report found, redirecting to assessment...")
                        redirectToAssessment()
                    }
                    is PathSource.Unavailable -> {
                        Log.w("LearningPath", "Could not read the learning path; not treating it as missing", source.cause)
                        isLoadingStages = false
                        showPathFailed(PathFailure.LOAD)
                    }
                }
                
            } catch (e: CancellationException) {
                // Leaving this screen mid-load is not a missing learning path.
                isLoadingStages = false
                throw e
            } catch (e: Exception) {
                Log.e("LearningPath", "Error loading from Firestore", e)
                isLoadingStages = false
                showPathFailed(PathFailure.LOAD)
            }
        }
    }

    /**
     * Regenerate personalized stages from the saved assessment report
     */
    private fun regenerateStagesFromReport(firestoreManager: com.example.embeddedsystemscareerguide.services.FirestoreManager) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val build = buildPathFromReport(
                    loadReport = { firestoreManager.getAssessmentReport() }
                ) { reportData ->
                    Log.d("LearningPath", "Got assessment report, generating stages...")
                    
                    // Convert report to AssessmentResult
                    val assessmentResult = convertReportToAssessmentResult(reportData)
                    
                    // Get username from SharedPreferences
                    val username = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .getString("current_username", "User") ?: "User"
                    
                    // Show loading toast
                    Toast.makeText(context, "Regenerating your learning path...", Toast.LENGTH_LONG).show()
                    
                    // Generate stages using StageGeneratorService
                    val stageGenerator = com.example.embeddedsystemscareerguide.services.StageGeneratorService.getInstance(requireContext())
                    
                    // The callbacks run inside the call, so the outcome is known when it returns.
                    var outcome: Result<Unit> = Result.failure(IllegalStateException("Stage generation did not finish"))
                    stageGenerator.generatePersonalizedStages(
                        userName = username,
                        assessmentResult = assessmentResult,
                        callback = object : com.example.embeddedsystemscareerguide.services.StageGeneratorService.GenerationCallback {
                            override fun onProgress(phase: Int, message: String) {
                                Log.d("LearningPath", "Stage generation progress: $message")
                            }
                            
                            override fun onSuccess(stages: List<PersonalizedStage>) {
                                Log.d("LearningPath", "Successfully generated ${stages.size} stages")
                                outcome = Result.success(Unit)
                            }
                            
                            override fun onError(error: String) {
                                outcome = Result.failure(Exception(error))
                            }
                        }
                    )
                    outcome
                }
                
                isLoadingStages = false
                when (build) {
                    PathBuild.Built -> {
                        Toast.makeText(context, "Learning path regenerated!", Toast.LENGTH_SHORT).show()
                        // Reload stages from Firestore (they're now saved)
                        loadStagesFromFirestore()
                    }
                    is PathBuild.Failed -> {
                        Log.e("LearningPath", "Could not build the learning path", build.cause)
                        showPathFailed(PathFailure.BUILD)
                    }
                }
            } catch (e: CancellationException) {
                // Regeneration takes minutes; leaving the screen meanwhile is not a
                // reason to send the student back to the assessment.
                isLoadingStages = false
                throw e
            }
        }
    }

    /**
     * The "We couldn't load / build your learning path" state, or none for null.
     * Its Retry button goes back through [loadStagesFromFirestore]: a saved path is
     * shown, the generation runs again if there still isn't one, and only a report
     * that turns out not to exist sends the student to the assessment. A failed load
     * used to be a toast on an empty screen.
     */
    private fun showPathFailed(failure: PathFailure?) {
        pathFailed = failure != null
        val b = _binding ?: return
        if (failure != null) {
            b.textPathFailedTitle.setText(failure.title)
            b.textPathFailedHint.setText(failure.hint)
        }
        b.layoutPathBuildFailed.visibility = if (failure != null) View.VISIBLE else View.GONE
        b.scrollViewMap.visibility = if (failure != null) View.GONE else View.VISIBLE
    }

    /**
     * Convert saved report data to AssessmentResult for stage generation
     */
    private fun convertReportToAssessmentResult(reportData: Map<String, Any>): com.example.embeddedsystemscareerguide.services.StageGeneratorService.AssessmentResult {
        val topicScoresMap = mutableMapOf<String, com.example.embeddedsystemscareerguide.services.StageGeneratorService.TopicScore>()
        
        // Extract topic scores from report
        @Suppress("UNCHECKED_CAST")
        val scoresData = reportData["topicScores"] as? Map<String, Map<String, Any>> ?: emptyMap()
        
        for ((topicName, scoreData) in scoresData) {
            val score = (scoreData["score"] as? Number)?.toInt() ?: 0
            val maxScore = (scoreData["maxScore"] as? Number)?.toInt() ?: 100
            val percentage = if (maxScore > 0) (score * 100) / maxScore else 0
            
            topicScoresMap[topicName] = com.example.embeddedsystemscareerguide.services.StageGeneratorService.TopicScore(
                score = score,
                maxScore = maxScore,
                percentage = percentage
            )
        }
        
        // If no topic scores found, create some defaults from overall score
        if (topicScoresMap.isEmpty()) {
            val overallScore = (reportData["totalScore"] as? Number)?.toInt() 
                ?: (reportData["overallScore"] as? Number)?.toInt() 
                ?: 50
            val defaultTopics = listOf("Microcontrollers", "GPIO", "Interrupts", "Timers", "Communication Protocols")
            defaultTopics.forEach { topic ->
                topicScoresMap[topic] = com.example.embeddedsystemscareerguide.services.StageGeneratorService.TopicScore(
                    score = overallScore,
                    maxScore = 100,
                    percentage = overallScore
                )
            }
        }
        
        val totalScore = (reportData["totalScore"] as? Number)?.toInt() 
            ?: (reportData["overallScore"] as? Number)?.toInt() 
            ?: 50
        
        return com.example.embeddedsystemscareerguide.services.StageGeneratorService.AssessmentResult(
            totalScore = totalScore,
            maxScore = (reportData["maxScore"] as? Number)?.toInt() ?: 100,
            topicScores = topicScoresMap,
            timestamp = (reportData["timestamp"] as? Number)?.toLong() 
                ?: (reportData["completedAt"] as? Number)?.toLong() 
                ?: System.currentTimeMillis()
        )
    }

    /**
     * Redirect to assessment page when no report exists
     */
    private fun redirectToAssessment() {
        activity?.runOnUiThread {
            Toast.makeText(context, "Please complete the assessment first", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(requireContext(), com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            activity?.finish()
        }
    }
    
    /**
     * Get color hex based on difficulty level
     */
    private fun getColorForDifficulty(difficulty: String): String {
        return when (difficulty.lowercase()) {
            "beginner" -> "#10B981"      // Green
            "intermediate" -> "#3B82F6"  // Blue
            "advanced" -> "#EF4444"      // Red
            else -> "#818CF8"            // Default purple
        }
    }
    
    /**
     * Load progress from cloud (primary source of truth)
     * Falls back to local cache if cloud unavailable
     */
    private fun loadProgressFromCloud() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Today's streak is counted once a day, by whichever of MainActivity, Home
            // and this screen asks first. This screen used to do it on every open; now
            // it waits for that update so the read below includes it.
            UserProgressSyncService.streakMessage(progressSyncService.recordDailyVisit())?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }

            // A failed read must NOT be treated as "new user".
            //
            // Both branches below previously assigned a zeroed UserProgress to
            // cloudProgress on failure. updateStreakSystem() then persisted that
            // object back to Firestore, so a single offline moment permanently
            // erased the user's XP, stars, and completed stages. Only Absent is a
            // real new user; Failed leaves cloudProgress untouched and reports it.
            val load = progressSyncService.loadProgressResult()
            when (load) {
                is UserProgressSyncService.ProgressLoad.Loaded -> {
                    val progress = load.progress
                    cloudProgress = progress
                    Log.d("LearningPath", "Loaded progress from cloud: XP=${progress.totalXP}, completed=${progress.completedStages.size}, stars=${progress.stageStars}, streak=${progress.streak}")
                    applyProgressToStages(progress)
                }

                is UserProgressSyncService.ProgressLoad.Absent -> {
                    Log.d("LearningPath", "No cloud progress found, initializing new user")
                    val fresh = UserProgressSyncService.UserProgress()
                    cloudProgress = fresh
                    applyProgressToStages(fresh)
                }

                is UserProgressSyncService.ProgressLoad.Failed -> {
                    Log.e("LearningPath", "Cloud progress read failed; keeping existing state", load.cause)
                    // Render whatever we already hold (possibly nothing); nothing is written back.
                    cloudProgress?.let { applyProgressToStages(it) }
                    Toast.makeText(
                        context,
                        "Could not load progress. Check your connection - your data is safe.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            createGamePath()
            updateHomePageProgress()

            // A quiz that finished while this screen was being recreated. After a
            // failed read it waits for the next load, so it is never judged against
            // stages whose progress is unknown.
            if (load !is UserProgressSyncService.ProgressLoad.Failed && stages.isNotEmpty()) {
                pendingQuizOutcome?.let {
                    pendingQuizOutcome = null
                    handleQuizOutcome(it)
                }
            }
        }
    }

    /**
     * Apply UserProgress to stage objects
     */
    private fun applyProgressToStages(progress: UserProgressSyncService.UserProgress) {
        stages.forEach { stage ->
            val stageId = stage.id
            val stageNumber = stageId.toIntOrNull() ?: 1
            
            stage.isCompleted = progress.completedStages.contains(stageId)
            
            // STRICT unlocking logic: ONLY stage 1 unlocked initially
            stage.isUnlocked = when {
                stageNumber == 1 -> true
                else -> {
                    val prevStageId = (stageNumber - 1).toString()
                    progress.completedStages.contains(prevStageId)
                }
            }
            
            // Load stars from cloud progress
            stage.starsEarned = if (stage.isCompleted) {
                progress.stageStars[stageId] ?: 0
            } else {
                0
            }
            
            Log.d("LearningPath", "Stage $stageId: completed=${stage.isCompleted}, stars=${stage.starsEarned}")
        }
        
        // Update user stats display
        updateUserStats(progress.totalXP, progress.currentStage, progress.streak, progress.completedStages.size)
    }

    // L2 fix: Empty setupUI() removed - was: "FAB help removed as per user request"

    // DEPRECATED: loadProgressFromPreferences removed - we now use cloud-only via loadProgressFromCloud()

    // The daily streak update moved to UserProgressSyncService.recordDailyVisit.

    // NOTE: loadStagesFromAssets() and createFallbackStages() removed
    // The app now regenerates stages from assessment report or redirects to assessment
    // No more hardcoded 16-stage fallback


    private fun createGamePath() {
        val stagesContainer = binding.stagesContainer
        stagesContainer.removeAllViews()

        // Create stages in REVERSE order (16 to 1) for bottom-to-top progression
        val reversedStages = stages.sortedByDescending { it.order }

        reversedStages.forEachIndexed { index, stage ->
            // index 0 is the highest-order stage, drawn at the top of the path.
            val stageView = createStageNode(
                stage,
                isFirstStage = index == reversedStages.size - 1,
                isTopOfPath = index == 0
            )
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

    private fun createStageNode(
        stage: LearningStage,
        isFirstStage: Boolean,
        isTopOfPath: Boolean
    ): View {
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

        // Hide connection lines appropriately. This used to test for stage 16,
        // which both hid a connector in the middle of a longer generated path
        // and left a dangling connector above the real final stage.
        if (isTopOfPath) {
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
                // Offer to retake quiz, read content, or view progress
                showStageOptionsDialog(stage)
            }
            else -> {
                // V2: Show options to read content or take quiz
                showNewStageOptionsDialog(stage)
            }
        }
    }

    /**
     * V2: Show options dialog for new/unlocked stages (Read or Quiz)
     */
    private fun showNewStageOptionsDialog(stage: LearningStage) {
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle(stage.title)
            .setMessage("${stage.subtitle}\n\n🎯 Topics: ${stage.topics.joinToString(", ")}\n\n📖 Ready to learn?")
            .setPositiveButton("📖 Read Content") { _, _ ->
                launchContentReading(stage)
            }
            .setNeutralButton("📝 Take Quiz") { _, _ ->
                launchQuizForStage(stage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show options dialog for completed stages
     */
    private fun showStageOptionsDialog(stage: LearningStage) {
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle(stage.title)
            .setMessage("✅ Completed with ${stage.starsEarned}⭐ (+${stage.xpReward} XP)\n\nWhat would you like to do?")
            .setPositiveButton("📖 Review Content") { _, _ ->
                launchContentReading(stage)
            }
            .setNeutralButton("📝 Retake Quiz") { _, _ ->
                launchQuizForStage(stage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * V2: Launch content reading activity for a stage
     */
    private fun launchContentReading(stage: LearningStage) {
        Toast.makeText(requireContext(),
            "📖 Opening \"${stage.title}\" content",
            Toast.LENGTH_SHORT).show()

        val intent = android.content.Intent(requireContext(), ContentReadingActivity::class.java)
        intent.putExtra(ContentReadingActivity.EXTRA_STAGE_ID, stage.id.toIntOrNull() ?: 1)
        intent.putExtra(ContentReadingActivity.EXTRA_STAGE_TITLE, stage.title)
        intent.putStringArrayListExtra(
            ContentReadingActivity.EXTRA_STAGE_TOPICS,
            ArrayList(stage.topics)
        )
        startActivity(intent)
    }

    /**
     * Launch quiz activity for a stage
     */
    private fun launchQuizForStage(stage: LearningStage) {
        Toast.makeText(requireContext(),
            "🚀 Starting \"${stage.title}\" Quiz",
            Toast.LENGTH_SHORT).show()

        val intent = android.content.Intent(requireContext(), 
            com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity::class.java)
        intent.putExtra(com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.EXTRA_STAGE_ID, stage.id.toIntOrNull() ?: 1)
        intent.putExtra(com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.EXTRA_STAGE_TITLE, stage.title)
        intent.putStringArrayListExtra(
            com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.EXTRA_STAGE_TOPICS,
            ArrayList(stage.topics)
        )
        
        quizLauncher.launch(intent)
    }

    // The quiz hands back the stage it was for: QuizActivity returns its own launch
    // intent, which carries EXTRA_STAGE_ID. This screen used to remember the stage in
    // a field instead, and the field was gone whenever Android recreated the screen
    // while the quiz was open (a font or language change, or the app closed in the
    // background), so a passed quiz was silently dropped.
    private var pendingQuizOutcome: QuizOutcome? = null
    private val quizLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        val outcome = quizOutcome(
            stageId = data?.getIntExtra(com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.EXTRA_STAGE_ID, -1) ?: -1,
            score = data?.getIntExtra(com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.RESULT_QUIZ_SCORE, 0) ?: 0,
            total = data?.getIntExtra(com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.RESULT_TOTAL_QUESTIONS, 5) ?: 5
        )
        if (outcome == null) {
            Log.w("LearningPath", "Quiz result without a stage id")
            return@registerForActivityResult
        }
        if (stages.isEmpty() || cloudProgress == null) {
            // Recreated while the quiz was open: the path and progress are still
            // loading. loadProgressFromCloud applies this once they are in.
            pendingQuizOutcome = outcome
        } else {
            handleQuizOutcome(outcome)
        }
    }

    private fun handleQuizOutcome(outcome: QuizOutcome) {
        val stage = stages.find { it.id == outcome.stageId }
        if (stage == null) {
            Log.w("LearningPath", "Quiz result for stage ${outcome.stageId}, which is not on the path")
            return
        }
        val score = outcome.score
        val total = outcome.total
        val stars = outcome.stars
        when (quizAction(outcome, stage)) {
            QuizAction.COMPLETE -> {
                // First time completion - award XP and stars
                completeStage(stage.id, stars)
            }
            QuizAction.IMPROVE_STARS -> {
                // Improvement! Update stars only (no duplicate XP)
                updateStageStars(stage.id, stars, stage.starsEarned)
            }
            QuizAction.KEEP_BEST -> {
                // Same or worse score - encourage but don't update
                val emoji = if (stars == stage.starsEarned) "🎯" else "💪"
                Toast.makeText(requireContext(),
                    "$emoji Score: $score/$total (${stars}⭐)\nYour best: ${stage.starsEarned}⭐ - Keep practicing!",
                    Toast.LENGTH_LONG).show()
            }
            QuizAction.NOT_PASSED -> {
                Toast.makeText(requireContext(),
                    "📚 Score: $score/$total (${outcome.percentage}%)\nYou need at least 40% to pass. Keep learning!",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUserStats(totalXP: Int, currentStage: Int, streak: Int, completedStageCount: Int) {
        binding.textTotalXp.text = totalXP.toString()
        binding.textCurrentLevel.text = currentStage.toString()
        binding.textStreak.text = streak.toString()

        // completedStageCount comes from the caller's own UserProgress.completedStages
        // (the live cloud-backed source of truth) rather than the "completed_stages"
        // prefs StringSet, which nothing writes any more.
        //
        // The denominator is the generated path's real length. It is not a fixed
        // 16 - StageGeneratorService asks the model for TARGET_STAGES and the
        // user's path is however many stages actually came back.
        val totalStages = stages.size
        val completedStages = completedStageCount
        val progressPercentage = if (totalStages > 0) (completedStages * 100) / totalStages else 0

        // Update progress bar with calculated percentage
        binding.progressOverall.progress = progressPercentage

        // Update progress text with actual completed stages count
        val progressText = if (totalStages > 0) {
            "$progressPercentage% Complete • $completedStages of $totalStages stages"
        } else {
            "$completedStages stages completed"
        }
        binding.textProgressDetail.text = progressText

        // Sync progress to home page for consistency
        updateHomePageProgress()
    }

    /**
     * Complete a stage and update all related progress - CLOUD FIRST
     * Writes directly to Firestore as the source of truth
     */
    fun completeStage(stageId: String, starsEarned: Int = 3) {
        Log.d("LearningPath", "completeStage called: stageId=$stageId, starsEarned=$starsEarned")
        val stage = stages.find { it.id == stageId }
        
        if (stage == null) {
            Log.e("LearningPath", "Stage $stageId not found")
            return
        }
        
        if (stage.isCompleted) {
            Log.d("LearningPath", "Stage $stageId already completed")
            return
        }
        
        // Set flag to prevent onResume from overwriting our optimistic update
        stageCompletionInProgress = true
        
        // Immediately update UI state (optimistic update), and the next stage unlocked
        val undo = markCompleted(stages, stageId, starsEarned)

        // Rebuild UI immediately with optimistic update
        createGamePath()

        // The save is a transaction, which fails outright offline: nothing was saved.
        // The path used to keep showing the stage done and the next one open until
        // the next reload quietly took them back.
        fun saveFailed() {
            undo()
            createGamePath()
            showSaveFailed("Couldn't save this stage. Check your connection and try again.") {
                completeStage(stageId, starsEarned)
            }
        }

        // Save to cloud (async)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updatedProgress = progressSyncService.completeStageInCloud(
                    stageId = stageId,
                    starsEarned = starsEarned,
                    xpReward = stage.xpReward
                )
                
                if (updatedProgress != null) {
                    cloudProgress = updatedProgress
                    Log.d("LearningPath", "Stage $stageId completed in cloud: XP=${updatedProgress.totalXP}, stars=$starsEarned")
                    
                    // Update UI with confirmed cloud data
                    updateUserStats(updatedProgress.totalXP, updatedProgress.currentStage, updatedProgress.streak, updatedProgress.completedStages.size)
                    createGamePath()
                    updateHomePageProgress()
                    
                    Toast.makeText(requireContext(),
                        "🎉 Stage completed! +${stage.xpReward} XP earned! ⭐×$starsEarned",
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LearningPath", "Failed to save stage completion to cloud")
                    saveFailed()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LearningPath", "Error completing stage in cloud", e)
                saveFailed()
            } finally {
                // Clear the flag after cloud operation completes
                stageCompletionInProgress = false
                Log.d("LearningPath", "Stage completion flag cleared")
            }
        }
    }

    /** A save that didn't happen: say so, with Retry. */
    private fun showSaveFailed(message: String, retry: () -> Unit) {
        val b = _binding ?: return
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.learning_path_retry) {
                // The Snackbar can outlast this screen's view.
                if (_binding != null) retry()
            }
            .show()
    }

    /**
     * Update stars for a completed stage (no duplicate XP award) - CLOUD FIRST
     * Only called when user improves their score on a retake
     */
    private fun updateStageStars(stageId: String, newStars: Int, oldStars: Int) {
        val stage = stages.find { it.id == stageId }
        if (stage == null || !stage.isCompleted || newStars <= oldStars) {
            return
        }
        
        // Optimistic UI update
        stage.starsEarned = newStars
        
        // Save to cloud (async)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updatedProgress = progressSyncService.updateStarsInCloud(stageId, newStars)
                
                if (updatedProgress != null) {
                    cloudProgress = updatedProgress
                    Log.d("LearningPath", "Stars updated in cloud for stage $stageId: $oldStars -> $newStars")
                    
                    // Refresh the path
                    createGamePath()
                    
                    // Show improvement message
                    Toast.makeText(requireContext(),
                        "🌟 IMPROVED! ${oldStars}⭐ → ${newStars}⭐\nGreat progress!",
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LearningPath", "Failed to update stars in cloud")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LearningPath", "Error updating stars in cloud", e)
            }
        }
    }

    /**
     * Show detailed progress dialog for a stage
     */
    private fun showProgressDetailsDialog(stage: LearningStage) {
        val starsDisplay = "⭐".repeat(stage.starsEarned) + "☆".repeat(3 - stage.starsEarned)
        val message = """
            |📊 Stage Progress Details
            |
            |Stage: ${stage.title}
            |Stars: $starsDisplay (${stage.starsEarned}/3)
            |XP Earned: ${stage.xpReward}
            |Duration: ${stage.estimatedDuration}
            |
            |Topics Covered:
            |${stage.topics.joinToString("\n") { "• $it" }}
        """.trimMargin()

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("📈 ${stage.title}")
            .setMessage(message)
            .setPositiveButton("Retake Quiz") { _, _ ->
                launchQuizForStage(stage)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Update home page progress to sync with learning path
     * CLOUD-ONLY: Reads from cloudProgress, writes to home prefs for display
     */
    private fun updateHomePageProgress() {
        val progress = cloudProgress ?: return
        val completedStages = stages.count { it.isCompleted }
        val progressPercentage = if (stages.isNotEmpty()) (completedStages * 100) / stages.size else 0

        // Store for home page to read (home page display still uses prefs for simplicity)
        prefs.edit()
            .putInt("home_total_xp", progress.totalXP)
            .putInt("home_current_level", progress.currentStage)
            .putInt("home_streak", progress.streak)
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

    /** A finished quiz: the stage it was for, and the score. */
    internal data class QuizOutcome(val stageId: String, val score: Int, val total: Int) {
        // A quiz with no questions is 0%, not a division by zero.
        val percentage: Int get() = score * 100 / total.coerceAtLeast(1)
        val stars: Int get() = when {
            percentage >= 80 -> 3
            percentage >= 60 -> 2
            percentage >= 40 -> 1
            else -> 0
        }
    }

    internal enum class QuizAction { COMPLETE, IMPROVE_STARS, KEEP_BEST, NOT_PASSED }
}
