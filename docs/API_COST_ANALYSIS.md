# Gemini API Cost Analysis & Budget Planning

**Document Created:** January 29, 2026  
**App:** Embedded Systems Career Guide  
**Model:** Gemini 2.5 Flash

---

## 📊 Gemini 2.5 Flash Pricing

| Token Type | USD per 1M tokens | INR per 1M tokens |
|------------|-------------------|-------------------|
| **Input** (text/image) | $0.30 | ₹25.50 |
| **Output** (generated) | $2.50 | ₹212.50 |
| **Audio Input** | $1.00 | ₹85.00 |

> **Note:** Output tokens cost **8.3x more** than input tokens. Output is your main cost driver.

---

## 📈 Actual Usage Data (Testing Phase)

### January 27, 2026
| Type | Tokens | Cost |
|------|--------|------|
| Input | 26,204 | ₹0.71 |
| Output | 100,282 | ₹22.56 |
| **Total** | 126,486 | **₹23.27** |

### January 28, 2026
| Type | Tokens | Cost |
|------|--------|------|
| Input | 58,976 | ₹1.59 |
| Output | 480,103 | ₹108.00 |
| **Total** | 539,079 | **₹109.59** |

### Testing Summary
| Metric | Value |
|--------|-------|
| Total API Requests | 84 |
| Total Input Tokens | 85,180 |
| Total Output Tokens | 580,385 |
| **Total Cost** | **₹132.86** |
| Avg Output per Request | ~6,909 tokens |

---

## 💰 Cost Projections (50 Users)

### Scenario 1: Current App Features Only

**Features included:**
- Assessment Report (1 per user)
- 16 Stage Quizzes
- Quiz Retakes (10 average)
- AI Chat (20 messages)

| Usage Level | Monthly Cost | Per User |
|-------------|--------------|----------|
| Light | ₹1,500 | ₹30 |
| Moderate | ₹2,500 | ₹50 |
| Heavy | ₹3,500 | ₹70 |

---

### Scenario 2: Full AI Features (Future)

**Additional features planned:**
- Dynamic personalized stages
- AI-generated learning content per stage
- Flashcard generation
- Enhanced quizzes with explanations

#### Token Estimates per User (Heavy Usage)

| Feature | Output Tokens | Input Tokens |
|---------|---------------|--------------|
| Assessment Report | 50,000 | 5,000 |
| Personalized Roadmap | 30,000 | 3,000 |
| 16 Stage Content | 160,000 | 16,000 |
| 16 Flashcard Sets | 80,000 | 8,000 |
| 16 Quizzes | 80,000 | 16,000 |
| Quiz Retakes (20x) | 100,000 | 20,000 |
| AI Chat (50 msgs/day × 30 days) | 1,200,000 | 450,000 |
| **TOTAL/USER** | **1,700,000** | **518,000** |

#### Monthly Cost Projection

```
Per User:
- Output: 1.7M × ₹225/1M = ₹382.50
- Input:  0.52M × ₹27/1M = ₹14.04
- Total: ₹396.54/user/month

50 Users:
- Base: ₹19,827/month
- With 30% buffer: ~₹25,800/month
```

| Usage Level | 50 Users/Month |
|-------------|----------------|
| Light | ₹10,000 |
| Moderate | ₹17,000 |
| Heavy | ₹25,000 |
| Extreme | ₹35,000 |

---

## 🗓️ Budget Runway Calculator

### With ₹50,000 Budget

| Scenario | Monthly Cost | Days | Months |
|----------|--------------|------|--------|
| Current app (basic) | ₹4,000 | 375 | ~12 |
| + Dynamic stages | ₹9,000 | 166 | ~5.5 |
| + Learning content | ₹15,000 | 100 | ~3.3 |
| **Full features (heavy)** | **₹25,000** | **60** | **~2** |
| Extreme usage | ₹35,000 | 43 | ~1.5 |

---

## 🔧 Cost Optimization Strategies

### 1. Content Caching (High Impact)
**Savings: 40-60%**

```kotlin
// Cache generated content in Firestore
fun cacheStageContent(stageId: String, content: String) {
    firestore.collection("cached_content")
        .document(stageId)
        .set(mapOf("content" to content, "timestamp" to System.currentTimeMillis()))
}

// Check cache before API call
suspend fun getStageContent(stageId: String): String {
    val cached = firestore.collection("cached_content").document(stageId).get().await()
    if (cached.exists()) return cached.getString("content")!!
    
    // Generate and cache if not exists
    val content = geminiService.generateContent(stageId)
    cacheStageContent(stageId, content)
    return content
}
```

