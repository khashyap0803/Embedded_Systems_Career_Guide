package com.example.embeddedsystemscareerguide.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        binding.buttonRegister.setOnClickListener {
            registerUser()
        }
        binding.textViewGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val username = binding.editTextUsernameRegister.text.toString().trim()
        val email = binding.editTextEmailRegister.text.toString().trim()
        val password = binding.editTextPasswordRegister.text.toString().trim()
        val confirmPassword = binding.editTextConfirmPasswordRegister.text.toString().trim()
        if (username.isEmpty()) {
            binding.textFieldUsernameRegister.error = "Username is required"
            binding.editTextUsernameRegister.requestFocus()
            return
        } else {
            binding.textFieldUsernameRegister.error = null
        }
        if (email.isEmpty()) {
            binding.textFieldEmailRegister.error = "Email is required"
            binding.editTextEmailRegister.requestFocus()
            return
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textFieldEmailRegister.error = "Enter a valid email"
            binding.editTextEmailRegister.requestFocus()
            return
        } else {
            binding.textFieldEmailRegister.error = null
        }
        if (password.isEmpty()) {
            binding.textFieldPasswordRegister.error = "Password is required"
            binding.editTextPasswordRegister.requestFocus()
            return
        } else if (password.length < 6) {
            binding.textFieldPasswordRegister.error = "Password must be at least 6 characters"
            binding.editTextPasswordRegister.requestFocus()
            return
        } else {
            binding.textFieldPasswordRegister.error = null
        }
        if (confirmPassword.isEmpty()) {
            binding.textFieldConfirmPasswordRegister.error = "Confirm password is required"
            binding.editTextConfirmPasswordRegister.requestFocus()
            return
        } else if (password != confirmPassword) {
            binding.textFieldConfirmPasswordRegister.error = "Passwords do not match"
            binding.editTextConfirmPasswordRegister.requestFocus()
            return
        } else {
            binding.textFieldConfirmPasswordRegister.error = null
        }
        binding.progressBarRegister.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.progressBarRegister.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Registration successful. Please login.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                } else {
                    Toast.makeText(baseContext, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}
