package com.example.embeddedsystemscareerguide.services

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R

/**
 * A user-selectable theme.
 *
 * Each entry names a full theme style from `themes_palettes.xml`, which
 * supplies the semantic `app*` attributes the layouts resolve against. Adding
 * a theme is a style plus one entry here.
 *
 * Keyed by a stable string rather than by ordinal so reordering or removing an
 * entry cannot silently repoint someone's saved choice at a different theme;
 * an unknown key falls back to [MIDNIGHT].
 *
 * [isLight] only drives the night-mode flag, which keeps framework widgets
 * that are not styled by us (date pickers, autofill popups) on the right side
 * of light/dark. The palette itself comes entirely from [styleRes].
 */
enum class AppTheme(
    val key: String,
    val labelRes: Int,
    val styleRes: Int,
    val isLight: Boolean = false
) {
    MIDNIGHT("midnight", R.string.theme_midnight, R.style.Theme_App_Midnight),
    DAYLIGHT("daylight", R.string.theme_daylight, R.style.Theme_App_Daylight, isLight = true),
    NEON("neon", R.string.theme_neon, R.style.Theme_App_Neon),
    OCEAN("ocean", R.string.theme_ocean, R.style.Theme_App_Ocean),
    FOREST("forest", R.string.theme_forest, R.style.Theme_App_Forest),
    SUNSET("sunset", R.string.theme_sunset, R.style.Theme_App_Sunset),
    NORD("nord", R.string.theme_nord, R.style.Theme_App_Nord);

    companion object {
        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.key == key } ?: MIDNIGHT
    }
}

object ThemeManager {

    private const val KEY_THEME = "app_theme"

    /** Matches how the app was designed before themes existed. */
    private val DEFAULT = AppTheme.MIDNIGHT

    fun getTheme(context: Context): AppTheme {
        val prefs = context.applicationContext
            .getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        return AppTheme.fromKey(prefs.getString(KEY_THEME, DEFAULT.key))
    }

    /**
     * Persists [theme] and repaints the app.
     *
     * Activities are recreated rather than re-themed in place because a
     * resolved `?attr` colour is baked into each View when it inflates -
     * changing the theme afterwards does not re-resolve them.
     */
    fun setTheme(activity: Activity, theme: AppTheme) {
        if (theme == getTheme(activity)) return
        activity.applicationContext
            .getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.key)
            .apply()
        applyNightMode(theme)
        activity.recreate()
    }

    /** Applies the stored theme to [activity]; call before `setContentView`. */
    fun applyTo(activity: Activity) {
        activity.setTheme(getTheme(activity).styleRes)
    }

    private fun applyNightMode(theme: AppTheme) {
        AppCompatDelegate.setDefaultNightMode(
            if (theme.isLight) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    /**
     * Applies the stored theme to every Activity automatically.
     *
     * Registered from Application.onCreate. `onActivityPreCreated` runs before
     * the Activity's own onCreate, which is the last point a theme can be set
     * and still affect inflation - so no Activity needs to remember to do it,
     * and a newly added screen is themed by default rather than by convention.
     */
    fun install(app: Application) {
        applyNightMode(getTheme(app))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyTo(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
