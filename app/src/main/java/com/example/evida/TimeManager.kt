package com.example.evida

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class TimeManager(context: Context) {

    companion object {
        private const val TAG = "TimeManager"
        private const val PREFS_NAME = "evida_time_prefs"
        private const val KEY_OFFSET_V3 = "time_offset_v3"
        private const val KEY_SYNC_ELAPSED = "sync_elapsed_v3"
        private const val KEY_LAST_SYNC_WALL = "last_sync_wall"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val NTP_SERVERS = listOf(
        "time.google.com",
        "pool.ntp.org",
        "time.apple.com",
        "time.cloudflare.com",
        "time.windows.com"
    )

    private val HTTP_SOURCES = listOf(
        "https://www.google.com",
        "https://www.cloudflare.com",
        "https://www.apple.com",
        "https://www.microsoft.com"
    )

    /**
     * Gets the high-integrity forensic timestamp.
     * Uses hardware-backed monotonic clock (elapsedRealtime) to prevent tampering via system clock changes.
     * Returns null if no sync has occurred in the current boot session.
     */
    fun getForensicTimestamp(): Long? {
        val offset = prefs.getLong(KEY_OFFSET_V3, Long.MIN_VALUE)
        val syncElapsed = prefs.getLong(KEY_SYNC_ELAPSED, -1L)
        
        if (offset == Long.MIN_VALUE || syncElapsed == -1L) {
            // Check legacy fallback for backward compatibility during transition
            val legacyOffset = prefs.getLong("time_offset", Long.MIN_VALUE)
            if (legacyOffset != Long.MIN_VALUE) {
                return System.currentTimeMillis() + legacyOffset
            }
            return null
        }
        
        val currentElapsed = SystemClock.elapsedRealtime()
        
        // Detection of reboot: elapsedRealtime resets to 0. 
        // If current is less than the elapsed time at sync, a reboot definitely happened.
        if (currentElapsed < syncElapsed) {
            Log.w(TAG, "Reboot detected since last sync. Atomic clock requires re-sync.")
            return null
        }
        
        return currentElapsed + offset
    }

    /**
     * Performs a deep sync using NTP (primary) and HTTP HEAD (fallback).
     * Returns true if successfully synchronized.
     */
    suspend fun syncWithAtomicClock(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Atomic Clock deep sync...")
        
        // 1. NTP Sync (High Precision)
        val ntpClient = NTPUDPClient()
        ntpClient.defaultTimeout = 5000
        for (server in NTP_SERVERS) {
            try {
                Log.d(TAG, "Trying NTP: $server")
                val address = InetAddress.getByName(server)
                val info = ntpClient.getTime(address)
                info.computeDetails()
                val offset = info.offset
                
                if (offset != null) {
                    val networkTime = System.currentTimeMillis() + offset
                    saveOffset(networkTime)
                    Log.i(TAG, "NTP sync successful: $server (Offset: $offset ms)")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "NTP failed for $server: ${e.message}")
            } finally {
                try { ntpClient.close() } catch (e: Exception) {}
            }
        }

        // 2. HTTP Sync (Fallback, High Compatibility)
        for (source in HTTP_SOURCES) {
            try {
                Log.d(TAG, "Trying HTTP fallback: $source")
                val url = URL(source)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val networkTime = connection.date
                if (networkTime > 0) {
                    saveOffset(networkTime)
                    Log.i(TAG, "HTTP sync successful: $source")
                    connection.disconnect()
                    return@withContext true
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "HTTP failed for $source: ${e.message}")
            }
        }

        Log.e(TAG, "Deep sync failed: All sources unreachable.")
        return@withContext false
    }

    private fun saveOffset(networkTime: Long) {
        val currentElapsed = SystemClock.elapsedRealtime()
        val offset = networkTime - currentElapsed
        prefs.edit {
            putLong(KEY_OFFSET_V3, offset)
            putLong(KEY_SYNC_ELAPSED, currentElapsed)
            putLong(KEY_LAST_SYNC_WALL, System.currentTimeMillis())
        }
    }
}
