package com.example.embeddedsystemscareerguide.ui.practice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        
        // Get username from SharedPreferences
        val userPrefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val username = userPrefs.getString("current_username", null)
        
        // Display username, fallback to display name
        val displayName = username ?: (user?.displayName?.split(" ")?.firstOrNull() ?: "Developer")

        binding.textWelcomeMessage.text = "Practice Mode, $displayName!"
        binding.textSubtitle.text = "Sharpen your embedded systems skills"
    }

    private fun setupPracticeOptions() {
        // H5 fix: Show "Coming Soon" toast for unimplemented features
        
        // Quick Practice Session
        binding.cardQuickPractice.setOnClickListener {
            showComingSoonToast("Quick Practice")
        }

        // Topic-Specific Practice
        binding.cardTopicPractice.setOnClickListener {
            showComingSoonToast("Topic-Specific Practice")
        }

        // Challenge Mode
        binding.cardChallengePractice.setOnClickListener {
            showComingSoonToast("Challenge Mode")
        }

        // Review Mistakes
        binding.cardReviewMistakes.setOnClickListener {
            showComingSoonToast("Review Mistakes")
        }
    }
    
    /**
     * H5 fix: Show coming soon message for unimplemented features
     */
    private fun showComingSoonToast(featureName: String) {
        Toast.makeText(
            requireContext(), 
            "🚧 $featureName coming soon!\nUse the Learning Path to practice quizzes.", 
            Toast.LENGTH_LONG
        ).show()
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
