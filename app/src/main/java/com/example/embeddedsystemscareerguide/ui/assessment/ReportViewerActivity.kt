package com.example.embeddedsystemscareerguide.ui.assessment

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityReportViewerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportViewerBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Assessment Report"

        // Configure WebView
        setupWebView()

        // Load report from Firebase only
        loadReportFromFirebase()
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
            }
            webViewClient = WebViewClient()
        }
    }

    private fun loadReportFromFirebase() {
        val user = auth.currentUser
        if (user != null) {
            // Show loading in WebView
            showLoadingMessage()

            firestore.collection("assessment_reports")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val htmlContent = document.getString("reportHtml")
                        if (htmlContent != null) {
                            // Display report directly from Firebase
                            binding.webView.loadDataWithBaseURL(
                                null,
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        } else {
                            showErrorMessage()
                        }
                    } else {
                        showErrorMessage()
                    }
                }
                .addOnFailureListener {
                    showErrorMessage()
                }
        } else {
            showErrorMessage()
        }
    }

    private fun showLoadingMessage() {
        val loadingHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background-color: #0f172a;
                        color: #cbd5e1;
                        padding: 2rem;
                        text-align: center;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                    }
                    .loading-container {
                        max-width: 400px;
                    }
                    .spinner {
                        border: 4px solid #1e293b;
                        border-top: 4px solid #6366f1;
                        border-radius: 50%;
                        width: 50px;
                        height: 50px;
                        animation: spin 1s linear infinite;
                        margin: 0 auto 1rem;
                    }
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                    h1 {
                        color: #6366f1;
                        font-size: 1.5rem;
                        margin-bottom: 1rem;
                    }
                </style>
            </head>
            <body>
                <div class="loading-container">
                    <div class="spinner"></div>
                    <h1>Loading Your Report...</h1>
                    <p>Please wait while we fetch your assessment report from the cloud.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL(
            null,
            loadingHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showErrorMessage() {
        val errorHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background-color: #0f172a;
                        color: #cbd5e1;
                        padding: 2rem;
                        text-align: center;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                    }
                    .error-container {
                        max-width: 400px;
                    }
                    h1 {
                        color: #f87171;
                        font-size: 1.5rem;
                        margin-bottom: 1rem;
                    }
                    p {
                        line-height: 1.6;
                    }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <h1>📄 No Report Found</h1>
                    <p>Unable to load your assessment report. Please ensure you have completed the assessment and have an active internet connection.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL(
            null,
            errorHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
