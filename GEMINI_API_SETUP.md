## 🔑 GEMINI API CONFIGURATION GUIDE

### Quick Setup (5 minutes)

#### Step 1: Get Your Free Gemini API Key

1. Visit: **https://makersuite.google.com/app/apikey** (or https://aistudio.google.com/app/apikey)
2. Sign in with your Google account
3. Click **"Create API Key"** button
4. Select **"Create API key in new project"** (or use existing project)
5. Copy the generated API key (starts with "AIza...")

#### Step 2: Add API Key to Your Code

Open this file in Android Studio:
```
app/src/main/java/com/example/embeddedsystemscareerguide/services/GeminiReportService.kt
```

Find line 26 and replace the placeholder:

**BEFORE:**
```kotlin
private val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
```

**AFTER:**
```kotlin
private val GEMINI_API_KEY = "AIzaSyC_your_actual_key_here_xxxxxxxxxxxxxxx"
```

#### Step 3: Verify Your API Key Works

After adding the key:
1. Build the project (Ctrl+F9 or Cmd+F9)
2. Run on device/emulator
3. Complete an assessment
4. If report generates successfully, your API key is working! ✅

---

### API Usage & Limits (Free Tier)

**Gemini 2.0 Flash Experimental - FREE TIER:**
- **Requests per minute (RPM)**: 15
- **Requests per day (RPD)**: 1,500
- **Tokens per minute (TPM)**: 1,000,000

**For this app:**
- Each report generation = 4 API calls
- You can generate ~375 reports per day (FREE!)
- Perfect for development and moderate production use

---

### Security Best Practices

⚠️ **IMPORTANT**: For production apps, do NOT store API keys in code!

**Recommended approach for production:**

1. **Use BuildConfig (Better):**
   
   Add to `app/build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
       }
   }
   ```
   
   Add to `gradle.properties` (add to .gitignore):
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```
   
   Use in code:
   ```kotlin
   private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
   ```

2. **Use Backend API (Best for Production):**
   - Create Firebase Cloud Function
   - Store API key in Firebase environment variables
   - App calls your backend, which calls Gemini API
   - More secure, prevents API key exposure

---

### Testing Your Implementation

#### Test Checklist:

- [ ] API key added to GeminiReportService.kt
- [ ] Gradle sync successful
- [ ] Project builds without errors
- [ ] App runs on device/emulator
- [ ] Can complete assessment (all 50 questions)
- [ ] Loading overlay shows during generation
- [ ] Report generates successfully
- [ ] Report displays in WebView
- [ ] Report saved locally
- [ ] Report saved to Firebase
- [ ] "View Report" option works
- [ ] "Retake Assessment" option works
- [ ] First-time user flow: Login → Assessment → Home ✓
- [ ] Returning user flow: Login → Home ✓

---

### Troubleshooting Common Issues

**Issue: "API key not valid"**
- Solution: Double-check you copied the entire key (starts with "AIza")
- Solution: Verify the key is enabled in Google AI Studio
- Solution: Check API key restrictions (should allow your app package name)

**Issue: "Rate limit exceeded"**
- Solution: You've hit the 15 requests/minute limit
- Wait 1 minute and try again
- For production, implement request queuing

**Issue: "Network error"**
- Solution: Check internet connection
- Solution: Check if device/emulator has internet access
- Solution: Verify `INTERNET` permission in AndroidManifest.xml (already added)

**Issue: Report generation takes too long**
- Normal: 30-60 seconds for complete report
- If longer: Check internet speed
- If fails: Check Logcat for specific error

---

### Monitoring API Usage

1. Visit: https://aistudio.google.com/app/apikey
2. Click on your API key
3. View usage statistics
4. Set up alerts for quota limits

---

### Cost Considerations

**Current Setup (Free Tier):**
- Cost: $0
- Limit: 1,500 reports/day
- Perfect for: Development, Testing, Small-scale Production

**If You Exceed Free Tier:**
- Gemini API has paid tiers with higher limits
- Very affordable: ~$0.001 per report
- Automatic billing through Google Cloud

---

### Environment Variables (Recommended)

For better security, store API key in environment variables:

**Method 1: local.properties**

1. Open/create `local.properties`
2. Add: `GEMINI_API_KEY=your_key_here`
3. Add `local.properties` to `.gitignore`
4. Read in build.gradle.kts and inject into BuildConfig

**Method 2: System Environment**

1. Set system environment variable: `GEMINI_API_KEY`
2. Read in code: `System.getenv("GEMINI_API_KEY")`

---

### Quick Start Command

After adding API key, run:

```bash
# Clean and rebuild
gradlew clean build

# Or in Android Studio:
# Build → Clean Project
# Build → Rebuild Project
# Run → Run 'app'
```

---

### Support & Documentation

- **Gemini API Docs**: https://ai.google.dev/docs
- **API Key Management**: https://aistudio.google.com/app/apikey
- **Pricing**: https://ai.google.dev/pricing
- **Rate Limits**: https://ai.google.dev/gemini-api/docs/rate-limits

---

### Success Indicators

When everything is working correctly, you should see:

1. ✅ Assessment completes smoothly
2. ✅ Loading overlay appears with messages
3. ✅ Report generates in 30-60 seconds
4. ✅ Success toast: "Report generated successfully!"
5. ✅ Home page opens
6. ✅ Assessment card shows View/Retake options
7. ✅ Report displays beautifully in WebView
8. ✅ Report is mobile-optimized and scrollable

---

### Next Steps After Setup

1. Test with a real 50-question assessment
2. Review the generated report quality
3. Adjust prompts in GeminiReportService.kt if needed
4. Customize report styling (HTML/CSS in the prompt)
5. Add your own branding/logo to reports

---

**Ready to Go!** 🚀

Once you add your API key and sync Gradle, your app is fully functional!

