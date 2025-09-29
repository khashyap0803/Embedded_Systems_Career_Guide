package com.example.embeddedsystemscareerguide

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.embeddedsystemscareerguide.databinding.ActivityMainBinding
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
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

            // Simplified navigation without drawer - all navigation through fragment cards
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home,
                    R.id.nav_learning,
                    R.id.nav_practice,
                    R.id.nav_profile
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate a simple menu for logout option
        menu.add(0, R.id.action_logout, 0, "Logout")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
