# GeminiReportService.kt

> **Location**: `app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiReportService.kt`

## Purpose

Generates comprehensive, personalized **assessment reports** using Google's Gemini API. Transforms quiz answers into a detailed HTML report with feedback and a 12-week learning roadmap.

## Functionality

### Core Method

```kotlin
suspend fun generateReport(
    userName: String,
    userEmail: String,
    qaList: List<Pair<AssessmentQuestion, String>>,
    progressCallback: ProgressCallback?
): String
```

### Report Phases

| Phase | Description |
|-------|-------------|
| 1-10 | Question-by-question feedback generation |
| 11 | 12-week personalized roadmap |
| 12 | Final HTML compilation |

### Output Structure

```html
<!DOCTYPE html>
<html>
<head>
  <style>/* Premium dark theme CSS */</style>
</head>
<body>
  <header>User info, date, summary</header>
  <section>Skill Analysis</section>
  <section>Question Feedback (detailed)</section>
  <section>12-Week Learning Roadmap</section>
  <footer>Motivational quote</footer>
</body>
</html>
```

## Why It's Important

1. **Personalization**: Each report is unique to user's answers
2. **Actionable Insights**: Specific feedback per question
3. **Roadmap**: Structured 12-week plan for improvement
4. **Visual Appeal**: Premium dark theme HTML styling

## Where It's Used

| Component | Usage |
|-----------|-------|
| AssessmentActivity | Generates report after quiz completion |
| ReportViewerActivity | Displays saved reports |

## API Integration

### Gemini API Call Pattern

```kotlin
// Uses gemini-2.0-flash-exp model
val response = OkHttpClient.executeRequest(
    url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent",
    apiKey = BuildConfig.GEMINI_API_KEY,
    prompt = buildPrompt(question, answer)
)
```

### Safety Settings

```kotlin
safetySettings = listOf(
    HarmCategory.HARASSMENT -> BLOCK_NONE,
    HarmCategory.HATE_SPEECH -> BLOCK_NONE,
    HarmCategory.SEXUALLY_EXPLICIT -> BLOCK_NONE,
    HarmCategory.DANGEROUS_CONTENT -> BLOCK_NONE
)
```

## Strengths

- ✅ Beautiful HTML report design
- ✅ Progress callback for UI updates
- ✅ Comprehensive question feedback
- ✅ Actionable 12-week roadmap

## Weaknesses

- ⚠️ Single API model (no fallback)
- ⚠️ Large file size (~33KB)
- ⚠️ No report versioning
- ⚠️ HTML stored as string (no structured data)

## Potential Improvements

1. **Add fallback model** for API failures
2. **Implement report caching** to avoid regeneration
3. **Store structured data** alongside HTML
4. **Add PDF export** option
