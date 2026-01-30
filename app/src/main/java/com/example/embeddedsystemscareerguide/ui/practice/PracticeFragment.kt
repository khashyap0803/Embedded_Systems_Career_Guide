package com.example.embeddedsystemscareerguide.ui.practice

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.databinding.FragmentPracticeBinding
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * CLOUD-ONLY: Practice Fragment loads all data from Firestore
 */
class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PracticeViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var progressSyncService: UserProgressSyncService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[PracticeViewModel::class.java]
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        progressSyncService = UserProgressSyncService(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupPracticeOptions()
        loadUserProgressFromCloud()
    }

    private fun setupUI() {
        val user = auth.currentUser
        
        // Get username from SharedPreferences (login session only)
        val userPrefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val username = userPrefs.getString("current_username", null)
        
        // Display username, fallback to display name
        val displayName = username ?: (user?.displayName?.split(" ")?.firstOrNull() ?: "Developer")

        binding.textWelcomeMessage.text = "Practice Mode, $displayName!"
        binding.textSubtitle.text = "Sharpen your embedded systems skills"
    }

    private fun setupPracticeOptions() {
        binding.cardQuickPractice.setOnClickListener {
            showComingSoonToast("Quick Practice")
        }

        binding.cardTopicPractice.setOnClickListener {
            showComingSoonToast("Topic-Specific Practice")
        }

        binding.cardChallengePractice.setOnClickListener {
            showComingSoonToast("Challenge Mode")
        }

        binding.cardReviewMistakes.setOnClickListener {
            showComingSoonToast("Review Mistakes")
        }
    }
    
    private fun showComingSoonToast(featureName: String) {
        Toast.makeText(
            requireContext(), 
            "🚧 $featureName coming soon!\nUse the Learning Path to practice quizzes.", 
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * CLOUD-ONLY: Load progress from Firestore
     */
    private fun loadUserProgressFromCloud() {
        lifecycleScope.launch {
            try {
                val progress = progressSyncService.loadProgressFromCloud()
                
                if (progress != null) {
                    binding.textTotalXp.text = "${progress.totalXP} XP"
                    binding.textCompletedStages.text = "${progress.completedStages.size} Stages Completed"
                    Log.d("PracticeFragment", "Loaded from cloud: XP=${progress.totalXP}")
                } else {
                    binding.textTotalXp.text = "0 XP"
                    binding.textCompletedStages.text = "0 Stages Completed"
                }
            } catch (e: Exception) {
                Log.e("PracticeFragment", "Error loading from cloud", e)
                binding.textTotalXp.text = "0 XP"
                binding.textCompletedStages.text = "0 Stages Completed"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

