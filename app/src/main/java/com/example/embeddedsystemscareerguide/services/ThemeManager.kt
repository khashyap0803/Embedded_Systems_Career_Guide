package com.example.embeddedsystemscareerguide.services

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.embeddedsystemscareerguide.PrefsKeys
import com.example.embeddedsystemscareerguide.R

/**
 * Owns the app's appearance setting.
 *
 * Kept as an enum keyed by a stable string rather than by ordinal so new modes
 * can be appended without invalidating what users already have stored, and so
 * a mode that is later removed degrades to [SYSTEM] instead of crashing.
 */
enum class ThemeMode(
    val key: String,
    val labelRes: Int,
    val nightMode: Int,
    /**
     * Whether this mode is offered in the UI yet.
     *
     * LIGHT is deliberately withheld. The theme layer itself is ready - the
     * semantic colour names flip correctly between values/ and values-night/ -
     * but roughly 185 references across the layouts still name absolute dark
     * swatches (slate_800 cards, white label text) instead of those semantic
     * names. Enabling LIGHT today produces a half-converted screen: migrated
     * cards turn white while their neighbours stay dark, and white text lands
     * on white surfaces. Flip this to true once those references are migrated;
     * nothing else here needs to change.
     */
    val selectable: Boolean = true
) {
    SYSTEM("system", R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO, selectable = false),
    DARK("dark", R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM

        /** Modes offered in Settings, in display order. */
        fun selectable(): List<ThemeMode> = entries.filter { it.selectable }
    }
}

object ThemeManager {

    private const val KEY_THEME_MODE = "theme_mode"

    /**
     * Default is DARK rather than SYSTEM: every screen in this app is authored
     * against the dark palette, so following a light system setting would show
     * a half-converted UI to users who never asked to change anything. Users
     * who do want light can pick it explicitly.
     */
    private val DEFAULT_MODE = ThemeMode.DARK

    fun getMode(context: Context): ThemeMode {
        val prefs = context.applicationContext
            .getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_THEME_MODE)) {
            ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))
        } else {
            DEFAULT_MODE
        }
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.applicationContext
            .getSharedPreferences(PrefsKeys.PREFS_USER, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.key)
            .apply()
        apply(mode)
    }

    /**
     * Applies the stored mode. Call once as early as possible - from
     * Application.onCreate - so the first Activity inflates with the right
     * configuration instead of visibly re-theming after it is already on screen.
     */
    fun applyStoredMode(context: Context) = apply(getMode(context))

    private fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}
