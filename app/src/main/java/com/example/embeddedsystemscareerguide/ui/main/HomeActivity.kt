package com.example.embeddedsystemscareerguide.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityHomeBinding
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import com.example.embeddedsystemscareerguide.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.textViewUserEmail.text = currentUser.email ?: "No email"

        binding.buttonLogout.setOnClickListener {
            // Clear local progress data before signing out to prevent data leakage
            UserProgressSyncService(this).clearLocalProgress()
            
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
