package com.example.evida

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.IntentCompat
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.evida.data.LogRepository
import com.example.evida.data.local.EvidenceLogDatabase
import com.example.evida.ui.theme.EVIDATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.app.usage.UsageStatsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.evida.ui.ScreenshotAnimation
import kotlinx.coroutines.tasks.await
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import java.io.File

class FloatingWidgetService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingWidgetView: ComposeView? = null
    private var animationOverlayView: ComposeView? = null
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private lateinit var evidenceProcessor: EvidenceProcessor
    private lateinit var timeManager: TimeManager
    private lateinit var securityManager: SecurityManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isSecureState by mutableStateOf(value = true)
    
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    companion object {
        var hasProjectionToken = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "EVIDA_CHANNEL")
            .setContentTitle("EVIDA")
            .setContentText("Secured evidence capture active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        timeManager = TimeManager(this)
        securityManager = SecurityManager(this)

        val database = EvidenceLogDatabase.getDatabase(this)
        val logRepository = LogRepository(database.evidenceLogDao())
        evidenceProcessor = EvidenceProcessor(
            this,
            HashManager(),
            EncryptionManager(),
            logRepository,
        )

        startSecurityMonitor()
        showFloatingWidget()
    }

    private fun startSecurityMonitor() {
        serviceScope.launch {
            while (true) {
                val isRooted = securityManager.isDeviceRooted()
                val adbEnabled = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
                val vpnActive = checkVpnActive()
                isSecureState = !isRooted && !adbEnabled && !vpnActive
                kotlinx.coroutines.delay(2000L) // Check every 2 seconds
            }
        }
    }

    private fun showFloatingWidget() {
        @SuppressLint("InlinedApi")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        floatingWidgetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWidgetService)
            setViewTreeViewModelStoreOwner(this@FloatingWidgetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)

            setContent {
                EVIDATheme {
                    FloatingWidget(
                        isSecure = isSecureState,
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this, params)
                        },
                        onDragEnd = {
                            // Edge Snapping Logic
                            val metrics = DisplayMetrics()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                windowManager.currentWindowMetrics.bounds.let { bounds ->
                                    metrics.widthPixels = bounds.width()
                                    metrics.heightPixels = bounds.height()
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                windowManager.defaultDisplay.getMetrics(metrics)
                            }
                            val screenWidth = metrics.widthPixels
                            
                            // Snap to nearest horizontal edge
                            params.x = if (params.x > 0) screenWidth else -screenWidth
                            windowManager.updateViewLayout(this, params)
                        },
                    ) { captureScreenshot() }
                }
            }
        }

        windowManager.addView(floatingWidgetView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "ACTION_SCREENSHOT_SUCCESS") {
            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = IntentCompat.getParcelableExtra(intent, "data", Intent::class.java)
            if ((resultCode == Activity.RESULT_OK) && (data != null)) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                hasProjectionToken = true
                Toast.makeText(this, "Evidence protection initialized.", Toast.LENGTH_SHORT).show()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "EVIDA Service Channel"
            val channel = NotificationChannel("EVIDA_CHANNEL", name, NotificationManager.IMPORTANCE_MIN)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenshot() {
        val currentMediaProjection = mediaProjection ?: run {
            Toast.makeText(this, "Session expired. Re-open EVIDA.", Toast.LENGTH_LONG).show()
            hasProjectionToken = false
            return
        }

        // 1. SNAPSHOT INTEGRITY DATA IMMEDIATELY
        val appMetadata = getForegroundAppMetadata()
        val isTimeAuto = Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME, 0) == 1
        val isVpnActive = checkVpnActive()
        val isAdbEnabled = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val isRooted = checkRooted()
        val elapsedRealtime = SystemClock.elapsedRealtime()
        
        // V3.0 DEEP METADATA
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val isDevOptions = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        val netInfo = getNetworkForensics()

        // Visual Feedback: Briefly hide widget
        floatingWidgetView?.visibility = View.GONE
        
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { bounds ->
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            }
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        val virtualDisplay: VirtualDisplay? = currentMediaProjection.createVirtualDisplay(
            "Screenshot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        imageReader.setOnImageAvailableListener(
            { reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = createBitmap(
                        width + rowPadding / pixelStride, 
                        height, 
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Show Animation UI
                    showScreenshotAnimation(bitmap)

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    val screenshotBytes = outputStream.toByteArray()

                    serviceScope.launch(Dispatchers.IO) {
                        // Fetch Location
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@FloatingWidgetService)
                        val location = try {
                             @SuppressLint("MissingPermission")
                             fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                        } catch (_: Exception) {
                            null
                        }

                        // Check for Mock Location
                        val isMockLocation = location?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                it.isMock
                            } else {
                                @Suppress("DEPRECATION")
                                it.isFromMockProvider
                            }
                        } ?: false

                        // Get Atomic Timestamp if available
                        val ntpTimestamp = timeManager.getForensicTimestamp()

                        val success = evidenceProcessor.processEvidence(
                            screenshot = screenshotBytes,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            foregroundApp = appMetadata.packageName,
                            installer = appMetadata.installer,
                            appSignature = appMetadata.signature,
                            isTimeAutomatic = isTimeAuto,
                            isVpnActive = isVpnActive,
                            isAdbEnabled = isAdbEnabled,
                            isRooted = isRooted,
                            isMockLocation = isMockLocation,
                            elapsedRealtime = elapsedRealtime,
                            ntpTimestamp = ntpTimestamp,
                            batteryLevel = batteryLevel,
                            networkType = netInfo.first,
                            ssid = netInfo.second,
                            isDeveloperOptionsEnabled = isDevOptions
                        )
                        
                        launch(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(this@FloatingWidgetService, "Evidence secured", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@FloatingWidgetService, "Failed to secure evidence", Toast.LENGTH_LONG).show()
                            }
                            floatingWidgetView?.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { floatingWidgetView?.visibility = View.VISIBLE }
            } finally {
                image?.close()
                virtualDisplay?.release()
                reader.close()
            }
        }, null)
    }

    private fun getNetworkForensics(): Pair<String, String> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        
        var type = "DISCONNECTED"
        var ssid = "N/A"
        
        if (caps != null) {
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    type = "WIFI"
                    try {
                        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        @Suppress("DEPRECATION")
                        val info = wm.connectionInfo
                        val name = info.ssid.replace("\"", "")
                        ssid = if (name == "<unknown ssid>") "SECURED_WLAN" else name
                    } catch (_: Exception) {
                        ssid = "SECURED_WLAN"
                    }
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    type = "CELLULAR"
                    try {
                        val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                        val operatorName = tm.networkOperatorName
                        ssid = if (!operatorName.isNullOrBlank()) operatorName else "MOBILE_DATA"
                    } catch (e: Exception) {
                        ssid = "CELLULAR_NET"
                    }
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    type = "ETHERNET"
                    ssid = "WIRED"
                }
                else -> {
                    type = "CONNECTED"
                    ssid = "ACTIVE_NET"
                }
            }
        }
        return Pair(type, ssid)
    }

    private fun checkVpnActive(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun checkRooted(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su")
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private data class AppMetadata(val packageName: String, val installer: String, val signature: String)

    private fun getForegroundAppMetadata(): AppMetadata {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Check last minute
        
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        
        // Find the app with the most recent "lastTimeUsed"
        // We no longer filter out our own package because we might want to capture ourselves
        val pkg = stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: "Unknown"

        if (pkg == "Unknown") return AppMetadata("Unknown", "Unknown", "Unknown")
        if (pkg == packageName) return AppMetadata("EVIDA CORE", "System Image (Factory)", "OFFICIAL")

        var installer = "Unknown"
        var signature = "Unknown"

        try {
            val pm = packageManager
            
            // 1. Get Installer
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(pkg)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }

            val appInfo = pm.getApplicationInfo(pkg, 0)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                             (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            installer = when {
                installerPackage == "com.android.vending" -> "Google Play Store"
                installerPackage != null -> installerPackage
                isSystemApp -> "System Image (Factory)"
                else -> "Manual/ADB"
            }

            // 2. Get Signature
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            signature = signatures?.firstOrNull()?.toCharsString()?.take(32) ?: "Unknown"
            
        } catch (e: Exception) {}

        return AppMetadata(pkg, installer, signature)
    }

    private fun showScreenshotAnimation(bitmap: Bitmap) {
        serviceScope.launch(Dispatchers.Main) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            animationOverlayView = ComposeView(this@FloatingWidgetService).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeViewModelStoreOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)

                setContent {
                    EVIDATheme {
                        ScreenshotAnimation(
                            bitmap = bitmap,
                            onAnimationComplete = {
                                removeAnimationOverlay()
                            }
                        )
                    }
                }
            }
            windowManager.addView(animationOverlayView, params)
        }
    }

    private fun removeAnimationOverlay() {
        animationOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            animationOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaProjection?.stop()
        floatingWidgetView?.let {
            windowManager.removeView(it)
        }
    }
}
