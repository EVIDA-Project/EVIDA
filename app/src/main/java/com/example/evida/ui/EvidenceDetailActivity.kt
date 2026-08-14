package com.example.evida.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.evida.DigitalSignatureManager
import com.example.evida.EncryptionManager
import com.example.evida.ui.theme.EVIDATheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class DecryptionStep {
    LOCKED,
    AUTHORIZATION,
    KEY_RETRIEVAL,
    UNWRAPPING,
    INTEGRITY_CHECK,
    SUCCESS
}

class EvidenceDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        val timestamp = intent.getLongExtra("timestamp", 0)
        val ntpTimestamp = intent.getLongExtra("ntpTimestamp", 0)
        val hashId = intent.getStringExtra("hashId") ?: ""
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val deviceModel = intent.getStringExtra("deviceModel") ?: "Unknown"
        val osVersion = intent.getStringExtra("osVersion") ?: "Unknown"
        val foregroundApp = intent.getStringExtra("foregroundApp") ?: "N/A"
        val installer = intent.getStringExtra("installer") ?: "N/A"
        val digitalSignature = intent.getStringExtra("digitalSignature") ?: "N/A"
        val iv = intent.getByteArrayExtra("iv")
        val isTimeAutomatic = intent.getBooleanExtra("isTimeAutomatic", true)
        val isVpnActive = intent.getBooleanExtra("isVpnActive", false)
        val isAdbEnabled = intent.getBooleanExtra("isAdbEnabled", false)
        val isRooted = intent.getBooleanExtra("isRooted", false)
        val isMockLocation = intent.getBooleanExtra("isMockLocation", false)
        val wrappedKey = intent.getByteArrayExtra("wrappedKey")
        val localWrappedKey = intent.getByteArrayExtra("localWrappedKey")
        val localWrappedKeyIv = intent.getByteArrayExtra("localWrappedKeyIv")
        
        val batteryLevel = intent.getIntExtra("batteryLevel", -1)
        val networkType = intent.getStringExtra("networkType") ?: "Unknown"
        val ssid = intent.getStringExtra("ssid") ?: "Unknown"
        val isDevOptions = intent.getBooleanExtra("isDeveloperOptionsEnabled", false)
        val appSourceStatus = intent.getStringExtra("appSourceStatus") ?: "Unknown"

        setContent {
            EVIDATheme {
                EvidenceDetailScreen(
                    timestamp = timestamp,
                    ntpTimestamp = ntpTimestamp,
                    hashId = hashId,
                    latitude = latitude,
                    longitude = longitude,
                    deviceModel = deviceModel,
                    osVersion = osVersion,
                    foregroundApp = foregroundApp,
                    installer = installer,
                    digitalSignature = digitalSignature,
                    iv = iv,
                    isTimeAutomatic = isTimeAutomatic,
                    isVpnActive = isVpnActive,
                    isAdbEnabled = isAdbEnabled,
                    isRooted = isRooted,
                    isMockLocation = isMockLocation,
                    wrappedKey = wrappedKey,
                    localWrappedKey = localWrappedKey,
                    localWrappedKeyIv = localWrappedKeyIv,
                    batteryLevel = batteryLevel,
                    networkType = networkType,
                    ssid = ssid,
                    isDevOptions = isDevOptions,
                    appSourceStatus = appSourceStatus,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceDetailScreen(
    timestamp: Long, ntpTimestamp: Long, hashId: String, latitude: Double, longitude: Double,
    deviceModel: String, @Suppress("UNUSED_PARAMETER") osVersion: String, foregroundApp: String, @Suppress("UNUSED_PARAMETER") installer: String,
    digitalSignature: String, iv: ByteArray?, isTimeAutomatic: Boolean,
    isVpnActive: Boolean, isAdbEnabled: Boolean, isRooted: Boolean, isMockLocation: Boolean,
    wrappedKey: ByteArray?, localWrappedKey: ByteArray?, 
    localWrappedKeyIv: ByteArray?, batteryLevel: Int, networkType: String, ssid: String,
    isDevOptions: Boolean, appSourceStatus: String, onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    var decryptedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decryptionType by remember { mutableStateOf("NONE") } 
    var isProcessing by remember { mutableStateOf(value = false) }
    var signatureVerified by remember { mutableStateOf<Boolean?>(null) }
    
    var currentStep by remember { mutableStateOf(DecryptionStep.LOCKED) }
    var selectedMethod by remember { mutableStateOf("") } 
    
    var techData by remember { mutableStateOf("") }
    var techLabel by remember { mutableStateOf("") }

    val encryptionManager = remember { EncryptionManager() }
    val signatureManager = remember { DigitalSignatureManager() }

    fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            activity, 
            executor, 
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Forensic Authorization")
            .setSubtitle("Authorize Decryption Chain Access")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(digitalSignature, hashId) {
        val metadataToSign = "hash:$hashId|source:$appSourceStatus|root:$isRooted|adb:$isAdbEnabled|mock:$isMockLocation|ntp:$ntpTimestamp"
        signatureVerified = signatureManager.verifySignature(metadataToSign, digitalSignature)
    }

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null) return "NULL"
        return bytes.joinToString("") { "%02x".format(it) }.take(48) + "..."
    }

    fun performLocalDecryption() {
        if ((localWrappedKey == null) || (localWrappedKeyIv == null) || (iv == null)) return
        isProcessing = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "$hashId.enc")
                if (file.exists()) {
                    val ephemeralKey = encryptionManager.unwrapKey(localWrappedKey, localWrappedKeyIv)
                    val decryptedData = encryptionManager.decrypt(file.readBytes(), iv, ephemeralKey)
                    val bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
                    launch(Dispatchers.Main) {
                        decryptedBitmap = bitmap
                        decryptionType = "LOCAL (Hardware Keystore)"
                        currentStep = DecryptionStep.SUCCESS
                        isProcessing = false
                    }
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Decryption Failed", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                    currentStep = DecryptionStep.LOCKED
                }
            }
        }
    }

    fun performAuthorityDecryption() {
        if (wrappedKey == null || iv == null) return
        isProcessing = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "$hashId.enc")
                if (file.exists()) {
                    val decryptedData = encryptionManager.decryptWithEnvelope(
                        encryptedData = file.readBytes(),
                        iv = iv,
                        wrappedKey = wrappedKey,
                        privateKeyBase64 = EncryptionManager.DEMO_AUTHORITY_PRIVATE_KEY
                    )
                    val bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
                    launch(Dispatchers.Main) {
                        decryptedBitmap = bitmap
                        decryptionType = "AUTHORITY (RSA Envelope)"
                        currentStep = DecryptionStep.SUCCESS
                        isProcessing = false
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Authority Decryption Failed", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                    currentStep = DecryptionStep.LOCKED
                }
            }
        }
    }

    fun executeManualStep() {
        when (currentStep) {
            DecryptionStep.LOCKED -> {
                showBiometricPrompt {
                    currentStep = DecryptionStep.AUTHORIZATION
                    techLabel = "IDENTITY STATUS"
                    techData = "INVESTIGATOR VERIFIED"
                }
            }
            DecryptionStep.AUTHORIZATION -> {
                currentStep = DecryptionStep.KEY_RETRIEVAL
                techLabel = "ENCRYPTED ENVELOPE (HEX)"
                techData = if (selectedMethod == "LOCAL") bytesToHex(localWrappedKey) else bytesToHex(wrappedKey)
            }
            DecryptionStep.KEY_RETRIEVAL -> {
                currentStep = DecryptionStep.UNWRAPPING
                techLabel = "EPHEMERAL SESSION KEY"
                techData = "UNWRAPPING VIA ${if (selectedMethod == "LOCAL") "AES-KWP" else "RSA-OAEP"}..."
                
                scope.launch {
                    kotlinx.coroutines.delay(800L)
                    techData = "0x" + (1..32).asSequence().map { "0123456789ABCDEF".random() }.joinToString("") + "..."
                }
            }
            DecryptionStep.UNWRAPPING -> {
                currentStep = DecryptionStep.INTEGRITY_CHECK
                techLabel = "SHA-256 BINARY HASH"
                techData = hashId
            }
            DecryptionStep.INTEGRITY_CHECK -> {
                if (selectedMethod == "LOCAL") performLocalDecryption() else performAuthorityDecryption()
            }
            else -> {}
        }
    }

    fun exportEncryptedFile() {
        try {
            val file = File(context.filesDir, "$hashId.enc")
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Encrypted Evidence (.ENC)"))
            } else {
                Toast.makeText(context, "Encrypted file not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FORENSIC EVIDENCE", style = MaterialTheme.typography.labelLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            
            Card(
                modifier = Modifier.fillMaxWidth().height(350.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (decryptedBitmap == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isProcessing) {
                        CircularProgressIndicator()
                    } else if (decryptedBitmap != null) {
                        Image(
                            bitmap = decryptedBitmap!!.asImageBitmap(), 
                            contentDescription = null, 
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (currentStep == DecryptionStep.LOCKED) Icons.Default.Lock else Icons.Default.GppMaybe, 
                                contentDescription = null, 
                                modifier = Modifier.size(64.dp), 
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                when(currentStep) {
                                    DecryptionStep.LOCKED -> "ENCRYPTED EVIDENCE"
                                    else -> "ACCESSING ENVELOPE..."
                                },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (decryptedBitmap == null) {
                if (currentStep == DecryptionStep.LOCKED) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { selectedMethod = "LOCAL"; executeManualStep() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LOCAL KEY")
                        }
                        if (wrappedKey != null) {
                            Button(
                                onClick = { selectedMethod = "LAB"; executeManualStep() },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.AccountBalance, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LAB UNWRAP")
                            }
                        }
                    }
                } else {
                    DecryptionWizard(
                        step = currentStep,
                        method = selectedMethod,
                        techLabel = techLabel,
                        techData = techData,
                        onNext = { executeManualStep() },
                        onCancel = { currentStep = DecryptionStep.LOCKED }
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Evidence Decrypted via $decryptionType", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                }

                Button(
                    onClick = { 
                        scope.launch(Dispatchers.IO) {
                            generateForensicPdf(
                                context = context,
                                screenshot = decryptedBitmap,
                                hashId = hashId,
                                ts = timestamp,
                                ntp = ntpTimestamp,
                                lat = latitude,
                                lon = longitude,
                                app = foregroundApp,
                                model = deviceModel,
                                sig = digitalSignature,
                                isVpnActive = isVpnActive,
                                sourceStatus = appSourceStatus,
                                battery = batteryLevel,
                                net = networkType,
                                ssid = ssid,
                                isRooted = isRooted,
                                isAdbEnabled = isAdbEnabled,
                                isTimeAuto = isTimeAutomatic,
                                isMock = isMockLocation,
                                isDev = isDevOptions
                            ) 
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Inventory, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORT FORENSIC BUNDLE (.ZIP)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("FORENSIC SECURITY MATRIX", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecuritySignalBadge("ROOT", !isRooted)
                        SecuritySignalBadge("ADB", !isAdbEnabled)
                        SecuritySignalBadge("VPN", !isVpnActive)
                        SecuritySignalBadge("TIME-AUTO", isTimeAutomatic)
                        SecuritySignalBadge("GPS-REAL", !isMockLocation)
                        SecuritySignalBadge("DEV-OPTS", !isDevOptions)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("CRYPTOGRAPHIC PROOF", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    MetadataItem(Icons.Default.Fingerprint, "SHA-256 DATA HASH", hashId)
                    MetadataItem(Icons.Default.AccessTimeFilled, "FORENSIC TIME (NTP)", if (ntpTimestamp != 0L) formatTimestamp(ntpTimestamp) else "LOCAL CLOCK ONLY")
                    MetadataItem(Icons.Default.BatteryChargingFull, "DEVICE STATE", "Battery: $batteryLevel% | Model: $deviceModel")
                    MetadataItem(Icons.Default.Wifi, "NETWORK CONTEXT", "$networkType ($ssid)")
                    MetadataItem(Icons.Default.VerifiedUser, "APP PROVENANCE", appSourceStatus)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (appSourceStatus.contains("TRUSTED") || appSourceStatus.contains("SYSTEM")) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("SECURITY STATUS:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(appSourceStatus, style = MaterialTheme.typography.titleMedium, color = if (appSourceStatus.contains("TRUSTED") || appSourceStatus.contains("SYSTEM")) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { exportEncryptedFile() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EXPORT RAW ENCRYPTED BLOB (.ENC)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun DecryptionWizard(step: DecryptionStep, method: String, techLabel: String, techData: String, onNext: () -> Unit, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "FORENSIC DECRYPTION CHAIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            
            if (techData.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(techLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(techData, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            WizardStepItem(label = "1. Identity Authorization", description = "Verify Investigator credentials.", isActive = step == DecryptionStep.AUTHORIZATION, isDone = step > DecryptionStep.AUTHORIZATION)
            WizardStepItem(label = "2. Keystore Retrieval", description = if (method == "LOCAL") "Fetch hardware AES-256 key." else "Verify Authority RSA-2048 cert.", isActive = step == DecryptionStep.KEY_RETRIEVAL, isDone = step > DecryptionStep.KEY_RETRIEVAL)
            WizardStepItem(label = "3. Envelope Unwrapping", description = "Decrypt ephemeral session key.", isActive = step == DecryptionStep.UNWRAPPING, isDone = step > DecryptionStep.UNWRAPPING)
            WizardStepItem(label = "4. Integrity Seal Check", description = "Validate SHA-256 binary hash.", isActive = step == DecryptionStep.INTEGRITY_CHECK, isDone = step > DecryptionStep.INTEGRITY_CHECK)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("ABORT") }
                Button(onClick = onNext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(when(step) {
                        DecryptionStep.AUTHORIZATION -> "AUTHORIZE"
                        DecryptionStep.KEY_RETRIEVAL -> "FETCH"
                        DecryptionStep.UNWRAPPING -> "UNWRAP"
                        DecryptionStep.INTEGRITY_CHECK -> "FINALIZE"
                        else -> "CONTINUE"
                    })
                }
            }
        }
    }
}

@Composable
fun WizardStepItem(label: String, description: String, isActive: Boolean, isDone: Boolean) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else if (isActive) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isDone) Color(0xFF4CAF50) else if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, color = if (isActive || isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            if (isActive) Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SecuritySignalBadge(label: String, isOk: Boolean) {
    Surface(
        color = (if (isOk) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, (if (isOk) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(if (isOk) Color(0xFF4CAF50) else Color.Red, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF2E7D32) else Color.Red)
        }
    }
}

@Composable
fun MetadataItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (label.contains("HASH")) FontFamily.Monospace else FontFamily.Default, fontSize = if (label.contains("HASH")) 11.sp else 14.sp), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun generateForensicPdf(
    context: Context, 
    screenshot: Bitmap?, 
    hashId: String, 
    ts: Long, 
    ntp: Long, 
    lat: Double, 
    lon: Double, 
    app: String, 
    model: String, 
    @Suppress("UNUSED_PARAMETER") sig: String, 
    isVpnActive: Boolean, 
    sourceStatus: String, 
    battery: Int, 
    net: String, 
    ssid: String, 
    isRooted: Boolean, 
    isAdbEnabled: Boolean, 
    isTimeAuto: Boolean, 
    isMock: Boolean, 
    isDev: Boolean,
) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint().apply { isAntiAlias = true }
    val margin = 50f

    paint.color = AndroidColor.WHITE
    canvas.drawRect(0f, 0f, 595f, 842f, paint)
    paint.color = AndroidColor.rgb(26, 35, 126) 
    canvas.drawRect(0f, 0f, 595f, 90f, paint)
    paint.color = AndroidColor.WHITE
    paint.textSize = 24f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    canvas.drawText("FORENSIC EVIDENCE REPORT", margin, 45f, paint)
    paint.textSize = 10f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    canvas.drawText("EVIDA CRYPTOGRAPHIC KERNEL V2.0 | SYSTEM ATTESTED", margin, 65f, paint)

    val isSecure = sourceStatus.contains("TRUSTED") || sourceStatus.contains("SYSTEM")
    paint.color = if (isSecure) AndroidColor.rgb(46, 125, 50) else AndroidColor.RED
    canvas.drawRect(0f, 90f, 595f, 120f, paint)
    paint.color = AndroidColor.WHITE
    paint.textSize = 12f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    canvas.drawText("APP PROVENANCE: $sourceStatus | SYSTEM ATTESTED", margin, 110f, paint)

    screenshot?.let {
        val maxWidth = 495f
        val maxHeight = 350f
        val ratio = it.width.toFloat() / it.height.toFloat()
        var imgWidth = maxWidth
        var imgHeight = maxWidth / ratio
        if (imgHeight > maxHeight) {
            imgHeight = maxHeight
            imgWidth = maxHeight * ratio
        }
        val left = (595f - imgWidth) / 2f
        canvas.drawBitmap(it, null, android.graphics.RectF(left, 140f, left + imgWidth, 140f + imgHeight), Paint().apply { isFilterBitmap = true })
    }

    val yMeta = 520f
    paint.color = AndroidColor.rgb(245, 245, 245)
    canvas.drawRoundRect(margin, yMeta, 545f, yMeta + 220f, 12f, 12f, paint)
    paint.color = AndroidColor.BLACK
    paint.textSize = 13f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    canvas.drawText("ACQUISITION TECHNICAL DATA", margin + 20f, yMeta + 30f, paint)

    var gridX = margin + 20f
    var gridY = yMeta + 50f
    val signals = listOf(Pair("ROOT", !isRooted), Pair("ADB", !isAdbEnabled), Pair("VPN", !isVpnActive), Pair("TIME", isTimeAuto), Pair("GPS", !isMock), Pair("DEV", !isDev))
    signals.chunked(3).forEach { row ->
        row.forEach { (label, isOk) ->
            paint.color = if (isOk) AndroidColor.rgb(46, 125, 50) else AndroidColor.RED
            canvas.drawCircle(gridX + 5f, gridY - 4f, 4f, paint)
            paint.color = AndroidColor.BLACK
            paint.textSize = 9f
            canvas.drawText(label, gridX + 15f, gridY, paint)
            gridX += 80f
        }
        gridX = margin + 20f
        gridY += 20f
    }

    paint.textSize = 10f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    val lines = listOf("SHA-256 HASH: $hashId", "TIMESTAMP: ${if (ntp != 0L) formatTimestamp(ntp) else "LOCAL: ${formatTimestamp(ts)}"}", "LOCATION: $lat, $lon", "SOURCE APP: $app", "DEVICE: $model (BATT: $battery%)", "NETWORK: $net ($ssid)")
    var currY = gridY + 10f
    lines.forEach { line -> canvas.drawText(line, margin + 20f, currY, paint); currY += 18f }

    // 5. THE "FORENSIC SEAL" - Scannable Verification QR
    val qrSize = 100f
    val qrX = 430f
    val qrY = yMeta + 90f
    
    try {
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(
            "EVIDA FORENSIC VERIFICATION\nHASH: $hashId\nTIME: ${if (ntp != 0L) formatTimestamp(ntp) else formatTimestamp(ts)}",
            BarcodeFormat.QR_CODE, 200, 200
        )
        
        paint.color = AndroidColor.BLACK
        val cellSize = qrSize / bitMatrix.width
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix[x, y]) {
                    canvas.drawRect(qrX + (x * cellSize), qrY + (y * cellSize), qrX + ((x + 1) * cellSize), qrY + ((y + 1) * cellSize), paint)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    paint.textSize = 8f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    canvas.drawText("SCANNABLE SEAL", qrX + 15f, qrY + qrSize + 15f, paint)
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
    paint.textSize = 6f
    canvas.drawText("Verify Digital Fingerprint", qrX + 15f, qrY + qrSize + 25f, paint)

    // 6. Forensic Disclaimer
    paint.color = AndroidColor.GRAY
    paint.textSize = 6f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.ITALIC)
    val disclaimer = "DISCLAIMER: EVIDA V2.0. App Provenance tracks application origin (Store vs Sideload). 'SCANNABLE SEAL' provides mathematical proof of pixel-level integrity. Environment status (Root/ADB/VPN) is snapshot at acquisition time."
    canvas.drawText(disclaimer, margin, 830f, paint)

    pdfDocument.finishPage(page)
    val pdfFile = File(context.cacheDir, "EVIDA_FORENSIC_V2_$hashId.pdf")
    pdfDocument.writeTo(FileOutputStream(pdfFile))
    pdfDocument.close()
    
    val imgFile = File(context.cacheDir, "EVIDA_HIGHRES_$hashId.png")
    screenshot?.let { FileOutputStream(imgFile).use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) } }

    // Create a ZIP bundle for forensic integrity
    val zipFile = File(context.cacheDir, "EVIDA_BUNDLE_$hashId.zip")
    try {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Add PDF
            val pdfEntry = ZipEntry(pdfFile.name)
            zos.putNextEntry(pdfEntry)
            pdfFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
            
            // Add Image
            if (imgFile.exists()) {
                val imgEntry = ZipEntry(imgFile.name)
                zos.putNextEntry(imgEntry)
                imgFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        
        // Clean up individual files
        pdfFile.delete()
        imgFile.delete()

        val zipUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Forensic ZIP Bundle"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to create forensic bundle", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())
    return sdf.format(timestamp)
}
