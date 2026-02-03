package com.example.embeddedsystemscareerguide.ui.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.embeddedsystemscareerguide.R
import com.example.embeddedsystemscareerguide.services.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ContentReadingActivity - Kindle-Style Learning Content Reader
 * 
 * Provides an immersive reading experience with swipe navigation
 * between content sections (Theory, Key Points, Code, etc.)
 * 
 * @author Embedded Systems Career Guide
 * @version 2.0
 */
class ContentReadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STAGE_ID = "extra_stage_id"
        const val EXTRA_STAGE_TITLE = "extra_stage_title"
        const val EXTRA_STAGE_TOPICS = "extra_stage_topics"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewPager: ViewPager2
    private lateinit var loadingLayout: View
    private lateinit var loadingText: TextView
    private lateinit var errorLayout: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var bottomNavCard: MaterialCardView
    private lateinit var pageIndicator: TextView
    private lateinit var readingProgress: LinearProgressIndicator
    private lateinit var readingTime: TextView

    private lateinit var contentService: StageContentService
    private var stageId: Int = 0
    private var stageTitle: String = ""
    private var stageTopics: List<String> = emptyList()
    private var pageCount: Int = 6  // 6 sections total
    
    // Main thread handler for UI operations
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_reading)

        // Get intent extras
        stageId = intent.getIntExtra(EXTRA_STAGE_ID, 0)
        stageTitle = intent.getStringExtra(EXTRA_STAGE_TITLE) ?: "Stage $stageId"
        stageTopics = intent.getStringArrayListExtra(EXTRA_STAGE_TOPICS) ?: emptyList()

        // Initialize services
        contentService = StageContentService.getInstance(this)

        // Initialize views
        initializeViews()
        setupToolbar()
        setupViewPager()

        // Load content
        loadContent()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        viewPager = findViewById(R.id.viewPager)
        loadingLayout = findViewById(R.id.loadingLayout)
        loadingText = findViewById(R.id.loadingText)
        errorLayout = findViewById(R.id.errorLayout)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)
        bottomNavCard = findViewById(R.id.bottomNavCard)
        pageIndicator = findViewById(R.id.pageIndicator)
        readingProgress = findViewById(R.id.readingProgress)
        readingTime = findViewById(R.id.readingTime)

        retryButton.setOnClickListener {
            loadContent(forceRegenerate = true)
        }
    }

    private fun setupToolbar() {
        toolbar.title = stageTitle
        toolbar.subtitle = "Stage $stageId"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViewPager() {
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateProgressIndicator(position)
            }
        })
    }

    private fun loadContent(forceRegenerate: Boolean = false) {
        showLoading("Loading content...")

        // Create a PersonalizedStage object for the service
        val stage = PersonalizedStage(
            id = stageId,
            title = stageTitle,
            topics = stageTopics
        )

        // Launch on Main dispatcher to ensure callback safety
        CoroutineScope(Dispatchers.Main).launch {
            if (forceRegenerate) {
                contentService.regenerateContent(stage, object : StageContentService.ContentCallback {
                    override fun onProgress(message: String) {
                        safeRunOnUiThread { loadingText.text = message }
                    }

                    override fun onSuccess(content: StageContent) {
                        safeRunOnUiThread { displayContent(content) }
                    }

                    override fun onError(error: String) {
                        safeRunOnUiThread { showError(error) }
                    }
                })
            } else {
                contentService.getStageContent(stage, object : StageContentService.ContentCallback {
                    override fun onProgress(message: String) {
                        safeRunOnUiThread { loadingText.text = message }
                    }

                    override fun onSuccess(content: StageContent) {
                        safeRunOnUiThread { displayContent(content) }
                    }

                    override fun onError(error: String) {
                        safeRunOnUiThread { showError(error) }
                    }
                })
            }
        }
    }
    
    /**
     * Safely run a block on the UI thread
     * Ensures we don't crash if called from wrong thread
     */
    private fun safeRunOnUiThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread
            block()
        } else {
            // Post to main thread
            mainHandler.post(block)
        }
    }

    private fun displayContent(content: StageContent) {
        // Build pages from the StageContent structure
        val pages = buildPagesFromContent(content)
        pageCount = pages.size

        // Set up adapter
        val adapter = ContentPageAdapter(this, pages)
        viewPager.adapter = adapter

        // Estimate reading time (roughly 1.5 min per section)
        readingTime.text = "~${pages.size * 1 + 5} min"

        // Show content
        loadingLayout.visibility = View.GONE
        errorLayout.visibility = View.GONE
        viewPager.visibility = View.VISIBLE
        bottomNavCard.visibility = View.VISIBLE

        // Initialize progress
        updateProgressIndicator(0)
    }

    private fun buildPagesFromContent(content: StageContent): List<ContentPage> {
        val pages = mutableListOf<ContentPage>()

        // 1. Theory section
        if (content.theory.isNotBlank()) {
            pages.add(ContentPage(
                type = "theory",
                title = "Theory & Concepts",
                textContent = content.theory
            ))
        }

        // 2. Key Points section
        if (content.keyPoints.isNotEmpty()) {
            pages.add(ContentPage(
                type = "key_points",
                title = "Key Takeaways",
                bulletPoints = content.keyPoints.map { "🔑 $it" }
            ))
        }

        // 3. Code Example section
        if (content.codeExample.code.isNotBlank()) {
            pages.add(ContentPage(
                type = "code_examples",
                title = "Code Example",
                codeLanguage = content.codeExample.language.uppercase(),
                codeContent = content.codeExample.code,
                codeExplanation = content.codeExample.explanation
            ))
        }

        // 4. Common Mistakes section
        if (content.commonMistakes.isNotEmpty()) {
            pages.add(ContentPage(
                type = "common_mistakes",
                title = "Common Mistakes to Avoid",
                bulletPoints = content.commonMistakes.map { 
                    "❌ ${it.mistake}\n✅ ${it.solution}" 
                }
            ))
        }

        // 5. Pro Tips section
        if (content.proTips.isNotEmpty()) {
            pages.add(ContentPage(
                type = "pro_tips",
                title = "Pro Tips",
                bulletPoints = content.proTips.map { "💡 $it" }
            ))
        }

        // 6. Mini Challenge section
        if (content.miniChallenge.task.isNotBlank()) {
            pages.add(ContentPage(
                type = "mini_challenge",
                title = "Mini Challenge",
                textContent = "🎯 **Your Task:**\n${content.miniChallenge.task}\n\n💡 **Hint:**\n${content.miniChallenge.hint}"
            ))
        }

        // If no pages were created, add a default page
        if (pages.isEmpty()) {
            pages.add(ContentPage(
                type = "theory",
                title = "Content Loading...",
                textContent = "Content is being generated. Please try again in a moment."
            ))
        }

        return pages
    }

    private fun updateProgressIndicator(position: Int) {
        val total = if (pageCount > 0) pageCount else 1
        pageIndicator.text = "${position + 1} of $total"
        
        val progress = ((position + 1).toFloat() / total * 100).toInt()
        readingProgress.progress = progress
    }

    private fun showLoading(message: String) {
        loadingLayout.visibility = View.VISIBLE
        loadingText.text = message
        errorLayout.visibility = View.GONE
        viewPager.visibility = View.GONE
        bottomNavCard.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorLayout.visibility = View.VISIBLE
        errorText.text = message
        loadingLayout.visibility = View.GONE
        viewPager.visibility = View.GONE
        bottomNavCard.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks
        mainHandler.removeCallbacksAndMessages(null)
    }
}

