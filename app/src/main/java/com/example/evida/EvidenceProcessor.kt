package com.example.evida

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.evida.data.LogRepository
import com.example.evida.data.local.EvidenceLog
import java.io.File

class EvidenceProcessor(
    private val context: Context,
    private val hashManager: HashManager,
    private val encryptionManager: EncryptionManager,
    private val logRepository: LogRepository,
) {
    private val tag = "EvidenceProcessor"
    private val signatureManager = DigitalSignatureManager()

    suspend fun processEvidence(
        screenshot: ByteArray,
        latitude: Double? = null,
        longitude: Double? = null,
        foregroundApp: String? = null,
        installer: String? = null,
        appSignature: String? = null,
        isTimeAutomatic: Boolean = true,
        isVpnActive: Boolean = false,
        isAdbEnabled: Boolean = false,
        isRooted: Boolean = false,
        isMockLocation: Boolean = false,
        elapsedRealtime: Long = 0,
        ntpTimestamp: Long? = null,
        batteryLevel: Int = -1,
        networkType: String? = null,
        ssid: String? = null,
        isDeveloperOptionsEnabled: Boolean = false,
    ): Boolean {
        Log.d(tag, "Starting evidence processing for $foregroundApp")
        return try {
            val hash = hashManager.generateSha256(screenshot)
            
            // App Provenance Logic (Defensible Feature)
            val sourceStatus = when (installer) {
                "Google Play Store" -> "TRUSTED STORE"
                "System Image (Factory)" -> "SYSTEM APP"
                else -> "SIDELOADED (UNVERIFIED)"
            }

            Log.d(tag, "Forensic Source Check: $foregroundApp via $sourceStatus")
            
            // ASYMMETRIC KEY WRAPPING (Digital Envelope)
            Log.d(tag, "Encrypting with envelope...")
            val envelopedData = encryptionManager.encryptWithEnvelope(
                screenshot, 
                EncryptionManager.DEMO_AUTHORITY_PUBLIC_KEY,
            )

            val file = File(context.filesDir, "$hash.enc")
            file.writeBytes(envelopedData.encryptedData)

            // Include deep metadata in the signature for legal verification
            val metadataToSign = "hash:$hash|source:$sourceStatus|root:$isRooted|adb:$isAdbEnabled|mock:$isMockLocation|ntp:$ntpTimestamp"
            val digitalSignature = signatureManager.signData(metadataToSign)

            val log = EvidenceLog(
                timestamp = System.currentTimeMillis(),
                ntpTimestamp = ntpTimestamp,
                hashId = hash,
                status = "Secured",
                latitude = latitude,
                longitude = longitude,
                deviceModel = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                foregroundApp = foregroundApp,
                installerPackage = installer,
                digitalSignature = digitalSignature,
                encryptionIv = envelopedData.iv,
                appSignature = appSignature,
                isTimeAutomatic = isTimeAutomatic,
                isVpnActive = isVpnActive,
                isAdbEnabled = isAdbEnabled,
                isRooted = isRooted,
                isMockLocation = isMockLocation,
                elapsedRealtime = elapsedRealtime,
                wrappedKey = envelopedData.authorityWrappedKey,
                localWrappedKey = envelopedData.localWrappedKey,
                localWrappedKeyIv = envelopedData.localWrappedKeyIv,
                batteryLevel = batteryLevel,
                networkType = networkType,
                ssid = ssid,
                isDeveloperOptionsEnabled = isDeveloperOptionsEnabled,
                appSourceStatus = sourceStatus,
            )
            Log.d(tag, "Inserting log into repository...")
            logRepository.insertLog(log)
            Log.d(tag, "Log insertion complete")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error processing evidence", e)
            false
        }
    }
}
