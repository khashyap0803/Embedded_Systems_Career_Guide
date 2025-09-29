package com.example.embeddedsystemscareerguide.ui.practice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.embeddedsystemscareerguide.databinding.FragmentPracticeBinding
import com.google.firebase.auth.FirebaseAuth

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PracticeViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[PracticeViewModel::class.java]
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        prefs = requireContext().getSharedPreferences("learning_progress", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupPracticeOptions()
        loadUserProgress()
    }

    private fun setupUI() {
        val user = auth.currentUser
        val userName = user?.displayName?.split(" ")?.firstOrNull() ?: "Developer"

        binding.textWelcomeMessage.text = "Practice Mode, $userName!"
        binding.textSubtitle.text = "Sharpen your embedded systems skills"
    }

    private fun setupPracticeOptions() {
        // Quick Practice Session
        binding.cardQuickPractice.setOnClickListener {
            // TODO: Implement quick practice with random questions
        }

        // Topic-Specific Practice
        binding.cardTopicPractice.setOnClickListener {
            // TODO: Implement topic selection for focused practice
        }

        // Challenge Mode
        binding.cardChallengePractice.setOnClickListener {
            // TODO: Implement challenge mode with time limits
        }

        // Review Mistakes
        binding.cardReviewMistakes.setOnClickListener {
            // TODO: Implement review of previously incorrect answers
        }
    }

    private fun loadUserProgress() {
        val totalXP = prefs.getInt("home_total_xp", 0)
        val completedStages = prefs.getInt("home_completed_stages", 0)

        binding.textTotalXp.text = "$totalXP XP"
        binding.textCompletedStages.text = "$completedStages Stages Completed"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
