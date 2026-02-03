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
import kotlinx.coroutines.launch
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentLearningPathBinding
import com.example.embeddedsystemscareerguide.models.LearningStage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

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


    // M4 fix: Constants delegate to PrefsKeys for centralization
    companion object {
        private val PREFS_NAME = PrefsKeys.PREFS_LEARNING
        private val KEY_TOTAL_XP = PrefsKeys.TOTAL_XP
        private val KEY_CURRENT_STAGE = PrefsKeys.CURRENT_STAGE
        private val KEY_STREAK = PrefsKeys.STREAK
        private val KEY_LAST_VISIT_DATE = PrefsKeys.LAST_ACTIVE_DATE
        // Keep using original "completed_stages" for StringSet - don't use the Integer count key
        private const val KEY_COMPLETED_STAGES = "completed_stages"
        private val KEY_STAGE_STARS = PrefsKeys.STAGE_STARS_PREFIX
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
        progressSyncService = UserProgressSyncService(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // L2 fix: Removed empty setupUI() call
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
     * - If no report → redirect to assessment page
     * 
     * NO FALLBACK TO 16 HARDCODED STAGES
     */
    private fun loadStagesFromFirestore() {
        lifecycleScope.launch {
            try {
                Log.d("LearningPath", "Checking for personalized stages in Firestore...")
                
                val firestoreManager = com.example.embeddedsystemscareerguide.services.FirestoreManager.getInstance(requireContext())
                
                // Check if user has personalized stages  
                if (firestoreManager.hasPersonalizedStages()) {
                    val result = firestoreManager.getPersonalizedStages()
                    
                    if (result.isSuccess) {
                        val personalizedStages = result.getOrNull() ?: emptyList()
                        
                        if (personalizedStages.isNotEmpty()) {
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
                            return@launch
                        }
                    }
                }
                
                // NO PERSONALIZED STAGES - Check if report exists to regenerate
                Log.d("LearningPath", "No personalized stages found, checking for assessment report...")
                
                if (firestoreManager.hasAssessmentReport()) {
                    // Report exists - regenerate stages from it
                    Log.d("LearningPath", "Assessment report found, regenerating stages...")
                    regenerateStagesFromReport(firestoreManager)
                } else {
                    // No report - redirect to assessment
                    Log.d("LearningPath", "No assessment report found, redirecting to assessment...")
                    redirectToAssessment()
                }
                
            } catch (e: Exception) {
                Log.e("LearningPath", "Error loading from Firestore", e)
                // On error, check for report
                try {
                    val firestoreManager = com.example.embeddedsystemscareerguide.services.FirestoreManager.getInstance(requireContext())
                    if (firestoreManager.hasAssessmentReport()) {
                        regenerateStagesFromReport(firestoreManager)
                    } else {
                        redirectToAssessment()
                    }
                } catch (e2: Exception) {
                    Log.e("LearningPath", "Critical error, redirecting to assessment", e2)
                    redirectToAssessment()
                }
            }
        }
    }

    /**
     * Regenerate personalized stages from the saved assessment report
     */
    private fun regenerateStagesFromReport(firestoreManager: com.example.embeddedsystemscareerguide.services.FirestoreManager) {
        lifecycleScope.launch {
            try {
                val reportData = firestoreManager.getAssessmentReport()
                
                if (reportData != null) {
                    Log.d("LearningPath", "Got assessment report, generating stages...")
                    
                    // Convert report to AssessmentResult
                    val assessmentResult = convertReportToAssessmentResult(reportData)
                    
                    // Get username from SharedPreferences
                    val username = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .getString("current_username", "User") ?: "User"
                    
                    // Show loading toast
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Regenerating your learning path...", Toast.LENGTH_LONG).show()
                    }
                    
                    // Generate stages using StageGeneratorService
                    val stageGenerator = com.example.embeddedsystemscareerguide.services.StageGeneratorService.getInstance(requireContext())
                    
                    stageGenerator.generatePersonalizedStages(
                        userName = username,
                        assessmentResult = assessmentResult,
                        callback = object : com.example.embeddedsystemscareerguide.services.StageGeneratorService.GenerationCallback {
                            override fun onProgress(phase: Int, message: String) {
                                Log.d("LearningPath", "Stage generation progress: $message")
                            }
                            
                            override fun onSuccess(stages: List<PersonalizedStage>) {
                                Log.d("LearningPath", "Successfully generated ${stages.size} stages")
                                activity?.runOnUiThread {
                                    Toast.makeText(context, "Learning path regenerated!", Toast.LENGTH_SHORT).show()
                                    // Reload stages from Firestore (they're now saved)
                                    loadStagesFromFirestore()
                                }
                            }
                            
                            override fun onError(error: String) {
                                Log.e("LearningPath", "Stage generation failed: $error")
                                activity?.runOnUiThread {
                                    Toast.makeText(context, "Failed to generate stages: $error", Toast.LENGTH_LONG).show()
                                    // Redirect to assessment as fallback
                                    redirectToAssessment()
                                }
                            }
                        }
                    )
                } else {
                    Log.e("LearningPath", "Failed to get report data, redirecting to assessment")
                    redirectToAssessment()
                }
            } catch (e: Exception) {
                Log.e("LearningPath", "Error regenerating stages", e)
                redirectToAssessment()
            }
        }
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
        lifecycleScope.launch {
            try {
                // CLOUD-ONLY: Load from cloud - no local fallback
                val progress = progressSyncService.loadProgressFromCloud()
                
                if (progress != null) {
                    cloudProgress = progress
                    Log.d("LearningPath", "Loaded progress from cloud: XP=${progress.totalXP}, completed=${progress.completedStages.size}, stars=${progress.stageStars}, streak=${progress.streak}")
                    applyProgressToStages(progress)
                } else {
                    // No cloud progress - initialize with defaults for new user
                    Log.d("LearningPath", "No cloud progress found, initializing new user")
                    cloudProgress = UserProgressSyncService.UserProgress()
                    applyProgressToStages(cloudProgress!!)
                }
                
                updateStreakSystem()
                createGamePath()
                updateHomePageProgress()
                
            } catch (e: Exception) {
                Log.e("LearningPath", "Error loading progress from cloud", e)
                // On cloud error, show default state - don't use stale local data
                cloudProgress = UserProgressSyncService.UserProgress()
                applyProgressToStages(cloudProgress!!)
                createGamePath()
                updateHomePageProgress()
                Toast.makeText(context, "Could not load progress. Check internet connection.", Toast.LENGTH_SHORT).show()
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
        updateUserStats(progress.totalXP, progress.currentStage, progress.streak)
    }

    // L2 fix: Empty setupUI() removed - was: "FAB help removed as per user request"

    /**
     * Initialize progress for first-time users with proper defaults
     * CLOUD-ONLY: Only sets home page display values. Cloud is the source of truth.
     */
    private fun initializeProgressForNewUsers() {
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            // Just mark first launch as done and set home page defaults for display
            prefs.edit()
                .putBoolean(KEY_FIRST_LAUNCH, false)
                // Reset home page display values only
                .putInt("home_total_xp", 0)
                .putInt("home_current_level", 1)
                .putInt("home_streak", 1)
                .putInt("home_progress_percentage", 0)
                .putInt("home_completed_stages", 0)
                .putInt("home_total_stages", com.example.embeddedsystemscareerguide.AppConstants.TOTAL_LEARNING_STAGES)
                .apply()
        }
    }

    // DEPRECATED: loadProgressFromPreferences removed - we now use cloud-only via loadProgressFromCloud()

    /**
     * CLOUD-ONLY: Update streak system using cloud data directly
     * Reads from cloudProgress, calculates new streak, saves directly to cloud
     */
    private fun updateStreakSystem() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Use cloud progress directly - NO local SharedPreferences
        val currentProgress = cloudProgress ?: return
        val lastVisit = currentProgress.lastVisitDate
        val currentStreak = currentProgress.streak
        val bestStreak = currentProgress.bestStreak

        val wasStreakBroken = lastVisit.isNotEmpty() && lastVisit != today && !isYesterday(lastVisit)
        
        val newStreak = when {
            lastVisit.isEmpty() -> currentStreak.coerceAtLeast(1) // First visit, keep cloud streak or default to 1
            lastVisit == today -> currentStreak // Same day, keep streak
            isYesterday(lastVisit) -> currentStreak + 1 // Consecutive day, increase streak
            else -> 1 // Streak broken, reset to 1
        }

        // Update best streak if needed
        val newBestStreak = maxOf(newStreak, bestStreak)

        // Only update if something changed
        if (newStreak != currentStreak || newBestStreak != bestStreak || lastVisit != today) {
            // Update cloud progress object
            cloudProgress = currentProgress.copy(
                streak = newStreak,
                bestStreak = newBestStreak,
                lastVisitDate = today,
                lastUpdated = System.currentTimeMillis()
            )
            
            // Save directly to cloud - NO local storage
            lifecycleScope.launch {
                try {
                    progressSyncService.saveProgress(cloudProgress!!)
                    Log.d("LearningPath", "Streak updated in cloud: $currentStreak -> $newStreak")
                } catch (e: Exception) {
                    Log.e("LearningPath", "Failed to save streak to cloud", e)
                }
            }
        }

        // Show streak notifications
        if (wasStreakBroken && currentStreak > 1) {
            Toast.makeText(requireContext(),
                "🔥 Previous streak: ${currentStreak} days\nStarting fresh! Let's build a new streak!",
                Toast.LENGTH_LONG).show()
        } else if (newStreak > currentStreak) {
            val milestoneMessage = when (newStreak) {
                7 -> "🔥 1 WEEK STREAK! Amazing dedication!"
                14 -> "🔥 2 WEEK STREAK! You're unstoppable!"
                30 -> "🔥 30 DAY STREAK! Legendary commitment!"
                else -> if (newStreak % 10 == 0) "🔥 $newStreak DAY STREAK! Keep it up!" else null
            }
            milestoneMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
        
        // Update UI with new streak
        updateUserStats(currentProgress.totalXP, currentProgress.currentStage, newStreak)
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


    // NOTE: loadStagesFromAssets() and createFallbackStages() removed
    // The app now regenerates stages from assessment report or redirects to assessment
    // No more hardcoded 16-stage fallback


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
        
        // Store current stage for completion after quiz
        currentQuizStage = stage
        
        quizLauncher.launch(intent)
    }

    // Activity result launcher for quiz
    private var currentQuizStage: LearningStage? = null
    private val quizLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val score = data?.getIntExtra(
                com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.RESULT_QUIZ_SCORE, 0
            ) ?: 0
            val total = data?.getIntExtra(
                com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity.RESULT_TOTAL_QUESTIONS, 5
            ) ?: 5
            
            // Calculate stars based on score
            val percentage = (score * 100) / total
            val stars = when {
                percentage >= 80 -> 3
                percentage >= 60 -> 2
                percentage >= 40 -> 1
                else -> 0
            }
            
            // Handle quiz completion with star improvements
            currentQuizStage?.let { stage ->
                when {
                    stars >= 1 && !stage.isCompleted -> {
                        // First time completion - award XP and stars
                        completeStage(stage.id, stars)
                    }
                    stars >= 1 && stage.isCompleted && stars > stage.starsEarned -> {
                        // Improvement! Update stars only (no duplicate XP)
                        updateStageStars(stage.id, stars, stage.starsEarned)
                    }
                    stars >= 1 && stage.isCompleted && stars <= stage.starsEarned -> {
                        // Same or worse score - encourage but don't update
                        val emoji = if (stars == stage.starsEarned) "🎯" else "💪"
                        Toast.makeText(requireContext(),
                            "$emoji Score: $score/$total (${stars}⭐)\nYour best: ${stage.starsEarned}⭐ - Keep practicing!",
                            Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        // Failed (0 stars)
                        Toast.makeText(requireContext(),
                            "📚 Score: $score/$total (${percentage}%)\nYou need at least 40% to pass. Keep learning!",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            currentQuizStage = null
        }
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
        
        // Immediately update UI state (optimistic update)
        stage.isCompleted = true
        stage.starsEarned = starsEarned
        
        // Unlock next stage
        val nextStageId = (stageId.toInt() + 1).toString()
        val nextStage = stages.find { it.id == nextStageId }
        nextStage?.isUnlocked = true
        
        // Rebuild UI immediately with optimistic update
        createGamePath()
        
        // Save to cloud (async)
        lifecycleScope.launch {
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
                    updateUserStats(updatedProgress.totalXP, updatedProgress.currentStage, updatedProgress.streak)
                    createGamePath()
                    updateHomePageProgress()
                    
                    Toast.makeText(requireContext(),
                        "🎉 Stage completed! +${stage.xpReward} XP earned! ⭐×$starsEarned",
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("LearningPath", "Failed to save stage completion to cloud")
                    Toast.makeText(requireContext(),
                        "⚠️ Couldn't save progress to cloud. Please check your connection.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("LearningPath", "Error completing stage in cloud", e)
                Toast.makeText(requireContext(),
                    "⚠️ Error saving progress. Please try again.",
                    Toast.LENGTH_SHORT).show()
            } finally {
                // Clear the flag after cloud operation completes
                stageCompletionInProgress = false
                Log.d("LearningPath", "Stage completion flag cleared")
            }
        }
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
        lifecycleScope.launch {
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

    /**
     * CLOUD-ONLY: Sync cloudProgress object directly to cloud
     * Does not use local SharedPreferences
     */
    private fun syncProgressToCloud() {
        val progress = cloudProgress ?: return
        lifecycleScope.launch {
            try {
                val success = progressSyncService.saveProgress(progress)
                if (success) {
                    android.util.Log.d("LearningPath", "Progress synced to cloud")
                }
            } catch (e: Exception) {
                android.util.Log.e("LearningPath", "Cloud sync failed: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
