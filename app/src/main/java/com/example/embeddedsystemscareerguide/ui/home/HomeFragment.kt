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
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentHomeBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.introduction.IntroductionActivity
import com.google.firebase.auth.FirebaseAuth

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
        startAnimations()
    }

    private fun setupUserWelcome() {
        val user = auth.currentUser
        val userName = user?.displayName?.split(" ")?.firstOrNull() ?: "Developer"

        // Animate welcome message
        binding.textWelcomeMessage.text = "Welcome back, $userName!"
        binding.textMotivationalQuote.text = "\"Every expert was once a beginner. Every pro was once an amateur.\" - Robin Sharma"

        // Set greeting based on time of day
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (currentHour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            in 18..21 -> "Good Evening"
            else -> "Good Night"
        }
        binding.textGreeting.text = "$greeting 👋"
    }

    private fun setupProgressDashboard() {
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
            startActivity(Intent(requireContext(), AssessmentActivity::class.java))
        }

        // Learning Path Card
        binding.cardLearningPath.setOnClickListener {
            findNavController().navigate(R.id.nav_learning)
        }

        // Introduction Card
        binding.cardIntroduction.setOnClickListener {
            startActivity(Intent(requireContext(), IntroductionActivity::class.java))
        }

        // Profile Card
        binding.cardProfile.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }

        // Add ripple effects and animations to cards
        listOf(
            binding.cardAssessment,
            binding.cardLearningPath,
            binding.cardIntroduction,
            binding.cardProfile
        ).forEach { card ->
            card.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                false
            }
        }
    }

    private fun setupRecentAchievements() {
        // Display recent achievements
        val achievements = listOf(
            "🎯 Completed Fundamentals Stage",
            "💡 Earned 500 XP this week",
            "🔥 Maintained 15-day streak",
            "⭐ Perfect score on C Programming quiz"
        )

        binding.textRecentAchievements.text = achievements.joinToString("\n")
    }

    private fun startAnimations() {
        // Staggered entrance animations for cards
        val cards = listOf(
            binding.cardWelcome,
            binding.cardProgress,
            binding.cardQuickActions,
            binding.cardAchievements
        )

        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 100f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay((index * 200).toLong())
                .start()
        }

        // Floating animation for welcome card
        startFloatingAnimation(binding.cardWelcome)

        // Pulse animation for action cards
        startPulseAnimation(binding.cardAssessment)
        startPulseAnimation(binding.cardLearningPath)
    }

    private fun animateCounter(
        textView: android.widget.TextView,
        target: Int,
        suffix: String,
        duration: Long,
        formatter: ((Int) -> String)? = null
    ) {
        val animator = ValueAnimator.ofInt(0, target)
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            textView.text = formatter?.invoke(value) ?: "$value$suffix"
        }
        animator.start()
    }

    private fun animateProgressBar(
        progressBar: com.google.android.material.progressindicator.LinearProgressIndicator,
        target: Int,
        duration: Long
    ) {
        val animator = ValueAnimator.ofInt(0, target)
        animator.duration = duration
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            progressBar.progress = value
        }
        animator.start()
    }

    private fun startFloatingAnimation(view: View) {
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, -15f, 0f)
        animator.duration = 4000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.start()
    }

    private fun startPulseAnimation(view: View) {
        val animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f, 1f)
        animator.duration = 2000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.startDelay = (Math.random() * 1000).toLong()
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
