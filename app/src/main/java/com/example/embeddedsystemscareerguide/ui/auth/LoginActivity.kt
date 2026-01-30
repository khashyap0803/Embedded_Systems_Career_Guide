@file:Suppress("DEPRECATION") // We are intentionally using the older, more stable Google Sign-In API

package com.example.embeddedsystemscareerguide.ui.auth

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.embeddedsystemscareerguide.services.UserProgressSyncService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.example.embeddedsystemscareerguide.BuildConfig
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityLoginBinding
import com.example.embeddedsystemscareerguide.ui.introduction.IntroductionActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,20}$")
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                hideLoading()
                // Handle specific error codes
                val errorMessage = when (e.statusCode) {
                    12500 -> "Google Sign-In failed. Please check your internet connection."
                    12501 -> "Sign-in was cancelled"
                    12502 -> "Sign-in is currently in progress"
                    10 -> "Developer error. Please contact support." // DEVELOPER_ERROR
                    7 -> "Network error. Please check your connection."
                    else -> "Google sign in failed (${e.statusCode}): ${e.message}"
                }
                Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
                showError(errorMessage)
            }
        } else {
            hideLoading()
            // Don't show error for user cancellation (resultCode == 0)
            if (result.resultCode != Activity.RESULT_CANCELED) {
                showError("Google sign in cancelled")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupUI()
        setupClickListeners()
        startEntranceAnimations()
    }

    private fun setupUI() {
        // Set up window for immersive experience
        window.statusBarColor = ContextCompat.getColor(this, R.color.slate_950)

        // Initially hide elements for entrance animation
        binding.cardViewLoginForm.alpha = 0f
        binding.cardViewLoginForm.translationY = 100f
        binding.buttonLogin.alpha = 0f
        binding.buttonLogin.translationY = 50f
        binding.buttonSignInWithGoogle.alpha = 0f
        binding.buttonSignInWithGoogle.translationY = 50f
    }

    private fun setupClickListeners() {
        // Login button
        binding.buttonLogin.setOnClickListener {
            if (validateInput()) {
                performLogin()
            }
        }

        // Google Sign-In button
        binding.buttonSignInWithGoogle.setOnClickListener {
            signInWithGoogle()
        }
        
        // Forgot Password
        binding.textViewForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Sign Up navigation
        binding.textViewGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Add button press animations
        setupButtonAnimations()
    }

    private fun setupButtonAnimations() {
        listOf(binding.buttonLogin, binding.buttonSignInWithGoogle).forEach { button ->
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                false
            }
        }
    }

    private fun startEntranceAnimations() {
        // Animate logo
        binding.imageViewAppLogo.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1000)
            .withEndAction {
                binding.imageViewAppLogo.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(500)
                    .start()
            }
            .start()

        // Animate welcome text
        binding.textViewLoginTitle.alpha = 0f
        binding.textViewLoginTitle.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(300)
            .start()

        // Animate login card
        binding.cardViewLoginForm.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate buttons
        binding.buttonLogin.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(700)
            .start()

        binding.buttonSignInWithGoogle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(750)
            .start()

        // Floating animation for background elements
        startFloatingAnimation()
    }

    private fun startFloatingAnimation() {
        val animator = ObjectAnimator.ofFloat(binding.cardViewLoginForm, "translationY", 0f, -10f, 0f)
        animator.duration = 4000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.start()
    }

    private fun validateInput(): Boolean {
        val emailOrUsername = binding.editTextEmailLogin.text.toString().trim()
        val password = binding.editTextPasswordLogin.text.toString()

        // Reset error states
        binding.textFieldEmailLogin.error = null
        binding.textFieldPasswordLogin.error = null

        var isValid = true

        // Validate email or username
        if (emailOrUsername.isEmpty()) {
            binding.textFieldEmailLogin.error = "Email or username is required"
            isValid = false
        } else if (emailOrUsername.contains("@")) {
            // Looks like an email - validate email format
            if (!Patterns.EMAIL_ADDRESS.matcher(emailOrUsername).matches()) {
                binding.textFieldEmailLogin.error = "Please enter a valid email"
                isValid = false
            }
        } else {
            // Looks like a username - validate username format
            val usernamePattern = Regex("^[a-z0-9_]{3,20}$")
            if (!usernamePattern.matches(emailOrUsername.lowercase())) {
                binding.textFieldEmailLogin.error = "Invalid username format"
                isValid = false
            }
        }

        // Validate password
        if (password.isEmpty()) {
            binding.textFieldPasswordLogin.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.textFieldPasswordLogin.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun performLogin() {
        val emailOrUsername = binding.editTextEmailLogin.text.toString().trim()
        val password = binding.editTextPasswordLogin.text.toString()

        showLoading()

        // Check if input is email or username
        if (emailOrUsername.contains("@")) {
            // Direct email login
            signInWithEmail(emailOrUsername, password)
        } else {
            // Username login - need to look up email first
            loginWithUsername(emailOrUsername.lowercase(), password)
        }
    }

    private fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login successful - check for username
                    checkUserProfileAndNavigate(auth.currentUser!!)
                } else {
                    hideLoading()
                    showError("Login failed: ${task.exception?.message}")
                }
            }
    }

    private fun loginWithUsername(username: String, password: String) {
        lifecycleScope.launch {
            try {
                // Look up email from username
                val usernameDoc = withContext(Dispatchers.IO) {
                    firestore.collection("usernames")
                        .document(username)
                        .get()
                        .await()
                }

                if (!usernameDoc.exists()) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        showError("Username \"$username\" not found")
                    }
                    return@launch
                }

                // Get the email from the username document or user profile
                val uid = usernameDoc.getString("uid")
                if (uid == null) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        showError("Invalid username data")
                    }
                    return@launch
                }

                // Get email from user profile
                val userDoc = withContext(Dispatchers.IO) {
                    firestore.collection("users")
                        .document(username)
                        .get()
                        .await()
                }

                val email = userDoc.getString("email")
                Log.d("LoginActivity", "Username lookup - found email: $email for username: $username")
                
                if (email == null) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        showError("Could not find email for this username")
                    }
                    return@launch
                }

                // Now sign in with the email
                // H1 fix: Removed debug Toast and logs in production
                withContext(Dispatchers.Main) {
                    if (BuildConfig.DEBUG) {
                        Log.d("LoginActivity", "Attempting login with email: $email")
                    }
                    signInWithEmail(email, password)
                }

            } catch (e: Exception) {
                Log.e("LoginActivity", "Error looking up username: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun signInWithGoogle() {
        // Check Google Play Services availability first
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                // Show dialog to resolve the issue
                googleApiAvailability.getErrorDialog(this, resultCode, 9001)?.show()
            } else {
                showError("This device doesn't support Google Play Services")
            }
            return
        }

        showLoading()
        // Sign out first to ensure account picker is shown
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success - check for username
                    checkUserProfileAndNavigate(auth.currentUser!!)
                } else {
                    hideLoading()
                    showError("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    /**
     * Check if user has a profile with username, if not, create one
     * FIXED: Improved detection for existing Google users
     */
    private fun checkUserProfileAndNavigate(user: FirebaseUser) {
        lifecycleScope.launch {
            try {
                // First, try to find user by UID in usernames collection
                val usernameQuery = firestore.collection("usernames")
                    .whereEqualTo("uid", user.uid)
                    .get()
                    .await()

                if (!usernameQuery.isEmpty) {
                    // User has username, get it and save to SharedPreferences
                    val username = usernameQuery.documents.first().id
                    saveUsernameToPrefs(username)
                    
                    // Update last login time
                    firestore.collection("users").document(username)
                        .update("lastLogin", System.currentTimeMillis())
                        .addOnFailureListener { /* Ignore errors */ }
                    
                    hideLoading()
                    showSuccess("Welcome back, @$username!")
                    navigateToMainActivity(username)
                } else {
                    // No username found - check if user document exists by email
                    // This handles cases where user data might be partially created
                    val emailQuery = firestore.collection("users")
                        .whereEqualTo("email", user.email)
                        .get()
                        .await()
                    
                    if (!emailQuery.isEmpty) {
                        // Found by email, recover username
                        val userDoc = emailQuery.documents.first()
                        val username = userDoc.getString("username") ?: userDoc.id
                        
                        // Make sure usernames collection is in sync
                        firestore.collection("usernames").document(username)
                            .set(mapOf("uid" to user.uid, "createdAt" to System.currentTimeMillis()))
                            .await()
                        
                        saveUsernameToPrefs(username)
                        hideLoading()
                        showSuccess("Welcome back, @$username!")
                        navigateToMainActivity(username)
                    } else {
                        // Truly new user - need to create username
                        hideLoading()
                        promptForUsername(user)
                    }
                }
            } catch (e: Exception) {
                hideLoading()
                showError("Error checking profile: ${e.message}")
            }
        }
    }

    /**
     * Extract username from email (e.g., hello123@gmail.com → hello123)
     */
    private fun extractUsernameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").lowercase()
        // Clean up: keep only letters, numbers, underscore
        return localPart.replace(Regex("[^a-z0-9_]"), "").take(20)
    }

    /**
     * Prompt user to choose a username (for Google Sign-In users)
     */
    private fun promptForUsername(user: FirebaseUser) {
        val suggestedUsername = extractUsernameFromEmail(user.email ?: "user")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_username_prompt, null)
        val usernameInput = dialogView.findViewById<TextInputEditText>(R.id.editTextUsername)
        val usernameLayout = dialogView.findViewById<TextInputLayout>(R.id.textFieldUsername)
        
        usernameInput.setText(suggestedUsername)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Choose Your Username")
            .setMessage("This will be your unique identifier in the app")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Continue", null) // Set null to override later
            .create()

        dialog.show()

        // Override positive button to add validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val username = usernameInput.text.toString().lowercase().trim()

            if (!USERNAME_PATTERN.matches(username)) {
                usernameLayout.error = "3-20 characters: letters, numbers, underscore only"
                return@setOnClickListener
            }

            usernameLayout.error = null
            showLoading()

            lifecycleScope.launch {
                try {
                    // Check if username is available
                    val exists = firestore.collection("usernames")
                        .document(username)
                        .get()
                        .await()
                        .exists()

                    if (exists) {
                        hideLoading()
                        usernameLayout.error = "Username already taken"
                        return@launch
                    }

                    // Create user profile and username mapping
                    createUserProfile(user, username)
                    dialog.dismiss()
                    
                    showSuccess("Welcome, @$username!")
                    navigateToMainActivity(username)
                } catch (e: Exception) {
                    hideLoading()
                    usernameLayout.error = "Error: ${e.message}"
                }
            }
        }
    }

    /**
     * Create user profile and username mapping in Firestore
     */
    private suspend fun createUserProfile(user: FirebaseUser, username: String) {
        val batch = firestore.batch()

        // 1. Create username mapping
        val usernameRef = firestore.collection("usernames").document(username)
        batch.set(usernameRef, mapOf(
            "uid" to user.uid,
            "createdAt" to System.currentTimeMillis()
        ))

        // 2. Create user profile
        val userRef = firestore.collection("users").document(username)
        batch.set(userRef, mapOf(
            "uid" to user.uid,
            "username" to username,
            "email" to (user.email ?: ""),
            "displayName" to (user.displayName ?: username),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "createdAt" to System.currentTimeMillis(),
            "lastLogin" to System.currentTimeMillis()
        ))

        batch.commit().await()
        
        // Save username to SharedPreferences
        saveUsernameToPrefs(username)
    }

    private fun saveUsernameToPrefs(username: String) {
        getSharedPreferences("user_prefs", MODE_PRIVATE)
            .edit()
            .putString("current_username", username)
            .apply()
    }

    private fun navigateToMainActivity(username: String) {
        showLoading()

        val user = auth.currentUser
        if (user != null) {
            // Check Firebase Firestore for existing report using username
            firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")
                .get()
                .addOnSuccessListener { document ->
                    hideLoading()

                    if (document.exists()) {
                        // Report exists - returning user
                        syncProgressAndNavigate(username)
                    } else {
                        // No report - first time user, go to Assessment
                        val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    hideLoading()
                    // On failure, check with old path for backwards compatibility
                    checkLegacyReportAndNavigate(user.uid, username)
                }
        } else {
            hideLoading()
        }
    }

    private fun checkLegacyReportAndNavigate(uid: String, username: String) {
        // Check old assessment_reports collection
        firestore.collection("assessment_reports")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Migrate old report to new location
                    lifecycleScope.launch {
                        migrateReport(document.data, username)
                        syncProgressAndNavigate(username)
                    }
                } else {
                    // No report anywhere - go to Assessment
                    val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                }
            }
            .addOnFailureListener {
                // Go to Assessment on error
                val intent = Intent(this, com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
    }

    private suspend fun migrateReport(reportData: Map<String, Any>?, username: String) {
        if (reportData != null) {
            firestore.collection("users")
                .document(username)
                .collection("data")
                .document("report")
                .set(reportData)
                .await()
        }
    }

    private fun showLoading() {
        binding.progressBarLogin.visibility = View.VISIBLE
        binding.buttonLogin.isEnabled = false
        binding.buttonSignInWithGoogle.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBarLogin.visibility = View.GONE
        binding.buttonLogin.isEnabled = true
        binding.buttonSignInWithGoogle.isEnabled = true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToIntroduction() {
        val intent = Intent(this, IntroductionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in AND has a saved username
        // This prevents auto-redirect when user needs to set up username
        val currentUser = auth.currentUser
        val savedUsername = getSharedPreferences("user_prefs", MODE_PRIVATE)
            .getString("current_username", null)
        
        if (currentUser != null && !savedUsername.isNullOrEmpty()) {
            // User is logged in and has profile set up
            navigateToIntroduction()
        }
        // If currentUser exists but no savedUsername, let them proceed to login
        // so checkUserProfileAndNavigate can handle the profile setup
    }

    /**
     * CLOUD-ONLY: Sync progress from cloud and navigate to MainActivity
     */
    private fun syncProgressAndNavigate(username: String) {
        lifecycleScope.launch {
            try {
                val syncService = UserProgressSyncService(this@LoginActivity)
                
                // Load progress from cloud only (no local merge)
                val progress = syncService.loadProgressFromCloud()
                
                if (progress != null) {
                    android.util.Log.d("LoginActivity", "Progress loaded from cloud for user: $username")
                } else {
                    android.util.Log.d("LoginActivity", "New user, no cloud progress yet: $username")
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginActivity", "Error loading progress: ${e.message}")
            }
            
            // CLOUD-ONLY: No local flags - Firebase report existence is checked on HomeFragment
            
            // Navigate to MainActivity
            runOnUiThread {
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
        }
    }
    
    /**
     * Show forgot password dialog
     */
    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.editTextForgotEmail)
        val emailLayout = dialogView.findViewById<TextInputLayout>(R.id.textFieldForgotEmail)
        
        // Pre-fill with current email if available
        val currentEmail = binding.editTextEmailLogin.text.toString().trim()
        if (currentEmail.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            emailInput.setText(currentEmail)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("Enter your email address and we'll send you a link to reset your password.")
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("Send Reset Link", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Override positive button to add validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val email = emailInput.text.toString().trim()
            
            if (email.isEmpty()) {
                emailLayout.error = "Email is required"
                return@setOnClickListener
            }
            
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Please enter a valid email"
                return@setOnClickListener
            }
            
            emailLayout.error = null
            
            // Send password reset email
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    dialog.dismiss()
                    if (task.isSuccessful) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Email Sent ✓")
                            .setMessage("If an account exists with $email, you will receive a password reset link shortly.\n\nPlease check your inbox and spam folder.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Error")
                            .setMessage("Failed to send reset email: ${task.exception?.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        }
    }
}
