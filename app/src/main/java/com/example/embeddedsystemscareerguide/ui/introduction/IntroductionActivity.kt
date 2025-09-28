package com.example.embeddedsystemscareerguide.ui.introduction

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityIntroductionBinding
import com.example.embeddedsystemscareerguide.ui.assessment.AssessmentActivity

class IntroductionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroductionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroductionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.buttonStartAssessment.setOnClickListener {
            startActivity(Intent(this, AssessmentActivity::class.java))
            finish()
        }
    }
}
