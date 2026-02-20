# Content Reading Module — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.content`  
> **File:** `ContentReadingActivity.kt` (430 lines)  
> **Contains:** `ContentReadingActivity` class, `ContentPage` data class, `ContentPageAdapter` class

---

## ContentReadingActivity

### Purpose
Provides an immersive, swipe-based reading experience for learning stage content. Loads AI-generated content from `StageContentService`, breaks it into typed sections (Theory, Key Points, Code Examples, Common Mistakes, Pro Tips, Mini Challenge), and displays them in a `ViewPager2`.

### Class Overview

| Element | Type | Role |
|---|---|---|
| `ContentReadingActivity` | `AppCompatActivity` | Hosts the immersive reader |
| `EXTRA_STAGE_ID` | `Int` (companion) | Intent extra key for stage ID |
| `EXTRA_STAGE_TITLE` | `String` (companion) | Intent extra key for stage title |
| `EXTRA_STAGE_TOPICS` | `List<String>` (companion) | Intent extra key for stage topics |
| `toolbar` | `MaterialToolbar` | Top bar with stage title and back navigation |
| `viewPager` | `ViewPager2` | Swipeable content pages |
| `loadingLayout/errorLayout` | `View` | State containers |
| `pageIndicator` | `TextView` | Shows "X of Y" page position |
| `readingProgress` | `LinearProgressIndicator` | Visual progress bar |
| `readingTime` | `TextView` | Estimated reading time |
| `contentService` | `StageContentService` | Fetches/generates stage content |
| `mainHandler` | `Handler(Looper.getMainLooper())` | Thread-safe UI posting |

### All Functions

#### `onCreate(savedInstanceState: Bundle?)` (lines 67–86)
- Reads `stageId`, `stageTitle`, and `stageTopics` from intent extras.
- Initialises `StageContentService.getInstance(this)`.
- Calls `initializeViews()`, `setupToolbar()`, `setupViewPager()`, `loadContent()`.

#### `initializeViews()` (lines 88–104)
- Finds all views by ID: toolbar, viewPager, loading/error layouts, progress indicator, page counter, reading time.
- Sets retry button click → `loadContent(forceRegenerate = true)`.

#### `setupToolbar()` (lines 106–110)
- Sets toolbar title to `stageTitle`, subtitle to `"Stage {id}"`.
- Sets navigation click → `finish()`.

#### `setupViewPager()` (lines 112–119)
- Registers a `OnPageChangeCallback` that calls `updateProgressIndicator(position)` on each page change.

#### `loadContent(forceRegenerate: Boolean = false)` (lines 121–163)
- Shows loading state.
- Creates a `PersonalizedStage(id, title, topics)` object.
- Launches on `Dispatchers.Main`:
  - **Force regenerate:** Calls `contentService.regenerateContent(stage, callback)`.
  - **Normal:** Calls `contentService.getStageContent(stage, callback)`.
- The `ContentCallback` has three methods:
  - `onProgress(message)` → updates loading text via `safeRunOnUiThread`.
  - `onSuccess(content)` → calls `displayContent(content)` via `safeRunOnUiThread`.
  - `onError(error)` → calls `showError(error)` via `safeRunOnUiThread`.

#### `safeRunOnUiThread(block: () -> Unit)` (lines 165–177)
- If already on main thread: executes block directly.
- Otherwise: posts to `mainHandler`.
- Prevents crashes from wrong-thread UI updates.

#### `displayContent(content: StageContent)` (lines 179–199)
- Builds pages from `StageContent` via `buildPagesFromContent()`.
- Creates and sets `ContentPageAdapter` on the ViewPager.
- Estimates reading time: `~{pages × 1 + 5} min`.
- Hides loading/error, shows ViewPager and bottom nav card.
- Initialises progress indicator at position 0.

#### `buildPagesFromContent(content: StageContent): List<ContentPage>` (lines 201–272)
- Converts `StageContent` into a list of `ContentPage` objects:

| Order | Section | Page Type | Source Field |
|---|---|---|---|
| 1 | Theory & Concepts | `"theory"` | `content.theory` |
| 2 | Key Takeaways | `"key_points"` | `content.keyPoints` (prefixed with 🔑) |
| 3 | Code Example | `"code_examples"` | `content.codeExample.{code, language, explanation}` |
| 4 | Common Mistakes | `"common_mistakes"` | `content.commonMistakes` (❌ mistake / ✅ solution format) |
| 5 | Pro Tips | `"pro_tips"` | `content.proTips` (prefixed with 💡) |
| 6 | Mini Challenge | `"mini_challenge"` | `content.miniChallenge.{task, hint}` |

