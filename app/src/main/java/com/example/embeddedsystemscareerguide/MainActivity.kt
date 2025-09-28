package com.example.embeddedsystemscareerguide

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.embeddedsystemscareerguide.databinding.ActivityMainBinding
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

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
            Snackbar.make(view, "Quick actions coming soon!", Snackbar.LENGTH_LONG)
                .setAction("OK", null)
                .setAnchorView(R.id.fab)
                .show()
        }

        try {
            val navHostFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment)
            val navController = navHostFragment.navController

            // Handle drawer layout if it exists
            val drawerLayout: DrawerLayout? = binding.drawerLayout
            val navView: com.google.android.material.navigation.NavigationView? = binding.navView

            if (navView != null && drawerLayout != null) {
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.nav_home,
                        R.id.nav_learning,
                        R.id.nav_profile
                    ),
                    drawerLayout
                )
                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)

                navView.setNavigationItemSelectedListener { item ->
                    val handled = when (item.itemId) {
                        R.id.nav_logout -> {
                            logout()
                            true
                        }
                        R.id.nav_home -> {
                            try {
                                navController.navigate(R.id.nav_home)
                            } catch (e: Exception) {
                                // Navigation failed, ignore
                            }
                            true
                        }
                        R.id.nav_learning -> {
                            try {
                                navController.navigate(R.id.nav_learning)
                            } catch (e: Exception) {
                                // Navigation failed, ignore
                            }
                            true
                        }
                        R.id.nav_profile -> {
                            try {
                                navController.navigate(R.id.nav_profile)
                            } catch (e: Exception) {
                                // Navigation failed, ignore
                            }
                            true
                        }
                        R.id.nav_settings -> {
                            try {
                                navController.navigate(R.id.nav_settings)
                            } catch (e: Exception) {
                                // Navigation failed, ignore
                            }
                            true
                        }
                        else -> {
                            try {
                                NavigationUI.onNavDestinationSelected(item, navController)
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                    drawerLayout.closeDrawer(GravityCompat.START)
                    handled
                }
            } else {
                // If no drawer layout, set up basic app bar configuration
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.nav_home,
                        R.id.nav_learning,
                        R.id.nav_profile
                    )
                )
                setupActionBarWithNavController(navController, appBarConfiguration)
            }

            // Handle bottom navigation if it exists
            findViewById<BottomNavigationView>(R.id.bottomNavView)?.let { bottomNav ->
                bottomNav.setupWithNavController(navController)
            }
        } catch (e: Exception) {
            // Navigation setup failed, continue without navigation
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
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
                // Handle settings navigation
                Snackbar.make(binding.root, "Settings coming soon!", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        try {
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // Navigate to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // If logout fails, still try to go to login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
