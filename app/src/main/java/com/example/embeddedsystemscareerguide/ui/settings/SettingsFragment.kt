package com.example.embeddedsystemscareerguide.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.embeddedsystemscareerguide.BuildConfig
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.FragmentSettingsBinding
import com.example.embeddedsystemscareerguide.services.AuthManager
import com.example.embeddedsystemscareerguide.services.DailyReminderManager
import com.example.embeddedsystemscareerguide.services.ThemeManager
import com.example.embeddedsystemscareerguide.services.ThemeMode
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Must be registered unconditionally before the Fragment reaches STARTED,
    // so this is a field initializer rather than something called from
    // onViewCreated.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            DailyReminderManager.enable(requireContext())
            binding.switchDailyReminder.isChecked = true
        } else {
            binding.switchDailyReminder.isChecked = false
            Toast.makeText(requireContext(), R.string.reminder_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAccountInfo()
        setupDailyReminderToggle()
        setupThemeSelector()
        setupAppInfo()

        binding.buttonLogout.setOnClickListener {
            performLogout()
        }
    }

    /**
     * Appearance dropdown. Driven off [ThemeMode.entries] so adding a mode to
     * that enum is the only change needed to surface it here.
     */
    private fun setupThemeSelector() {
        val modes = ThemeMode.selectable()
        val labels = modes.map { getString(it.labelRes) }

        binding.dropdownThemeMode.setSimpleItems(labels.toTypedArray())
        binding.dropdownThemeMode.setText(
            getString(ThemeManager.getMode(requireContext()).labelRes),
            false
        )

        binding.dropdownThemeMode.setOnItemClickListener { _, _, position, _ ->
            val chosen = modes[position]
            if (chosen == ThemeManager.getMode(requireContext())) return@setOnItemClickListener
            // Applying a night mode recreates the Activity, which is how every
            // already-inflated screen picks up the new palette.
            ThemeManager.setMode(requireContext(), chosen)
        }
    }

    private fun setupAccountInfo() {
        val user = FirebaseAuth.getInstance().currentUser
        val userPrefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        val username = userPrefs.getString(PrefsKeys.CURRENT_USERNAME, null)

        binding.textSettingsUsername.text = username?.let { "@$it" } ?: (user?.displayName ?: "User")
        binding.textSettingsEmail.text = user?.email ?: ""
    }

    /**
     * The switch reflects DailyReminderManager's actual persisted state - not a
     * hardcoded "Enabled" label like the static text this replaced (see
     * DailyReminderManager for why that label was simply false: no
     * notification system existed anywhere in the app before this).
     */
    private fun setupDailyReminderToggle() {
        // Set the initial state before attaching the listener so restoring it
        // doesn't itself trigger enable()/disable().
        binding.switchDailyReminder.isChecked = DailyReminderManager.isEnabled(requireContext())

        binding.switchDailyReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermissionThenEnable()
            } else {
                DailyReminderManager.disable(requireContext())
            }
        }
    }

    private fun requestNotificationPermissionThenEnable() {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            DailyReminderManager.enable(requireContext())
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupAppInfo() {
        binding.textAppVersion.text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun performLogout() {
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
