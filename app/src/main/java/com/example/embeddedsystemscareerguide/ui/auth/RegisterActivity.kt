package com.example.embeddedsystemscareerguide.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    // H7 fix: Using lifecycleScope instead of custom CoroutineScope to prevent leaks
    
    private var usernameCheckJob: Job? = null
    private var isUsernameAvailable = false
    private var lastCheckedUsername = ""

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,20}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        
        setupUsernameValidation()
        
        binding.buttonRegister.setOnClickListener {
            registerUser()
        }
        binding.textViewGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // H7 fix: Removed onDestroy scope.cancel() - lifecycleScope handles this automatically

    private fun setupUsernameValidation() {
        binding.editTextUsernameRegister.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val username = s.toString().lowercase().trim()
                
                // Cancel previous check
                usernameCheckJob?.cancel()
                
                if (username.isEmpty()) {
                    binding.textFieldUsernameRegister.helperText = "3-20 characters, letters, numbers, underscore only"
                    binding.textFieldUsernameRegister.setEndIconDrawable(null)
                    isUsernameAvailable = false
                    return
                }
                
                // Validate format first
                if (!USERNAME_PATTERN.matches(username)) {
                    binding.textFieldUsernameRegister.error = "Only lowercase letters, numbers, underscore (3-20 chars)"
                    binding.textFieldUsernameRegister.setEndIconDrawable(null)
                    isUsernameAvailable = false
                    return
                }
                
                binding.textFieldUsernameRegister.error = null
                binding.textFieldUsernameRegister.helperText = "Checking availability..."
                
                // Debounce check - wait 500ms before checking
                usernameCheckJob = lifecycleScope.launch {
                    delay(500)
                    checkUsernameAvailability(username)
                }
            }
        })
    }

    private suspend fun checkUsernameAvailability(username: String) {
        try {
            val document = firestore.collection("usernames")
                .document(username)
                .get()
                .await()
            
            if (document.exists()) {
                // Username taken
                binding.textFieldUsernameRegister.helperText = "❌ Username already taken"
                binding.textFieldUsernameRegister.setHelperTextColor(
                    ContextCompat.getColorStateList(this@RegisterActivity, R.color.error_color)
                )
                isUsernameAvailable = false
            } else {
                // Username available
                binding.textFieldUsernameRegister.helperText = "✓ Username available"
                binding.textFieldUsernameRegister.setHelperTextColor(
                    ContextCompat.getColorStateList(this@RegisterActivity, R.color.success_color)
                )
                isUsernameAvailable = true
                lastCheckedUsername = username
            }
        } catch (e: Exception) {
            binding.textFieldUsernameRegister.helperText = "Unable to check availability"
            isUsernameAvailable = false
        }
    }

    private fun registerUser() {
        val username = binding.editTextUsernameRegister.text.toString().lowercase().trim()
        val email = binding.editTextEmailRegister.text.toString().trim()
        val password = binding.editTextPasswordRegister.text.toString().trim()
        val confirmPassword = binding.editTextConfirmPasswordRegister.text.toString().trim()
        
        // Validate username
        if (username.isEmpty()) {
            binding.textFieldUsernameRegister.error = "Username is required"
            binding.editTextUsernameRegister.requestFocus()
            return
        }
        
        if (!USERNAME_PATTERN.matches(username)) {
            binding.textFieldUsernameRegister.error = "Only lowercase letters, numbers, underscore (3-20 chars)"
            binding.editTextUsernameRegister.requestFocus()
            return
        }
        
        if (!isUsernameAvailable || username != lastCheckedUsername) {
            binding.textFieldUsernameRegister.error = "Please choose an available username"
            binding.editTextUsernameRegister.requestFocus()
            return
        }
        
        binding.textFieldUsernameRegister.error = null
        
        // Validate email
        if (email.isEmpty()) {
            binding.textFieldEmailRegister.error = "Email is required"
            binding.editTextEmailRegister.requestFocus()
            return
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textFieldEmailRegister.error = "Enter a valid email"
            binding.editTextEmailRegister.requestFocus()
            return
        }
        binding.textFieldEmailRegister.error = null
        
        // Validate password
        if (password.isEmpty()) {
            binding.textFieldPasswordRegister.error = "Password is required"
            binding.editTextPasswordRegister.requestFocus()
            return
        } else if (password.length < 6) {
            binding.textFieldPasswordRegister.error = "Password must be at least 6 characters"
            binding.editTextPasswordRegister.requestFocus()
            return
        }
        binding.textFieldPasswordRegister.error = null
        
        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.textFieldConfirmPasswordRegister.error = "Confirm password is required"
            binding.editTextConfirmPasswordRegister.requestFocus()
            return
        } else if (password != confirmPassword) {
            binding.textFieldConfirmPasswordRegister.error = "Passwords do not match"
            binding.editTextConfirmPasswordRegister.requestFocus()
            return
        }
        binding.textFieldConfirmPasswordRegister.error = null
        
        // Proceed with registration
        binding.progressBarRegister.visibility = View.VISIBLE
        binding.buttonRegister.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // Create Firebase Auth user first
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid ?: throw Exception("Failed to get user ID")
                
                // Use Firestore transaction to atomically check and claim username
                // This prevents race condition where two users could claim same username
                val usernameRef = firestore.collection("usernames").document(username)
                val userRef = firestore.collection("users").document(username)
                
                var usernameClaimSuccess = false
                try {
                    firestore.runTransaction { transaction ->
                        val usernameDoc = transaction.get(usernameRef)
                        
                        if (usernameDoc.exists()) {
                            // Username already taken - this will throw and rollback
                            throw com.google.firebase.firestore.FirebaseFirestoreException(
                                "Username already taken",
                                com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                            )
                        }
                        
                        // Claim the username atomically
                        transaction.set(usernameRef, mapOf(
                            "uid" to uid,
                            "createdAt" to System.currentTimeMillis()
                        ))
                        
                        // Create user profile
                        transaction.set(userRef, mapOf(
                            "uid" to uid,
                            "username" to username,
                            "email" to email,
                            "displayName" to username,
                            "createdAt" to System.currentTimeMillis(),
                            "lastLogin" to System.currentTimeMillis()
                        ))
                    }.await()
                    usernameClaimSuccess = true
                } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED) {
                        // Username was taken during transaction
                        // Delete the auth user we just created
                        auth.currentUser?.delete()?.await()
                        throw Exception("Username was taken by another user. Please choose a different one.")
                    } else {
                        throw e
                    }
                }
                
                if (usernameClaimSuccess) {
                    binding.progressBarRegister.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finishAffinity()
                }
                
            } catch (e: Exception) {
                binding.progressBarRegister.visibility = View.GONE
                binding.buttonRegister.isEnabled = true
                Toast.makeText(this@RegisterActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
