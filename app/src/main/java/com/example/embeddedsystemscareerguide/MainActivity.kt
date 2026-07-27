package com.example.embeddedsystemscareerguide

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.embeddedsystemscareerguide.databinding.ActivityMainBinding
import com.example.embeddedsystemscareerguide.services.AuthManager
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.example.embeddedsystemscareerguide.ui.practice.PracticeContentActivity
import com.example.embeddedsystemscareerguide.ui.widget.RadialFabMenuView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private companion object {
        const val ACTION_CHAT = 1
        const val ACTION_PRACTICE = 2
        const val ACTION_LEARNING = 3
        const val ACTION_ASSESSMENT = 4
        const val ACTION_FLASHCARDS = 5
        const val ACTION_INTERVIEW = 6
        const val ACTION_PROFILE = 7
        const val ACTION_SETTINGS = 8
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        setupRadialFabMenu()

        try {
            val navHostFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment)
            val controller = navHostFragment.navController
            navController = controller

            // Simplified navigation without drawer - all navigation through fragment cards
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home,
                    R.id.nav_learning,
                    R.id.nav_practice,
                    R.id.nav_profile
                )
            )
            setupActionBarWithNavController(controller, appBarConfiguration)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Previously built by hand with only a Logout entry, which meant
        // action_settings (and therefore nav_settings / SettingsFragment) was
        // never displayed and had no way to be reached. Inflating the real menu
        // resource restores both entries.
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            R.id.action_settings -> {
                navController?.navigate(R.id.nav_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun setupRadialFabMenu() {
        val fabMenu = findViewById<RadialFabMenuView>(R.id.radialFabMenu) ?: return

        fabMenu.actions = listOf(
            RadialFabMenuView.Action(ACTION_CHAT, "AI Tutor", R.drawable.ic_idea),
            RadialFabMenuView.Action(ACTION_PRACTICE, "Practice", R.drawable.ic_quiz),
            RadialFabMenuView.Action(ACTION_LEARNING, "Learning Path", R.drawable.ic_learning_path),
            RadialFabMenuView.Action(ACTION_ASSESSMENT, "Assessment", R.drawable.ic_assessment),
            RadialFabMenuView.Action(ACTION_FLASHCARDS, "Flashcards", R.drawable.ic_code),
            RadialFabMenuView.Action(ACTION_INTERVIEW, "Interview Prep", R.drawable.ic_career),
            RadialFabMenuView.Action(ACTION_PROFILE, "Profile", R.drawable.ic_profile),
            RadialFabMenuView.Action(ACTION_SETTINGS, "Settings", R.drawable.ic_settings)
        )

        fabMenu.onActionSelected = { action -> runQuickAction(action.id) }

        // Screen readers cannot drive a slide-to-pick gesture, so offer the same
        // actions as a plain selectable list when touch exploration is on.
        fabMenu.onAccessibleMenuRequested = { actions ->
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quick_actions_title)
                .setItems(actions.map { it.label }.toTypedArray()) { _, which ->
                    runQuickAction(actions[which].id)
                }
                .show()
        }

        // The View can read system bar insets itself but not the app bar, which
        // would otherwise let the button be dragged underneath the toolbar.
        findViewById<AppBarLayout>(R.id.appBarLayout)?.doOnLayout { bar ->
            fabMenu.extraTopInset = bar.height
        }
    }

    private fun runQuickAction(actionId: Int) {
        when (actionId) {
            ACTION_CHAT -> navController?.navigate(R.id.nav_chat)
            ACTION_PRACTICE -> navController?.navigate(R.id.nav_practice)
            ACTION_LEARNING -> navController?.navigate(R.id.nav_learning)
            ACTION_PROFILE -> navController?.navigate(R.id.nav_profile)
            ACTION_SETTINGS -> navController?.navigate(R.id.nav_settings)
            ACTION_ASSESSMENT -> startActivity(Intent(this, AssessmentActivity::class.java))
            ACTION_FLASHCARDS -> startActivity(
                PracticeContentActivity.intentFor(this, PracticeContentActivity.Mode.FLASHCARDS)
            )
            ACTION_INTERVIEW -> startActivity(
                PracticeContentActivity.intentFor(this, PracticeContentActivity.Mode.INTERVIEW)
            )
        }
    }

    private fun logout() {
        AuthManager.logout(this)
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
