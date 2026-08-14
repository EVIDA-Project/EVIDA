package com.example.evida

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

@Suppress("DEPRECATION")
class SecurityManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "evida_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun setPasscode(pin: String) {
        sharedPreferences.edit { putString("app_passcode", pin) }
    }

    fun getPasscode(): String? {
        return sharedPreferences.getString("app_passcode", null)
    }

    fun isPasscodeSet(): Boolean {
        return getPasscode() != null
    }

    fun verifyPasscode(input: String): Boolean {
        return getPasscode() == input
    }

    fun setLastLockTime(time: Long) {
        sharedPreferences.edit { putLong("last_lock_time", time) }
    }

    fun getLastLockTime(): Long {
        return sharedPreferences.getLong("last_lock_time", 0L)
    }

    fun isOnboardingComplete(): Boolean {
        return sharedPreferences.getBoolean("onboarding_complete", false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        sharedPreferences.edit { putBoolean("onboarding_complete", complete) }
    }

    /**
     * Environment Integrity Checks
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }

    fun isAdbEnabled(context: Context): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.ADB_ENABLED, 0
        ) != 0
    }
}
