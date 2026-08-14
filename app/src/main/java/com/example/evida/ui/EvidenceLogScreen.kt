package com.example.evida.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.evida.SecurityManager
import com.example.evida.TimeManager
import com.example.evida.ui.PasscodeActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceLogScreen(
    viewModel: EvidenceLogViewModel,
    modifier: Modifier = Modifier,
) {
    val evidenceLogs by viewModel.evidenceLogs.collectAsState()
    val context = LocalContext.current
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredLogs = remember(evidenceLogs, searchQuery) {
        if (searchQuery.isEmpty()) evidenceLogs
        else evidenceLogs.filter { 
            (it.foregroundApp?.contains(searchQuery, ignoreCase = true) == true) || 
            it.hashId.contains(searchQuery, ignoreCase = true) 
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EVIDA CORE", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
                        Text("SECURED FORENSIC KERNEL V2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, null) },
                    label = { Text("Monitor") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("Evidence") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> DashboardTab(evidenceLogs.size, context)
                1 -> EvidenceTab(filteredLogs, searchQuery, onQueryChange = { searchQuery = it }, context)
                2 -> SettingsTab(context)
            }
        }
    }
}

@Composable
fun DashboardTab(logCount: Int, context: Context) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SecurityStatsHeader(count = logCount)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("FORENSIC CORE STATUS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp), letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            IntegrityDashboardContent(context)
        }
    }
}

@Composable
fun IntegrityDashboardContent(context: Context) {
    val timeManager = remember { TimeManager(context) }
    val securityManager = remember { SecurityManager(context) }
    
    Column(modifier = Modifier.padding(20.dp)) {
        val isRooted = securityManager.isDeviceRooted()
        IntegrityItem(label = "Kernel State", status = if (isRooted) "UNSAFE" else "SECURE", isOk = !isRooted) { }
        
        val adbEnabled = securityManager.isAdbEnabled(context)
        IntegrityItem(
            label = "Debug Port", 
            status = if (adbEnabled) "OPEN" else "LOCKED", 
            isOk = !adbEnabled,
        ) { 
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) 
        }
        
        val hasUsage = isUsageAllowed(context)
        IntegrityItem(label = "Audit Logging", status = if (hasUsage) "ACTIVE" else "DISABLED", isOk = hasUsage, onAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        
        val hasNtp = timeManager.getForensicTimestamp() != null
        IntegrityItem(label = "Atomic Clock", status = if (hasNtp) "SYNCED" else "LOCAL", isOk = hasNtp, onAction = { })
    }
}

@Composable
fun EvidenceTab(logs: List<com.example.evida.data.local.EvidenceLog>, query: String, onQueryChange: (String) -> Unit, context: Context) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search hash or app...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        if (logs.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(logs) { log ->
                    EvidenceLogItem(
                        log = log,
                        onClick = {
                            val intent = Intent(context, EvidenceDetailActivity::class.java).apply {
                                // ... existing putExtra logic
                                putExtra("timestamp", log.timestamp)
                                putExtra("ntpTimestamp", log.ntpTimestamp ?: 0L)
                                putExtra("hashId", log.hashId)
                                putExtra("latitude", log.latitude ?: 0.0)
                                putExtra("longitude", log.longitude ?: 0.0)
                                putExtra("deviceModel", log.deviceModel)
                                putExtra("osVersion", log.osVersion)
                                putExtra("foregroundApp", log.foregroundApp)
                                putExtra("installer", log.installerPackage)
                                putExtra("digitalSignature", log.digitalSignature)
                                putExtra("iv", log.encryptionIv)
                                putExtra("appSignature", log.appSignature)
                                putExtra("isTimeAutomatic", log.isTimeAutomatic)
                                putExtra("isVpnActive", log.isVpnActive)
                                putExtra("isAdbEnabled", log.isAdbEnabled)
                                putExtra("isRooted", log.isRooted)
                                putExtra("isMockLocation", log.isMockLocation)
                                putExtra("elapsedRealtime", log.elapsedRealtime)
                                putExtra("wrappedKey", log.wrappedKey)
                                putExtra("localWrappedKey", log.localWrappedKey)
                                putExtra("localWrappedKeyIv", log.localWrappedKeyIv)
                                putExtra("batteryLevel", log.batteryLevel)
                                putExtra("networkType", log.networkType)
                                putExtra("ssid", log.ssid)
                                putExtra("isDeveloperOptionsEnabled", log.isDeveloperOptionsEnabled)
                                putExtra("appSourceStatus", log.appSourceStatus)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTab(context: Context) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("SECURITY CONFIG", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                val intent = Intent(context, PasscodeActivity::class.java).apply {
                    putExtra("isSetup", false)
                    putExtra("isChangeRequest", true)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.LockReset, null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("CHANGE EVIDA PIN")
        }
    }
}

@Composable
fun IntegrityItem(label: String, status: String, isOk: Boolean, onAction: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = (if (isOk) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.GppMaybe,
                        contentDescription = null,
                        tint = if (isOk) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (isOk) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Red)
            }
        }
        if (!isOk) {
            IconButton(onClick = onAction) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun isUsageAllowed(context: Context): Boolean {
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

@Composable
fun SecurityStatsHeader(count: Int) {
    val animatedCount by animateIntAsState(targetValue = count, label = "count")
    
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(140.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "ACTIVE & SECURE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Pulse Animation for "Live" effect
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.Green.copy(alpha = alpha), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE MONITORING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Green.copy(alpha = 0.7f))
                }
            }
            
            // Circular Progress Visualization
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeWidth = 6.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                val progress = (count.toFloat() / 10f).coerceAtMost(1f)
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = animatedCount.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No records found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Text(
            "Use the floating widget to begin capture.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp).padding(top = 4.dp)
        )
    }
}
