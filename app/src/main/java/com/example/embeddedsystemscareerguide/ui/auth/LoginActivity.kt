@file:Suppress("DEPRECATION") // We are intentionally using the older, more stable Google Sign-In API

package com.example.embeddedsystemscareerguide.ui.auth

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityLoginBinding
import com.example.embeddedsystemscareerguide.ui.introduction.IntroductionActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

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
                showError("Google sign in failed: ${e.message}")
            }
        } else {
            hideLoading()
            showError("Google sign in cancelled")
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
        val email = binding.editTextEmailLogin.text.toString().trim()
        val password = binding.editTextPasswordLogin.text.toString()

        // Reset error states
        binding.textFieldEmailLogin.error = null
        binding.textFieldPasswordLogin.error = null

        var isValid = true

        // Validate email
        if (email.isEmpty()) {
            binding.textFieldEmailLogin.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textFieldEmailLogin.error = "Please enter a valid email"
            isValid = false
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
        val email = binding.editTextEmailLogin.text.toString().trim()
        val password = binding.editTextPasswordLogin.text.toString()

        showLoading()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                hideLoading()
                if (task.isSuccessful) {
                    // Login successful
                    showSuccess("Welcome back!")
                    navigateToMainActivity()
                } else {
                    // Login failed
                    showError("Login failed: ${task.exception?.message}")
                }
            }
    }

    private fun signInWithGoogle() {
        showLoading()
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                hideLoading()
                if (task.isSuccessful) {
                    // Sign in success
                    showSuccess("Welcome!")
                    navigateToMainActivity()
                } else {
                    // Sign in failed
                    showError("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
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

    private fun shakeView(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 500
        shake.start()
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
        // Check if user is signed in and update UI accordingly
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToIntroduction()
        }
    }
}
