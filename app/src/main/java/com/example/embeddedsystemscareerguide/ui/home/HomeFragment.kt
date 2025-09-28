package com.example.embeddedsystemscareerguide.ui.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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

    // User progress data
    private var totalXP = 1250
    private var currentStreak = 15
    private var currentLevel = 5
    private var overallProgress = 35
    private var completedStages = 5
    private var totalStages = 15

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserWelcome()
        setupProgressDashboard()
        setupQuickActions()
        setupRecentAchievements()
        setupStudyStreak()
        startAnimations()
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
        // Update progress percentage display
        binding.textProgressPercentage.text = "$overallProgress%"

        // Animate progress statistics
        animateCounter(binding.textTotalXp, totalXP, " XP", 1000)
        animateCounter(binding.textCurrentStreak, currentStreak, " Days", 1200)
        animateCounter(binding.textCurrentLevel, currentLevel, "", 800) { value ->
            "Level $value"
        }

        // Animate progress bars
        animateProgressBar(binding.progressOverall, overallProgress, 1500)
        animateProgressBar(binding.progressStages, (completedStages * 100) / totalStages, 1800)

        // Update progress text
        binding.textOverallProgress.text = "$overallProgress% Complete"
        binding.textStagesProgress.text = "$completedStages / $totalStages Stages"
    }

    private fun setupQuickActions() {
        // Assessment Card
        binding.cardAssessment.setOnClickListener {
            addRippleEffect(it)
            startActivity(Intent(requireContext(), AssessmentActivity::class.java))
        }

        // Learning Path Card
        binding.cardLearningPath.setOnClickListener {
            addRippleEffect(it)
            findNavController().navigate(R.id.nav_learning)
        }

        // Practice Mode Card (New)
        binding.cardPractice.setOnClickListener {
            addRippleEffect(it)
            // TODO: Navigate to practice mode when implemented
            showComingSoonMessage("Practice Mode")
        }

        // Profile Card
        binding.cardProfile.setOnClickListener {
            addRippleEffect(it)
            findNavController().navigate(R.id.nav_profile)
        }
    }

    private fun setupRecentAchievements() {
        // Setup RecyclerView for achievements
        binding.recyclerAchievements.layoutManager = LinearLayoutManager(requireContext())

        // For now, show empty state
        binding.layoutEmptyAchievements.visibility = View.VISIBLE
        binding.recyclerAchievements.visibility = View.GONE

        // View All Achievements click handler
        binding.textViewAllAchievements.setOnClickListener {
            // TODO: Navigate to achievements page when implemented
            showComingSoonMessage("Achievements Page")
        }
    }

    private fun setupStudyStreak() {
        // Update streak message based on current streak
        val streakMessage = when {
            currentStreak >= 30 -> "Amazing! You're on fire with a ${currentStreak}-day streak! 🔥"
            currentStreak >= 14 -> "Fantastic! Keep your ${currentStreak}-day streak alive! 💪"
            currentStreak >= 7 -> "Great job! You have a ${currentStreak}-day streak going! 🎯"
            currentStreak >= 3 -> "Nice work! ${currentStreak} days in a row! Keep it up! ⚡"
            currentStreak >= 1 -> "Good start! You have a ${currentStreak}-day streak! 🌟"
            else -> "Start your learning streak today! Study to begin your journey 🚀"
        }
        binding.textStreakMessage.text = streakMessage
    }

    private fun startAnimations() {
        // Stagger entrance animations for cards
        val cards = listOf(
            binding.cardWelcome,
            binding.cardProgress,
            binding.cardQuickActions,
            binding.cardAchievements,
            binding.cardStudyStreak
        )

        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 50f

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay((index * 100).toLong())
                .start()
        }
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
            val animatedValue = animation.animatedValue as Int
            textView.text = if (formatter != null) {
                formatter(animatedValue)
            } else {
                "$animatedValue$suffix"
            }
        }
        animator.start()
    }

    private fun animateProgressBar(
        progressBar: com.google.android.material.progressindicator.LinearProgressIndicator,
        targetProgress: Int,
        duration: Long
    ) {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, targetProgress)
        animator.duration = duration
        animator.start()
    }

    private fun addRippleEffect(view: View) {
        // Add subtle scale animation on click
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun showComingSoonMessage(feature: String) {
        // TODO: Replace with proper navigation when features are implemented
        android.widget.Toast.makeText(
            requireContext(),
            "$feature coming soon! 🚀",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when user returns to home
        setupProgressDashboard()
        setupStudyStreak()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
