package com.example.embeddedsystemscareerguide.services

/**
 * H6 fix: Input Sanitization Utility
 * Sanitizes user input before sending to Gemini API to prevent prompt injection
 */
object InputSanitizer {

    /**
     * Sanitize user input for API prompts
     * - Removes potential prompt injection patterns
     * - Escapes special characters
     * - Limits length
     */
    fun sanitizeForApi(input: String, maxLength: Int = 5000): String {
        if (input.isBlank()) return ""
        
        var sanitized = input
            // Remove potential system prompt manipulation attempts
            .replace(Regex("\\bsystem\\s*:", RegexOption.IGNORE_CASE), "[system]")
            .replace(Regex("\\buser\\s*:", RegexOption.IGNORE_CASE), "[user]")
            .replace(Regex("\\bassistant\\s*:", RegexOption.IGNORE_CASE), "[assistant]")
            .replace(Regex("\\bignore\\s+previous\\s+instructions?", RegexOption.IGNORE_CASE), "[filtered]")
            .replace(Regex("\\bforget\\s+((all|your)\\s+)?instructions?", RegexOption.IGNORE_CASE), "[filtered]")
            .replace(Regex("\\bdisregard\\s+((all|your)\\s+)?instructions?", RegexOption.IGNORE_CASE), "[filtered]")
            // Remove markdown code block manipulation
            .replace("```", "'''")
            // Escape backslashes
            .replace("\\", "\\\\")
            // Trim whitespace
            .trim()
        
        // Limit length
        if (sanitized.length > maxLength) {
            sanitized = sanitized.take(maxLength) + "..."
        }
        
        return sanitized
    }
    
    /**
     * Sanitize content for HTML display (XSS prevention)
     */
    fun sanitizeForHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * Validate and sanitize username input
     */
    fun sanitizeUsername(input: String): String {
        return input
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(20)
    }
}
