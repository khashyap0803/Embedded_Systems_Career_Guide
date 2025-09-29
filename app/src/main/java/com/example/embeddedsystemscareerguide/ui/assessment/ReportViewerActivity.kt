package com.example.embeddedsystemscareerguide.ui.assessment

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.embeddedsystemscareerguide.databinding.ActivityReportViewerBinding
import java.io.File

class ReportViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportViewerBinding

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

        // Load report
        loadReport()
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

    private fun loadReport() {
        try {
            // Try to load from local storage first
            val reportFile = File(filesDir, "assessment_report.html")

            if (reportFile.exists()) {
                val htmlContent = reportFile.readText()
                binding.webView.loadDataWithBaseURL(
                    null,
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            } else {
                // Local file doesn't exist, try Firebase
                loadReportFromFirebase()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadReportFromFirebase()
        }
    }

    private fun loadReportFromFirebase() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("assessment_reports")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val htmlContent = document.getString("reportHtml")
                        if (htmlContent != null) {
                            // Save locally for future offline access
                            saveReportLocally(htmlContent)

                            // Display report
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

    private fun saveReportLocally(htmlContent: String) {
        try {
            val reportFile = File(filesDir, "assessment_report.html")
            reportFile.writeText(htmlContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showErrorMessage() {
        // Show error message
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
                    <p>You haven't completed the assessment yet. Please take the assessment first to generate your personalized report.</p>
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