- Skips empty sections. If no pages created, adds a fallback "Content Loading..." page.

#### `updateProgressIndicator(position: Int)` (lines 274–280)
- Updates `pageIndicator` text to `"{pos+1} of {total}"`.
- Sets `readingProgress` to percentage: `(pos+1) / total * 100`.

#### `showLoading(message: String)` (lines 282–288)
- Shows `loadingLayout` with the given message, hides everything else.

#### `showError(message: String)` (lines 290–296)
- Shows `errorLayout` with the error message, hides everything else.

#### `onDestroy()` (lines 298–302)
- Removes all pending callbacks from `mainHandler` to prevent leaks.

---

## ContentPage (data class, lines 308–316)

Represents a single swipeable page/section of content.

| Property | Type | Default | Purpose |
|---|---|---|---|
| `type` | `String` | — | Section type: `"theory"`, `"key_points"`, `"code_examples"`, `"common_mistakes"`, `"pro_tips"`, `"mini_challenge"` |
| `title` | `String` | — | Section heading |
| `textContent` | `String` | `""` | Main body text (for theory/challenge pages) |
| `bulletPoints` | `List<String>` | `emptyList()` | Bullet items (for key points/mistakes/tips) |
| `codeLanguage` | `String` | `""` | Programming language label |
| `codeContent` | `String` | `""` | Source code text |
| `codeExplanation` | `String` | `""` | Explanation text below code |

---

## ContentPageAdapter (lines 321–429)

A `RecyclerView.Adapter` that powers the `ViewPager2` swipe pages.

### Inner Class: `PageViewHolder` (lines 326–337)
Holds references to all views in `item_content_page.xml`:
- `sectionType` (badge), `sectionTitle`, `mainContent`, `bulletPointsContainer`, `codeBlockCard`, `codeLanguage`, `codeContent`, `codeExplanation`, `copyCodeButton`, `swipeHint`.

### Functions

#### `onCreateViewHolder(parent, viewType)` (lines 339–343)
- Inflates `R.layout.item_content_page`.

#### `getItemCount()` (line 345)
- Returns `pages.size`.

#### `onBindViewHolder(holder, position)` (lines 347–392)
- Sets section type badge via `getSectionTypeLabel()` and title.
- Resets all content views to `GONE`.
- Shows appropriate content based on `page.type`:
  - **`"theory"` / `"mini_challenge"`:** Shows `mainContent` with `textContent`.
  - **`"key_points"` / `"common_mistakes"` / `"pro_tips"`:** Calls `displayBulletPoints()`.
  - **`"code_examples"`:** Shows code block card with language label, code content, optional explanation, and copy-to-clipboard button.
- Shows `swipeHint` on the first page if there are multiple pages.

#### `getSectionTypeLabel(type: String): String` (lines 394–404)
- Maps type strings to emoji-prefixed labels:

| Type | Label |
|---|---|
| `"theory"` | 📚 THEORY |
| `"key_points"` | 🔑 KEY POINTS |
| `"code_examples"` | 💻 CODE |
| `"common_mistakes"` | ⚠️ MISTAKES |
| `"pro_tips"` | 💡 PRO TIPS |
| `"mini_challenge"` | 🎯 CHALLENGE |

#### `displayBulletPoints(holder, points)` (lines 406–421)
- Clears the container, then creates a `TextView` for each bullet point with primary text colour, 16sp, and 1.4× line spacing.

#### `copyToClipboard(text: String)` (lines 423–428)
- Copies code text to system clipboard using `ClipboardManager`.
- Shows "Code copied to clipboard" toast.

### Design Decisions
- **Section-based pagination:** Each content type gets its own swipeable page. Avoids overwhelming the user with a single long scroll.
- **Thread-safe callbacks:** `safeRunOnUiThread()` + `Handler` ensures all UI updates happen on the main thread, even when callbacks arrive from background coroutines.
- **Force regenerate:** The retry button triggers `forceRegenerate = true`, which bypasses the cache and re-requests content from the AI service.
- **Estimated reading time:** Simple heuristic of `pages × 1 + 5` minutes.
