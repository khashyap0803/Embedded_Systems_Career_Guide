package com.example.embeddedsystemscareerguide.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.DialogAvatarPickerBinding
import com.example.embeddedsystemscareerguide.databinding.FragmentProfileBinding
import com.example.embeddedsystemscareerguide.services.AuthManager
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Profile screen: real account info, real cloud progress stats, and avatar
 * picking. Previously this screen showed only email/username with a
 * permanently-static "0 / 0 / 0" progress row baked into the layout, a fixed
 * generic avatar icon with no way to change it, and rendered fully blank if
 * currentUser happened to be null.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var progressSyncService: UserProgressSyncService

    /** avatar_1..avatar_16, indexed 0..15. Explicit list rather than getIdentifier() lookups. */
    private val avatarDrawables = listOf(
        R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4,
        R.drawable.avatar_5, R.drawable.avatar_6, R.drawable.avatar_7, R.drawable.avatar_8,
        R.drawable.avatar_9, R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
        R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16
    )

    /** -1 = no avatar chosen yet (show the generic ic_profile placeholder). */
    private var currentAvatarIndex = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        progressSyncService = UserProgressSyncService(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Reachable only if this screen were somehow shown without an active
            // session (MainActivity's own auth gate should prevent it in practice) -
            // guarded anyway rather than silently rendering blank as before.
            binding.textViewUserName.text = getString(R.string.profile_not_signed_in)
            binding.textViewUserEmail.text = ""
            return
        }

        setupUserInfo(user)
        setupAvatar()
        loadProgressStats()

        binding.buttonLogoutProfile.setOnClickListener {
            performLogout()
        }
    }

    private fun setupUserInfo(user: com.google.firebase.auth.FirebaseUser) {
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null)

        binding.textViewUserEmail.text = user.email ?: "No email"
        binding.textViewUserName.text = username?.let { "@$it" } ?: (user.displayName ?: "User")
    }

    private fun setupAvatar() {
        binding.imageViewProfileAvatar.setOnClickListener { openAvatarPicker() }
        binding.imageViewAvatarEditBadge.setOnClickListener { openAvatarPicker() }

        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val doc = firestore.collection("users").document(username).get().await()
                val savedIndex = doc.getLong("avatarIndex")?.toInt()
                if (savedIndex != null && savedIndex in avatarDrawables.indices) {
                    currentAvatarIndex = savedIndex
                    binding.imageViewProfileAvatar.setImageResource(avatarDrawables[savedIndex])
                    binding.imageViewProfileAvatar.backgroundTintList = null
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to load saved avatar", e)
                // Non-fatal: the generic placeholder set in XML remains visible.
            }
        }
    }

    private fun openAvatarPicker() {
        val dialogBinding = DialogAvatarPickerBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Theme_App_AlertDialog)
            .setTitle(R.string.choose_avatar_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialogBinding.recyclerAvatarPicker.layoutManager = GridLayoutManager(requireContext(), 4)
        dialogBinding.recyclerAvatarPicker.adapter = AvatarPickerAdapter(
            avatarResIds = avatarDrawables,
            selectedIndex = currentAvatarIndex
        ) { index, resId ->
            binding.imageViewProfileAvatar.setImageResource(resId)
            binding.imageViewProfileAvatar.backgroundTintList = null
            currentAvatarIndex = index
            saveAvatarChoice(index)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveAvatarChoice(index: Int) {
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null) ?: return

        // A targeted field update, not a full-document set/merge - this must not
        // touch uid/email/createdAt/etc. on the user's profile document.
        firestore.collection("users").document(username)
            .update("avatarIndex", index)
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Failed to save avatar choice", e)
            }
    }

    private fun loadProgressStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val progress = progressSyncService.loadProgressFromCloud()
                if (progress != null) {
                    binding.textCompletedCount.text = progress.completedStages.size.toString()
                    binding.textXpCount.text = progress.totalXP.toString()
                    binding.textStreakCount.text = progress.streak.toString()
                } else {
                    binding.textCompletedCount.text = "0"
                    binding.textXpCount.text = "0"
                    binding.textStreakCount.text = "0"
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to load progress stats", e)
            }
        }
    }

    private fun performLogout() {
        // AuthManager clears the identity key first and unconditionally, so a
        // failure elsewhere can no longer leave the next account on this device
        // reading/writing this user's Firestore documents. See AuthManager for why.
        AuthManager.logout(requireContext())
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
