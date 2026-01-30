# Gemini API Integration

## Overview

The app uses **Google Gemini API** (gemini-2.0-flash-exp model) for AI-powered features:
- Quiz question generation
- Assessment reports
- Chat tutor

## Configuration

### API Key Setup

1. Get API key from [Google AI Studio](https://aistudio.google.com/)
2. Add to `local.properties`:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```
3. Access in code via `BuildConfig.GEMINI_API_KEY`

### Endpoint

```
https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent
```

## Request Format

```kotlin
val requestBody = mapOf(
    "contents" to listOf(
        mapOf(
            "role" to "user",
            "parts" to listOf(
                mapOf("text" to prompt)
            )
        )
    ),
    "generationConfig" to mapOf(
        "temperature" to 0.7,
        "maxOutputTokens" to 2048
    ),
    "safetySettings" to safetySettings
)
```

## Safety Settings

All safety filters set to `BLOCK_NONE` for educational content:
- HARM_CATEGORY_HARASSMENT
- HARM_CATEGORY_HATE_SPEECH
- HARM_CATEGORY_SEXUALLY_EXPLICIT
- HARM_CATEGORY_DANGEROUS_CONTENT

## Cost Analysis

| Feature | Tokens/Request | Requests/User | Monthly Cost |
|---------|----------------|---------------|--------------|
| Quiz (5 Q) | ~1,000 | 16 stages | ~$0.01 |
| Report | ~8,000 | 1 initial | ~$0.01 |
| Chat | ~500 | 50 msgs | ~$0.03 |

**Estimated**: ~$0.05/user/month (well within free tier)

## Error Handling

```kotlin
try {
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        throw GeminiApiException(response.code)
    }
} catch (e: IOException) {
    // Network error - show retry option
}
```

## Best Practices

1. **Cache responses** where possible
2. **Use lower temperature** for consistent answers
3. **Set max tokens** to prevent excessive usage
4. **Handle rate limits** gracefully
