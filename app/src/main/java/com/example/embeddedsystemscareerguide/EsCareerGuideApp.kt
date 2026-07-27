package com.example.embeddedsystemscareerguide

import android.app.Application
import com.example.embeddedsystemscareerguide.services.ThemeManager

/**
 * Applies the stored appearance before any Activity is created.
 *
 * Doing this in an Activity's onCreate instead would inflate the first screen
 * with the previous configuration and then visibly re-theme it, and would also
 * re-apply on every Activity start.
 */
class EsCareerGuideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStoredMode(this)
    }
}
