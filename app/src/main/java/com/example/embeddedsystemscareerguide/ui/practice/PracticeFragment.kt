package com.example.embeddedsystemscareerguide.ui.practice

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
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentPracticeBinding
import com.example.embeddedsystemscareerguide.services.FirestoreManager
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.quiz.QuizActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * CLOUD-ONLY: Practice Fragment loads all data from Firestore
 *
 * All four cards previously showed a "coming soon" toast. They now route to
 * real features: a quiz on the user's current stage, flashcards, AI-suggested
 * projects, and interview prep questions - see PracticeContentActivity for the
 * latter three, which share one implementation.
 */
class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PracticeViewModel
    private lateinit var auth: FirebaseAuth
    private lateinit var progressSyncService: UserProgressSyncService
    private lateinit var firestoreManager: FirestoreManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[PracticeViewModel::class.java]
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        progressSyncService = UserProgressSyncService(requireContext())
        firestoreManager = FirestoreManager.getInstance(requireContext())
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
        binding.cardQuickPractice.setOnClickListener { launchQuickQuiz() }
        binding.cardTopicPractice.setOnClickListener {
            startActivity(PracticeContentActivity.intentFor(requireContext(), PracticeContentActivity.Mode.FLASHCARDS))
        }
        binding.cardChallengePractice.setOnClickListener {
            startActivity(PracticeContentActivity.intentFor(requireContext(), PracticeContentActivity.Mode.PROJECTS))
        }
        binding.cardReviewMistakes.setOnClickListener {
            startActivity(PracticeContentActivity.intentFor(requireContext(), PracticeContentActivity.Mode.INTERVIEW))
        }
    }

    /**
     * Quick Practice launches a real quiz (GeminiQuizService via QuizActivity, already
     * used elsewhere by LearningPathFragment) on the user's current stage. Requires a
     * personalized learning path to exist - a brand new user is asked to complete the
     * assessment first rather than being handed a generic, unpersonalized quiz.
     */
    private fun launchQuickQuiz() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stages = firestoreManager.getPersonalizedStages().getOrNull() ?: emptyList()
            if (stages.isEmpty()) {
                Toast.makeText(requireContext(), R.string.practice_needs_assessment, Toast.LENGTH_LONG).show()
                return@launch
            }
            val stage = stages.firstOrNull { it.isUnlocked && !it.isCompleted } ?: stages.first()

            val intent = Intent(requireContext(), QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_STAGE_ID, stage.id)
                putExtra(QuizActivity.EXTRA_STAGE_TITLE, stage.title)
                putStringArrayListExtra(QuizActivity.EXTRA_STAGE_TOPICS, ArrayList(stage.topics))
            }
            startActivity(intent)
        }
    }

    /**
     * CLOUD-ONLY: Load progress from Firestore
     */
    private fun loadUserProgressFromCloud() {
        viewLifecycleOwner.lifecycleScope.launch {
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
