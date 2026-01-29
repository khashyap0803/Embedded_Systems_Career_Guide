package com.example.embeddedsystemscareerguide.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * M7 fix: Network connectivity utility
 * Provides methods to check network availability before making API calls
 */
object NetworkUtils {

    /**
     * Check if the device has an active internet connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo?.isConnectedOrConnecting == true
        }
    }
    
    /**
     * Check if WiFi is connected
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
    
    /**
     * Check if mobile data is connected
     */
    fun isMobileDataConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo?.type == ConnectivityManager.TYPE_MOBILE && networkInfo.isConnected
        }
    }
    
    /**
     * Get a human-readable network status string
     */
    fun getNetworkStatus(context: Context): String {
        return when {
            isWifiConnected(context) -> "WiFi"
            isMobileDataConnected(context) -> "Mobile Data"
            isNetworkAvailable(context) -> "Connected"
            else -> "Offline"
        }
    }
}
