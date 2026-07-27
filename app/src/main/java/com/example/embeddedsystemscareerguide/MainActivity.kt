package com.example.embeddedsystemscareerguide

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.embeddedsystemscareerguide.databinding.ActivityMainBinding
import com.example.embeddedsystemscareerguide.services.AuthManager
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

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

        findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener { view ->
            showQuickActionsMenu(view)
        }

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

    private fun showQuickActionsMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.fab_quick_actions, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.quick_action_chat -> navController?.navigate(R.id.nav_chat)
                R.id.quick_action_practice -> navController?.navigate(R.id.nav_practice)
                R.id.quick_action_learning -> navController?.navigate(R.id.nav_learning)
                R.id.quick_action_assessment -> startActivity(Intent(this, AssessmentActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun logout() {
        AuthManager.logout(this)
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
