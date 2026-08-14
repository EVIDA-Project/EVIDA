package com.example.evida.ui

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import com.example.evida.MainActivity
import com.example.evida.SecurityManager
import com.example.evida.ScreenshotManager
import com.example.evida.FloatingWidgetService
import com.example.evida.ui.theme.EVIDATheme

class OnboardingActivity : ComponentActivity() {
    
    private val securityManager by lazy { SecurityManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EVIDATheme {
                OnboardingScreen {
                    securityManager.setOnboardingComplete(complete = true)
                    MainActivity.isAppUnlocked = true
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(-1) } // -1 for Welcome, 0 for Intro, 1 for Readiness, 2 for PIN
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Readiness states
    var gpsState by remember { mutableStateOf(value = false) }
    var overlayState by remember { mutableStateOf(value = false) }
    var usageState by remember { mutableStateOf(value = false) }
    var captureState by remember { mutableStateOf(value = false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                gpsState = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                overlayState = Settings.canDrawOverlays(context)
                usageState = isUsageStatsAllowed(context)
                // Capture state is handled by the launcher
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        gpsState = result.values.all { it }
    }

    val screenshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(context, FloatingWidgetService::class.java).apply {
                action = "ACTION_SCREENSHOT_SUCCESS"
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            context.startService(serviceIntent)
            captureState = true
        }
    }

    val passcodeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onComplete()
    }

    when (currentStep) {
        -1 -> WelcomeScreen { currentStep = 0 }
        0 -> IntroPager { currentStep = 1 }
        1 -> SystemReadinessScreen(
            gps = gpsState,
            overlay = overlayState,
            usage = usageState,
            capture = captureState,
            onGpsRequest = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onOverlayRequest = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) },
            onUsageRequest = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            onCaptureRequest = { ScreenshotManager(context).startScreenshotCapture(screenshotLauncher) },
            onComplete = { currentStep = 2 },
        )
        2 -> {
            // Set PIN Step
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("SECURE ACCESS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Create your 6-digit forensic pin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { passcodeLauncher.launch(Intent(context, PasscodeActivity::class.java).apply { putExtra("isSetup", true) }) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("SET ACCESS PIN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IntroPager(onNext: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("CRYPTOGRAPHIC SEAL", "ATOMIC TIME", "HARDWARE ATTESTATION")
    val descs = listOf(
        "Every capture is hashed and signed using military-grade AES-256 and ECDSA algorithms.",
        "Internal clocks can be faked. EVIDA syncs with global atomic clocks for legal validity.",
        "Our kernel verifies your device hardware and network state to ensure zero tampering."
    )
    val icons = listOf(Icons.Default.EnhancedEncryption, Icons.Default.AccessTimeFilled, Icons.Default.Verified)

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(targetState = page, label = "intro") { targetPage ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icons[targetPage], null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(32.dp))
                Text(titles[targetPage], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(descs[targetPage], style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(modifier = Modifier.size(8.dp).background(if (page == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = { if (page < 2) page++ else onNext() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (page < 2) "CONTINUE" else "INITIALIZE KERNEL")
        }
    }
}

@Composable
fun SystemReadinessScreen(
    gps: Boolean, overlay: Boolean, usage: Boolean, capture: Boolean,
    onGpsRequest: () -> Unit, onOverlayRequest: () -> Unit, onUsageRequest: () -> Unit, onCaptureRequest: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("SYSTEM READINESS", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("Verify forensic compliance modules.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        
        Spacer(modifier = Modifier.height(48.dp))
        
        ReadinessItem("GPS COORDINATES", "Required for location stamping.", gps, onGpsRequest)
        ReadinessItem("WIDGET OVERLAY", "Required for floating capture ui.", overlay, onOverlayRequest)
        ReadinessItem("USAGE TELEMETRY", "Required for source app validation.", usage, onUsageRequest)
        ReadinessItem("SCREEN CAPTURE", "Required for evidence acquisition.", capture, onCaptureRequest)
        
        Spacer(modifier = Modifier.weight(1f))
        
        val allReady = gps && overlay && usage && capture
        Button(
            onClick = onComplete,
            enabled = allReady,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (allReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text("PROCEED TO SECURITY", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ReadinessItem(label: String, desc: String, isReady: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
        color = Color.Transparent,
        enabled = !isReady
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Icon(
                imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isReady) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isReady) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Decorative background elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A237E).copy(alpha = 0.3f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 1000f
                    )
                )
        )
        
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color(0xFF42A5F5)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "EVIDA KERNEL",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                ),
                color = Color.White
            )
            
            Text(
                text = "VERSION 2.0 SECURED",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 4.sp
                ),
                color = Color(0xFF42A5F5)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Initializing secure forensic environment for digital evidence acquisition...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(80.dp))
            
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("INITIALIZE CORE", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        
        // Technical footer
        Text(
            text = "EVIDA-SYSTEM-AUTH: READY",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50).copy(alpha = 0.5f),
            letterSpacing = 2.sp
        )
    }
}

private fun isUsageStatsAllowed(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
