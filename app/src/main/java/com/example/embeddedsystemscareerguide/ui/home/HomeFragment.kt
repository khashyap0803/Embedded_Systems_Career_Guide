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
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentHomeBinding
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.assessment.ReportViewerActivity
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
    
    // Cached cloud progress
    private var cloudProgress: UserProgressSyncService.UserProgress? = null

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
        firestore = FirebaseFirestore.getInstance()
        progressSyncService = UserProgressSyncService(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh()
        setupUserWelcome()
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
        lifecycleScope.launch {
            try {
                val progress = progressSyncService.loadProgressFromCloud()
                
                if (progress != null) {
                    cloudProgress = progress
                    Log.d("HomeFragment", "Loaded from cloud: XP=${progress.totalXP}, streak=${progress.streak}")
                    updateProgressDashboard(progress)
                    updateStudyStreak(progress.streak)
                } else {
                    // New user - use defaults
                    cloudProgress = UserProgressSyncService.UserProgress()
                    updateProgressDashboard(cloudProgress!!)
                    updateStudyStreak(1)
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

        // Set random daily insight
        val randomInsight = dailyInsights[Random().nextInt(dailyInsights.size)]
        binding.textDailyInsight.text = randomInsight
    }

    /**
     * CLOUD-ONLY: Update progress dashboard from cloud data
     */
    private fun updateProgressDashboard(progress: UserProgressSyncService.UserProgress) {
        val totalXP = progress.totalXP
        val currentLevel = progress.currentStage
        val currentStreak = progress.streak
        val completedStages = progress.completedStages.size
        val totalStages = 16 // AppConstants.TOTAL_LEARNING_STAGES
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

        lifecycleScope.launch {
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

    private fun showRetakeConfirmationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle("⚠️ Retake Assessment")
            .setMessage("Are you sure you want to retake the assessment? Your previous report will be replaced with a new one.")
            .setPositiveButton("Yes, Retake") { _, _ ->
                val intent = Intent(requireContext(), AssessmentActivity::class.java)
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
     * Setup achievements section - hide RecyclerView and show empty state
     */
    private fun setupAchievements() {
        binding.recyclerAchievements.visibility = View.GONE
        binding.layoutEmptyAchievements.visibility = View.VISIBLE
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
