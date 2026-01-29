package com.example.embeddedsystemscareerguide.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentProfileBinding
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup user info
        setupUserInfo()

        // Find logout button and set click listener
        view.findViewById<MaterialButton>(R.id.button_logout_profile)?.setOnClickListener {
            performLogout()
        }
    }

    private fun setupUserInfo() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            // Get username from SharedPreferences
            val userPrefs = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
            val username = userPrefs.getString("current_username", null)
            
            // Update UI with user information
            binding.textViewUserEmail?.text = user.email ?: "No email"
            binding.textViewUserName?.text = username ?: (user.displayName ?: "User")
        }
    }

    private fun performLogout() {
        try {
            // Clear local progress data before signing out to prevent data leakage
            UserProgressSyncService(requireContext()).clearLocalProgress()
            
            // Clear user-specific prefs (including username)
            requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // Navigate to LoginActivity
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Finish the parent activity
            requireActivity().finish()
        } catch (e: Exception) {
            // If logout fails, still try to go to login
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
