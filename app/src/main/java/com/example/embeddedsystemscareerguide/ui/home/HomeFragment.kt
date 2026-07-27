package com.example.embeddedsystemscareerguide.ui.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.embeddedsystemscareerguide.AppConstants
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentHomeBinding
import com.example.embeddedsystemscareerguide.services.DailyTip
import com.example.embeddedsystemscareerguide.services.DailyTipService
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.assessment.ReportViewerActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Home Fragment - Main dashboard and app entry point
 * 
 * CLOUD-ONLY: All progress data is loaded from Firebase Firestore.
 * Includes pull-to-refresh for manual data sync.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressSyncService: UserProgressSyncService
    private lateinit var dailyTipService: DailyTipService

    // Cached cloud progress
    private var cloudProgress: UserProgressSyncService.UserProgress? = null

    // The tip currently shown, kept so tapping the summary line can show it in full.
    private var currentDailyTip: DailyTip? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        progressSyncService = UserProgressSyncService(requireContext())
        dailyTipService = DailyTipService.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh()
        setupUserWelcome()
        loadDailyTip()
        loadProgressFromCloud() // CLOUD-ONLY: Load all data from cloud
        setupQuickActions()
        setupAchievements()
        startAnimations()
    }

    override fun onResume() {
        super.onResume()
        // Refresh progress from cloud when returning to home page
        loadProgressFromCloud()
    }

    /**
     * Setup pull-to-refresh functionality
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.cyan_400,
            R.color.purple_400,
            R.color.emerald_400
        )
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("HomeFragment", "Pull-to-refresh triggered")
            loadProgressFromCloud()
        }
    }

    /**
     * CLOUD-ONLY: Load all progress data from cloud
     */
    private fun loadProgressFromCloud() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val progress = progressSyncService.loadProgressFromCloud()
                
                if (progress != null) {
                    cloudProgress = progress
                    Log.d("HomeFragment", "Loaded from cloud: XP=${progress.totalXP}, streak=${progress.streak}")
                    updateProgressDashboard(progress)
                    updateStudyStreak(progress.streak)
                    updateAchievements(progress)
                } else {
                    // New user - use defaults
                    cloudProgress = UserProgressSyncService.UserProgress()
                    updateProgressDashboard(cloudProgress!!)
                    updateStudyStreak(1)
                    updateAchievements(cloudProgress!!)
                    Log.d("HomeFragment", "No cloud progress, using defaults")
                }
                
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading from cloud", e)
                Toast.makeText(context, "Could not load data. Check connection.", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupUserWelcome() {
        val user = auth.currentUser
        
        // Get username from SharedPreferences (login session only)
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null)
        
        // Display username with @ prefix, fallback to first name or "Developer"
        val displayName = username ?: (user?.displayName?.split(" ")?.firstOrNull() ?: "Developer")

        binding.textWelcomeMessage.text = "Welcome back, $displayName!"

        // Set greeting based on time of day
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (currentHour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            in 18..21 -> "Good Evening"
            else -> "Good Night"
        }
        binding.textGreeting.text = "$greeting 👋"
    }

    /**
     * Load today's tip from DailyTipService (Firestore-cached, LLM-generated,
     * regenerated once per day) and show a one-line summary that expands into
     * the full tip - including its code snippet - on tap.
     *
     * Replaces a hardcoded 7-line array that was picked with Random() on every
     * rotation and every return to this screen, so the "daily" tip changed
     * several times a minute and the purpose-built DailyTipService sat unused.
     */
    private fun loadDailyTip() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = dailyTipService.getTodaysTip()
            val tip = result.getOrNull()
            if (tip == null) {
                Log.w("HomeFragment", "Failed to load daily tip", result.exceptionOrNull())
                binding.textDailyInsight.text = getString(R.string.daily_tip_load_failed)
                binding.textDailyInsightHint.visibility = View.GONE
                currentDailyTip = null
                return@launch
            }

            currentDailyTip = tip
            binding.textDailyInsight.text = "💡 ${summarize(tip)}"
            binding.textDailyInsightHint.visibility = View.VISIBLE
        }

        binding.textDailyInsight.setOnClickListener {
            currentDailyTip?.let { showDailyTipDialog(it) }
        }
    }

    /** The tip body is markdown-ish: "**Title**\n\ncontent...". Pull just the title for the summary line. */
    private fun summarize(tip: DailyTip): String {
        val boldTitle = Regex("^\\*\\*(.+?)\\*\\*").find(tip.tip)?.groupValues?.get(1)
        return boldTitle ?: tip.tip.take(80).substringBefore("\n")
    }

    private fun showDailyTipDialog(tip: DailyTip) {
        val body = buildString {
            append(tip.tip.replace("**", "").replace("```c", "").replace("```", ""))
            if (tip.actionItem.isNotBlank()) {
                append("\n\n👉 ")
                append(tip.actionItem)
            }
        }
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle(R.string.daily_tip_dialog_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * CLOUD-ONLY: Update progress dashboard from cloud data
     */
    private fun updateProgressDashboard(progress: UserProgressSyncService.UserProgress) {
        val totalXP = progress.totalXP
        val currentLevel = totalXP / AppConstants.XP_PER_LEVEL + 1
        val currentStreak = progress.streak
        val completedStages = progress.completedStages.size
        val totalStages = AppConstants.TOTAL_LEARNING_STAGES
        val overallProgress = if (totalStages > 0) (completedStages * 100) / totalStages else 0

        // Update progress percentage display
        binding.textProgressPercentage.text = "$overallProgress%"

        // Animate progress statistics with real cloud data
        animateCounter(binding.textTotalXp, totalXP, " XP", 1000)
        animateCounter(binding.textCurrentStreak, currentStreak, " Days", 1200)
        animateCounter(binding.textCurrentLevel, currentLevel, "", 800) { value ->
            "Level $value"
        }

        // Animate progress bars with real data
        animateProgressBar(binding.progressOverall, overallProgress, 2000)
        animateProgressBar(binding.progressStages, (completedStages * 100) / totalStages, 1500)

        // Update progress text
        binding.textOverallProgress.text = "$overallProgress% Complete"
        binding.textStagesProgress.text = "$completedStages / $totalStages Stages"
    }

    private fun setupQuickActions() {
        // Learning Path card - navigates to learning path only
        binding.cardLearningPath.setOnClickListener {
            findNavController().navigate(R.id.nav_learning)
        }

        // Assessment card - now shows options for View Report or Retake
        binding.cardAssessment.setOnClickListener {
            checkAssessmentStatusFromCloud()
        }

        // Practice card - now properly navigates to practice fragment
        binding.cardPractice.setOnClickListener {
            findNavController().navigate(R.id.nav_practice)
        }

        // Profile card - navigates to profile page (restored functionality)
        binding.cardProfile.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }

        // AI Tutor card - navigates to AI chat fragment
        binding.cardAiTutor.setOnClickListener {
            findNavController().navigate(R.id.nav_chat)
        }
    }

    /**
     * CLOUD-ONLY: Check if assessment report exists in cloud
     */
    private fun checkAssessmentStatusFromCloud() {
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null)
        
        if (username == null) {
            Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Check if report exists in cloud
                val reportDoc = withContext(Dispatchers.IO) {
                    firestore.collection("users")
                        .document(username)
                        .collection("data")
                        .document("report")
                        .get()
                        .await()
                }

                if (reportDoc.exists()) {
                    // Report exists - show options
                    showAssessmentOptions(hasReport = true)
                } else {
                    // No report - start assessment directly
                    val intent = Intent(requireContext(), AssessmentActivity::class.java)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error checking assessment status", e)
                // On error, let user try to start assessment
                val intent = Intent(requireContext(), AssessmentActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun showAssessmentOptions(hasReport: Boolean) {
        if (!hasReport) {
            val intent = Intent(requireContext(), AssessmentActivity::class.java)
            startActivity(intent)
            return
        }

        val options = arrayOf("📊 View Report", "🔄 Retake Assessment")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("📋 Assessment Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(requireContext(), ReportViewerActivity::class.java)
                        startActivity(intent)
                    }
                    1 -> {
                        showRetakeConfirmationDialog()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * V2: Enhanced retake confirmation with warning about learning progress loss
     */
    private fun showRetakeConfirmationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("⚠️ Retake Assessment")
            .setMessage(
                "Are you sure you want to retake the assessment?\n\n" +
                "This will:\n" +
                "• Replace your previous report with a new one\n" +
                "• Generate a NEW personalized learning path\n" +
                "• Reset all stage progress and stars ⭐\n\n" +
                "However, your performance history will be considered when creating your new learning path " +
                "(topics you struggled with will get more focus).\n\n" +
                "This action cannot be undone."
            )
            .setPositiveButton("Yes, Retake") { _, _ ->
                // V2: Navigate to AssessmentActivity with retake flag
                // The regeneration with history will happen after the new assessment
                val intent = Intent(requireContext(), AssessmentActivity::class.java)
                intent.putExtra("is_retake", true)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * CLOUD-ONLY: Update study streak from cloud data
     */
    private fun updateStudyStreak(streak: Int) {
        // Update streak display with real data
        val streakMessage = when {
            streak >= 30 -> "🔥 Amazing! You're on fire! $streak days straight!"
            streak >= 14 -> "🚀 Great consistency! $streak days strong!"
            streak >= 7 -> "💪 Building momentum! $streak days in a row!"
            streak >= 3 -> "📈 Making progress! $streak days running!"
            streak == 1 -> "🌟 Great start! Keep the momentum going!"
            else -> "🌟 Start your streak today!"
        }
        binding.textStreakMessage.text = streakMessage

        // Update visual streak indicators
        updateStreakVisualIndicators(streak)
    }

    /**
     * Wires the "View All" link. The list itself is populated by
     * [updateAchievements] once cloud progress has loaded - previously this
     * function unconditionally hid the RecyclerView and showed the empty
     * state, for every user, regardless of what they had actually achieved.
     */
    private fun setupAchievements() {
        binding.textViewAllAchievements.setOnClickListener { showAllAchievementsDialog() }
    }

    /**
     * Real milestone badges computed from the same [UserProgressSyncService.UserProgress]
     * already loaded for the dashboard - see Achievements.kt. No separate
     * Firestore read is needed since every condition is already in `progress`.
     */
    private fun updateAchievements(progress: UserProgressSyncService.UserProgress) {
        val earned = com.example.embeddedsystemscareerguide.models.Achievements.earned(progress)
        if (earned.isEmpty()) {
            binding.recyclerAchievements.visibility = View.GONE
            binding.layoutEmptyAchievements.visibility = View.VISIBLE
            return
        }
        binding.recyclerAchievements.visibility = View.VISIBLE
        binding.layoutEmptyAchievements.visibility = View.GONE
        binding.recyclerAchievements.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
            )
        binding.recyclerAchievements.adapter = AchievementAdapter(earned.reversed())
    }

    private fun showAllAchievementsDialog() {
        val progress = cloudProgress ?: UserProgressSyncService.UserProgress()
        val lines = com.example.embeddedsystemscareerguide.models.Achievements.ALL.joinToString("\n\n") { a ->
            val mark = if (a.isEarned(progress)) "✅" else "🔒"
            "$mark ${a.emoji} ${a.title}\n${a.description}"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("🏆 All Achievements")
            .setMessage(lines)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateStreakVisualIndicators(streak: Int) {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val mondayFirstDay = when (currentDayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val streakDays = listOf(
            binding.streakDayMonday,
            binding.streakDayTuesday,
            binding.streakDayWednesday,
            binding.streakDayThursday,
            binding.streakDayFriday,
            binding.streakDaySaturday,
            binding.streakDaySunday
        )

        streakDays.forEach { dayView ->
            dayView.setBackgroundResource(R.drawable.bg_streak_day_inactive)
        }

        val daysToHighlight = minOf(streak, 7)

        for (i in 0 until daysToHighlight) {
            val dayIndex = (mondayFirstDay - i + 7) % 7
            if (dayIndex >= 0 && dayIndex < streakDays.size) {
                streakDays[dayIndex].setBackgroundResource(R.drawable.bg_streak_day_active)
            }
        }

        if (streak > 0) {
            streakDays[mondayFirstDay].setBackgroundResource(R.drawable.bg_streak_day_active)
        }
    }

    private fun startAnimations() {
        val cards = listOf(
            binding.cardWelcome,
            binding.cardProgress,
            binding.cardStudyStreak,
            binding.cardQuickActions
        )

        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 100f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay((index * 100).toLong())
                .start()
        }

        val floatingAnimation = ObjectAnimator.ofFloat(binding.cardProgress, "translationY", 0f, -20f, 0f)
        floatingAnimation.duration = 3000
        floatingAnimation.repeatCount = ValueAnimator.INFINITE
        floatingAnimation.start()
    }

    private fun animateCounter(
        textView: android.widget.TextView,
        targetValue: Int,
        suffix: String,
        duration: Long,
        formatter: ((Int) -> String)? = null
    ) {
        val animator = ValueAnimator.ofInt(0, targetValue)
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            textView.text = if (formatter != null) {
                formatter(value)
            } else {
                "$value$suffix"
            }
        }
        animator.start()
    }

    private fun animateProgressBar(
        progressBar: com.google.android.material.progressindicator.LinearProgressIndicator,
        targetProgress: Int,
        duration: Long
    ) {
        val animator = ValueAnimator.ofInt(0, targetProgress)
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            progressBar.progress = progress
        }
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
