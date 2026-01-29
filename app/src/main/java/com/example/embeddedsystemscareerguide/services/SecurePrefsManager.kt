package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure SharedPreferences Manager
 * Uses EncryptedSharedPreferences for storing sensitive user data
 * Provides fallback to regular SharedPreferences if encryption fails
 */
object SecurePrefsManager {

    private const val TAG = "SecurePrefsManager"
    private const val SECURE_PREFS_NAME = "secure_user_prefs"
    
    @Volatile
    private var securePrefs: SharedPreferences? = null
    
    /**
     * Get encrypted SharedPreferences instance
     * Falls back to regular SharedPreferences if encryption fails
     */
    fun getSecurePrefs(context: Context): SharedPreferences {
        return securePrefs ?: synchronized(this) {
            securePrefs ?: createSecurePrefs(context).also { securePrefs = it }
        }
    }
    
    private fun createSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to regular", e)
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    // Convenience methods for common operations
    
    fun saveUsername(context: Context, username: String) {
        getSecurePrefs(context).edit().putString(Keys.CURRENT_USERNAME, username).apply()
    }
    
    fun getUsername(context: Context): String? {
        return getSecurePrefs(context).getString(Keys.CURRENT_USERNAME, null)
    }
    
    fun saveUserId(context: Context, userId: String) {
        getSecurePrefs(context).edit().putString(Keys.USER_ID, userId).apply()
    }
    
    fun getUserId(context: Context): String? {
        return getSecurePrefs(context).getString(Keys.USER_ID, null)
    }
    
    fun clearAll(context: Context) {
        getSecurePrefs(context).edit().clear().apply()
    }
    
    /**
     * Migrate from old unencrypted prefs to encrypted prefs
     */
    fun migrateFromLegacyPrefs(context: Context) {
        val legacyPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val legacyUsername = legacyPrefs.getString("current_username", null)
        
        if (legacyUsername != null) {
            saveUsername(context, legacyUsername)
            // Clear legacy data after migration
            legacyPrefs.edit().remove("current_username").apply()
            Log.d(TAG, "Migrated username to encrypted storage")
        }
    }
    
    /**
     * Keys for secure preferences
     */
    object Keys {
        const val CURRENT_USERNAME = "secure_current_username"
        const val USER_ID = "secure_user_id"
        const val USER_EMAIL = "secure_user_email"
    }
}