**What to cache:**
- Stage learning content (same for all users)
- Flashcard sets (same topics)
- Quiz question pools (randomize selection)

---

### 2. Rate Limiting (Medium Impact)
**Savings: 20-30%**

```kotlin
object RateLimiter {
    private const val MAX_CHAT_PER_DAY = 30
    private const val MAX_QUIZ_RETAKES = 5
    
    fun canSendChat(userId: String): Boolean {
        val today = getTodayKey()
        val count = prefs.getInt("chat_count_$userId_$today", 0)
        return count < MAX_CHAT_PER_DAY
    }
    
    fun incrementChatCount(userId: String) {
        val today = getTodayKey()
        val count = prefs.getInt("chat_count_$userId_$today", 0)
        prefs.edit().putInt("chat_count_$userId_$today", count + 1).apply()
    }
}
```

**Recommended limits:**
| Feature | Daily Limit | Monthly Cap |
|---------|-------------|-------------|
| Chat messages | 30 | 500 |
| Quiz retakes | 5 per stage | 50 total |
| Report regeneration | 1 | 3 |

---

### 3. Output Token Limits (Medium Impact)
**Savings: 15-25%**

```kotlin
// Set max_output_tokens in API calls
val generationConfig = GenerationConfig.Builder()
    .setMaxOutputTokens(2048)  // Limit response length
    .setTemperature(0.7f)
    .build()
```

**Recommended limits:**
| Feature | Max Output Tokens |
|---------|-------------------|
| Quiz question | 500 |
| Chat response | 800 |
| Flashcard set | 2,000 |
| Stage content | 4,000 |
| Full report | 10,000 |

---

### 4. Use Gemini Flash-Lite for Chat
**Savings: 80% on chat costs**

Gemini 2.5 Flash-Lite pricing:
- Input: $0.10/1M (vs $0.30)
- Output: $0.40/1M (vs $2.50) ← **6x cheaper!**

```kotlin
// Use different models for different features
object GeminiModels {
    const val REPORT = "gemini-2.5-flash"      // High quality needed
    const val QUIZ = "gemini-2.5-flash"        // Accuracy important
    const val CHAT = "gemini-2.5-flash-lite"   // Cost optimization
    const val FLASHCARD = "gemini-2.5-flash-lite"
}
```

---

### 5. Batch Processing
**Savings: 10-15%**

Generate content in batches during off-peak hours:
```kotlin
// Pre-generate quiz pools during app initialization
suspend fun preGenerateQuizPool() {
    for (stageId in 1..16) {
        if (!hasQuizPool(stageId)) {
            val questions = geminiService.generateQuizPool(stageId, count = 20)
            cacheQuizPool(stageId, questions)
        }
    }
}
```

---

## 📊 Optimization Impact Summary

| Strategy | Effort | Savings | New Monthly Cost |
|----------|--------|---------|------------------|
| No optimization | - | 0% | ₹25,000 |
| Caching only | Low | 40% | ₹15,000 |
| + Rate limits | Medium | 60% | ₹10,000 |
| + Output limits | Low | 70% | ₹7,500 |
| + Flash-Lite for chat | Medium | 80% | ₹5,000 |
| **All optimizations** | High | **80%** | **₹5,000** |

### Extended Budget Runway (₹50,000)

| Optimization Level | Monthly Cost | Days |
|--------------------|--------------|------|
| None | ₹25,000 | 60 |
| Basic caching | ₹15,000 | 100 |
| Full optimization | ₹5,000 | **300** |

---

## 🎯 Recommended Implementation Priority

### Phase 1: Quick Wins (Week 1)
- [ ] Implement content caching for stage content
- [ ] Set max_output_tokens on all API calls
- [ ] Add daily rate limits for chat

### Phase 2: Medium Effort (Week 2-3)
- [ ] Switch chat to Gemini Flash-Lite
- [ ] Implement quiz question pooling
- [ ] Add flashcard caching

### Phase 3: Advanced (Week 4+)
- [ ] Batch content pre-generation
- [ ] Usage analytics dashboard
- [ ] Smart rate limiting based on user patterns

---

## 📞 Monitoring & Alerts

Set up Google Cloud billing alerts:
1. Go to **Billing → Budgets & alerts**
2. Create budget: ₹50,000
3. Set alerts at: 25%, 50%, 75%, 90%
4. Enable email notifications

---

## 📚 References

- [Gemini API Pricing](https://ai.google.dev/pricing)
- [Google Cloud Billing](https://console.cloud.google.com/billing)
- [Token Counter Tool](https://ai.google.dev/gemini-api/docs/tokens)
