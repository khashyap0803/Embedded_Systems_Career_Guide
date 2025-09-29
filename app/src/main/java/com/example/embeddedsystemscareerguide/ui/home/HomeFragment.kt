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
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentHomeBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.*

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
        prefs = requireContext().getSharedPreferences("learning_progress", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserWelcome()
        setupProgressDashboard()
        setupQuickActions()
        setupStudyStreak()
        startAnimations()
    }

    override fun onResume() {
        super.onResume()
        // Refresh progress when returning to home page
        setupProgressDashboard()
    }

    private fun setupUserWelcome() {
        val user = auth.currentUser
        val userName = user?.displayName?.split(" ")?.firstOrNull() ?: "Developer"

        // Animate welcome message
        binding.textWelcomeMessage.text = "Welcome back, $userName!"

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
        val totalXP = prefs.getInt("home_total_xp", 0)
        val currentLevel = prefs.getInt("home_current_level", 1)
        val currentStreak = prefs.getInt("home_streak", 1)
        val overallProgress = prefs.getInt("home_progress_percentage", 0)
        val completedStages = prefs.getInt("home_completed_stages", 0)
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

        // Assessment card
        binding.cardAssessment.setOnClickListener {
            val intent = Intent(requireContext(), AssessmentActivity::class.java)
            startActivity(intent)
        }

        // Practice card - now properly navigates to practice fragment
        binding.cardPractice.setOnClickListener {
            findNavController().navigate(R.id.nav_practice)
        }

        // Profile card - navigates to profile page (restored functionality)
        binding.cardProfile.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }
    }

    private fun setupStudyStreak() {
        // Load real streak data from SharedPreferences (synced with learning path)
        val streak = prefs.getInt("home_streak", 1)
        val lastVisitDate = prefs.getString("last_visit_date", "")
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