/**
 * Data class representing a single page/section of content
 */
data class ContentPage(
    val type: String,
    val title: String,
    val textContent: String = "",
    val bulletPoints: List<String> = emptyList(),
    val codeLanguage: String = "",
    val codeContent: String = "",
    val codeExplanation: String = ""
)

/**
 * Adapter for content pages in ViewPager2
 */
class ContentPageAdapter(
    private val context: Context,
    private val pages: List<ContentPage>
) : RecyclerView.Adapter<ContentPageAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionType: TextView = view.findViewById(R.id.sectionType)
        val sectionTitle: TextView = view.findViewById(R.id.sectionTitle)
        val mainContent: TextView = view.findViewById(R.id.mainContent)
        val bulletPointsContainer: LinearLayout = view.findViewById(R.id.bulletPointsContainer)
        val codeBlockCard: MaterialCardView = view.findViewById(R.id.codeBlockCard)
        val codeLanguage: TextView = view.findViewById(R.id.codeLanguage)
        val codeContent: TextView = view.findViewById(R.id.codeContent)
        val codeExplanation: TextView = view.findViewById(R.id.codeExplanation)
        val copyCodeButton: ImageButton = view.findViewById(R.id.copyCodeButton)
        val swipeHint: View = view.findViewById(R.id.swipeHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content_page, parent, false)
        return PageViewHolder(view)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]

        // Set section type badge
        holder.sectionType.text = getSectionTypeLabel(page.type)
        holder.sectionTitle.text = page.title

        // Reset visibility
        holder.mainContent.visibility = View.GONE
        holder.bulletPointsContainer.visibility = View.GONE
        holder.codeBlockCard.visibility = View.GONE
        holder.swipeHint.visibility = View.GONE

        // Show content based on page type
        when (page.type) {
            "theory", "mini_challenge" -> {
                holder.mainContent.text = page.textContent
                holder.mainContent.visibility = View.VISIBLE
            }
            "key_points", "common_mistakes", "pro_tips" -> {
                displayBulletPoints(holder, page.bulletPoints)
            }
            "code_examples" -> {
                holder.codeLanguage.text = page.codeLanguage
                holder.codeContent.text = page.codeContent
                holder.codeBlockCard.visibility = View.VISIBLE

                if (page.codeExplanation.isNotEmpty()) {
                    holder.codeExplanation.text = page.codeExplanation
                    holder.codeExplanation.visibility = View.VISIBLE
                } else {
                    holder.codeExplanation.visibility = View.GONE
                }

                // Copy button
                holder.copyCodeButton.setOnClickListener {
                    copyToClipboard(page.codeContent)
                }
            }
        }

        // Show swipe hint on first page
        if (position == 0 && pages.size > 1) {
            holder.swipeHint.visibility = View.VISIBLE
        }
    }

    private fun getSectionTypeLabel(type: String): String {
        return when (type) {
            "theory" -> "📚 THEORY"
            "key_points" -> "🔑 KEY POINTS"
            "code_examples" -> "💻 CODE"
            "common_mistakes" -> "⚠️ MISTAKES"
            "pro_tips" -> "💡 PRO TIPS"
            "mini_challenge" -> "🎯 CHALLENGE"
            else -> type.uppercase()
        }
    }

    private fun displayBulletPoints(holder: PageViewHolder, points: List<String>) {
        holder.bulletPointsContainer.removeAllViews()
        
        points.forEach { point ->
            val textView = TextView(context).apply {
                text = point
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 16f
                setPadding(0, 12, 0, 12)
                setLineSpacing(0f, 1.4f)
            }
            holder.bulletPointsContainer.addView(textView)
        }
        
        holder.bulletPointsContainer.visibility = View.VISIBLE
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
