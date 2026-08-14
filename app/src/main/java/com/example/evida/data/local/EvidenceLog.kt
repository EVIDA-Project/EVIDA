package com.example.evida.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_logs")
data class EvidenceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val hashId: String,
    val status: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val deviceModel: String? = null,
    val manufacturer: String? = null,
    val osVersion: String? = null,
    val foregroundApp: String? = null,
    val installerPackage: String? = null,
    val digitalSignature: String? = null,
    val encryptionIv: ByteArray? = null,
    val appSignature: String? = null,
    val isTimeAutomatic: Boolean = true,
    val isVpnActive: Boolean = false,
    val isAdbEnabled: Boolean = false,
    val isRooted: Boolean = false,
    val isMockLocation: Boolean = false,
    val elapsedRealtime: Long = 0,
    val ntpTimestamp: Long? = null,
    val wrappedKey: ByteArray? = null,
    val localWrappedKey: ByteArray? = null,
    val localWrappedKeyIv: ByteArray? = null,
    
    // V3.0 DEEP METADATA
    val batteryLevel: Int = -1,
    val networkType: String? = null, // WiFi, Cellular, etc.
    val ssid: String? = null,
    val isDeveloperOptionsEnabled: Boolean = false,
    val appSourceStatus: String = "Unknown" // "Trusted Store", "System App", "Sideloaded (Warning)"
)
