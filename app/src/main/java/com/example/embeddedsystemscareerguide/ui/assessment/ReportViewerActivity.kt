package com.example.embeddedsystemscareerguide.ui.assessment

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.databinding.ActivityReportViewerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportViewerBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentReportHtml: String? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

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

        // Load report from Firebase
        loadReportFromFirebase()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.report_viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_download -> {
                downloadReport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

            // Get username from SharedPreferences
            val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val username = userPrefs.getString("current_username", null)

            if (username != null) {
                // Load from new path: users/{username}/data/report
                firestore.collection("users")
                    .document(username)
                    .collection("data")
                    .document("report")
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val htmlContent = document.getString("reportHtml")
                            if (htmlContent != null) {
                                currentReportHtml = htmlContent
                                binding.webView.loadDataWithBaseURL(
                                    null,
                                    htmlContent,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            } else {
                                loadLegacyReport(user.uid)
                            }
                        } else {
                            loadLegacyReport(user.uid)
                        }
                    }
                    .addOnFailureListener {
                        loadLegacyReport(user.uid)
                    }
            } else {
                // Fallback to legacy path
                loadLegacyReport(user.uid)
            }
        } else {
            showErrorMessage()
        }
    }

    /**
     * Load report from legacy assessment_reports collection for backwards compatibility
     */
    private fun loadLegacyReport(userId: String) {
        firestore.collection("assessment_reports")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val htmlContent = document.getString("reportHtml")
                    if (htmlContent != null) {
                        currentReportHtml = htmlContent
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
    }

    private fun downloadReport() {
        val htmlContent = currentReportHtml
        if (htmlContent == null) {
            Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for storage permission on older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        saveReportToDownloads(htmlContent)
    }

    private fun saveReportToDownloads(htmlContent: String) {
        try {
            val username = getSharedPreferences("user_prefs", MODE_PRIVATE)
                .getString("current_username", "user") ?: "user"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Embedded_Systems_Report_${username}_$timestamp.html"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(htmlContent.toByteArray())
                    }
                    Toast.makeText(this, "✓ Report saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save report", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Legacy storage for older Android versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(htmlContent.toByteArray())
                }
                Toast.makeText(this, "✓ Report saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving report: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                currentReportHtml?.let { saveReportToDownloads(it) }
            } else {
                Toast.makeText(this, "Storage permission required to download report", Toast.LENGTH_SHORT).show()
            }
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
}
