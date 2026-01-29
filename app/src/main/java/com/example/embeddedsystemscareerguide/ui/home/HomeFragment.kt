package com.example.embeddedsystemscareerguide.ui.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentHomeBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.assessment.ReportViewerActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.*

/**
 * Home Fragment - Main dashboard and app entry point
 *
 * Displays user progress, daily insights, and quick navigation actions.
 * Features include:
 * - Personalized welcome message with username
 * - Progress dashboard (XP, level, completed stages)
 * - Daily streak tracking and motivational insights
 * - Quick action buttons for learning, assessment, AI chat
 * - Achievement badges display
 *
 * @see HomeViewModel for state management
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: HomeViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    // Daily insights array for variety
    private val dailyInsights = arrayOf(
        "💡 Tip: Practice coding for 30 minutes daily to build consistency",
        "🔥 Focus on understanding concepts rather than memorizing syntax",
        "🎯 Start with simple projects and gradually increase complexity",
        "⚡ Debug your code step by step to improve problem-solving skills",
        "🚀 Join embedded systems communities to learn from others",
        "💪 Regular practice is more effective than long cramming sessions",
        "🌟 Document your learning journey to track your progress"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        prefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_LEARNING, Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserWelcome()
        setupProgressDashboard()
        setupQuickActions()
        setupStudyStreak()
        setupAchievements()  // Fix RecyclerView warning
        startAnimations()
    }

    override fun onResume() {
        super.onResume()
        // Refresh progress when returning to home page
        setupProgressDashboard()
    }

    private fun setupUserWelcome() {
        val user = auth.currentUser
        
        // Get username from SharedPreferences (unique identifier)
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null)
        
        // Display username with @ prefix, fallback to first name or "Developer"
        val displayName = username ?: (user?.displayName?.split(" ")?.firstOrNull() ?: "Developer")

        // Animate welcome message
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

        // Set random daily insight
        val randomInsight = dailyInsights[Random().nextInt(dailyInsights.size)]
        binding.textDailyInsight.text = randomInsight
    }

    private fun setupProgressDashboard() {
        // Load real progress from shared preferences (synced with learning path)
        // M8 fix: Using PrefsKeys constants instead of hardcoded strings
        val totalXP = prefs.getInt(PrefsKeys.TOTAL_XP, 0)
        val currentLevel = prefs.getInt(PrefsKeys.CURRENT_LEVEL, 1)
        val currentStreak = prefs.getInt(PrefsKeys.STREAK, 1)
        val overallProgress = prefs.getInt("home_progress_percentage", 0)
        val completedStages = prefs.getInt(PrefsKeys.COMPLETED_STAGES, 0)
        val totalStages = prefs.getInt("home_total_stages", 16)

        // Update progress percentage display
        binding.textProgressPercentage.text = "$overallProgress%"

        // Animate progress statistics with real data
        animateCounter(binding.textTotalXp, totalXP, " XP", 1000)
        animateCounter(binding.textCurrentStreak, currentStreak, " Days", 1200)
        animateCounter(binding.textCurrentLevel, currentLevel, "", 800) { value ->
            "Level $value"
        }

        // Animate progress bars with real data
        animateProgressBar(binding.progressOverall, overallProgress, 2000)
        animateProgressBar(binding.progressStages, (completedStages * 100) / totalStages, 1500)

        // Update progress text
        val overallProgressText = "$overallProgress% Complete"
        binding.textOverallProgress.text = overallProgressText

        val stagesProgressText = "$completedStages / $totalStages Stages"
        binding.textStagesProgress.text = stagesProgressText
    }

    private fun setupQuickActions() {
        // Learning Path card - navigates to learning path only
        binding.cardLearningPath.setOnClickListener {
            findNavController().navigate(R.id.nav_learning)
        }

        // Assessment card - now shows options for View Report or Retake
        binding.cardAssessment.setOnClickListener {
            showAssessmentOptions()
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

    private fun showAssessmentOptions() {
        val user = auth.currentUser
        if (user == null) return

        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Use user-specific key to check assessment completion
        val assessmentCompleted = prefs.getBoolean("assessment_completed_${user.uid}", false)

        if (assessmentCompleted) {
            // Show dialog with View Report and Retake options
            val options = arrayOf("📊 View Report", "🔄 Retake Assessment")
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
                .setTitle("📋 Assessment Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // View Report
                            val intent = Intent(requireContext(), ReportViewerActivity::class.java)
                            startActivity(intent)
                        }
                        1 -> {
                            // Retake Assessment
                            showRetakeConfirmationDialog()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // First time - directly start assessment
            val intent = Intent(requireContext(), AssessmentActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showRetakeConfirmationDialog() {
        val user = auth.currentUser
        if (user == null) return

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("⚠️ Retake Assessment")
            .setMessage("Are you sure you want to retake the assessment? Your previous report will be replaced with a new one.")
            .setPositiveButton("Yes, Retake") { _, _ ->
                // Clear user-specific assessment completion flag
                val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("assessment_completed_${user.uid}", false).apply()

                // Start assessment
                val intent = Intent(requireContext(), AssessmentActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupStudyStreak() {
        // Load real streak data from SharedPreferences (synced with learning path)
        val streak = prefs.getInt(PrefsKeys.STREAK, 1)
        val lastVisitDate = prefs.getString(PrefsKeys.LAST_ACTIVE_DATE, "")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        // Update streak display with real data
        binding.textStreakMessage.text = "$streak-day streak! Keep it up! 🔥"

        // Update streak motivation message based on actual streak length
        val streakMessage = when {
            streak >= 30 -> "🔥 Amazing! You're on fire! $streak days straight!"
            streak >= 14 -> "🚀 Great consistency! $streak days strong!"
            streak >= 7 -> "💪 Building momentum! $streak days in a row!"
            streak >= 3 -> "📈 Making progress! $streak days running!"
            streak == 1 -> "🌟 Great start! Keep the momentum going!"
            else -> "🌟 Start your streak today!"
        }
        binding.textStreakMessage.text = streakMessage

        // Update visual streak indicators based on real data
        updateStreakVisualIndicators(streak)
    }

    /**
     * Setup achievements section - hide RecyclerView and show empty state
     * This fixes the "No adapter attached" warning
     */
    private fun setupAchievements() {
        // Hide the RecyclerView since we're not using it yet
        // Show the empty state instead
        binding.recyclerAchievements.visibility = android.view.View.GONE
        binding.layoutEmptyAchievements.visibility = android.view.View.VISIBLE
    }

    private fun updateStreakVisualIndicators(streak: Int) {
        // Get current day of the week (1 = Sunday, 2 = Monday, ..., 7 = Saturday)
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Convert to Monday-first format (0 = Monday, 1 = Tuesday, ..., 6 = Sunday)
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

        // Get all streak day views
        val streakDays = listOf(
            binding.streakDayMonday,
            binding.streakDayTuesday,
            binding.streakDayWednesday,
            binding.streakDayThursday,
            binding.streakDayFriday,
            binding.streakDaySaturday,
            binding.streakDaySunday
        )

        // Reset all days to inactive first
        streakDays.forEach { dayView ->
            dayView.setBackgroundResource(R.drawable.bg_streak_day_inactive)
        }

        // Calculate which days should be active based on current streak
        val daysToHighlight = minOf(streak, 7) // Cap at 7 days for the week view

        // Highlight days leading up to and including today
        for (i in 0 until daysToHighlight) {
            val dayIndex = (mondayFirstDay - i + 7) % 7
            if (dayIndex >= 0 && dayIndex < streakDays.size) {
                streakDays[dayIndex].setBackgroundResource(R.drawable.bg_streak_day_active)
            }
        }

        // Highlight today with special emphasis if it's part of the streak
        if (streak > 0) {
            streakDays[mondayFirstDay].setBackgroundResource(R.drawable.bg_streak_day_active)
        }
    }

    private fun startAnimations() {
        // Animate cards with staggered entrance
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

        // Floating animation for progress card
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
