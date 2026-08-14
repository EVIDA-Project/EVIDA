package com.example.evida

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.evida.data.LogRepository
import com.example.evida.data.local.EvidenceLogDatabase
import com.example.evida.ui.EvidenceLogScreen
import com.example.evida.ui.EvidenceLogViewModel
import com.example.evida.ui.EvidenceLogViewModelFactory
import com.example.evida.ui.OnboardingActivity
import com.example.evida.ui.PasscodeActivity
import com.example.evida.ui.theme.EVIDATheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val database by lazy { EvidenceLogDatabase.getDatabase(this) }
    private val repository by lazy { LogRepository(database.evidenceLogDao()) }
    private val viewModel: EvidenceLogViewModel by viewModels {
        EvidenceLogViewModelFactory(repository)
    }
    private lateinit var screenshotManager: ScreenshotManager
    private lateinit var securityManager: SecurityManager
    private val timeManager by lazy { TimeManager(applicationContext) }

    // SESSION STATE
    companion object {
        var isAppUnlocked = false
    }

    private val passcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            isAppUnlocked = true
            securityManager.setLastLockTime(System.currentTimeMillis())
            showMainUI()
            checkAndProceed()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        screenshotManager = ScreenshotManager(this)
        securityManager = SecurityManager(this)
        
        // Initialize Forensic Encryption Keys
        EncryptionManager.initializeKeys(this)

        if (!securityManager.isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        lifecycleScope.launch {
            timeManager.syncWithAtomicClock()
        }

        if (isAppUnlocked) {
            showMainUI()
        }
    }

    override fun onResume() {
        super.onResume()
        if (securityManager.isOnboardingComplete()) {
            checkAppLock()
        }
    }

    private fun checkAppLock() {
        if (!securityManager.isPasscodeSet()) {
            val intent = Intent(this, PasscodeActivity::class.java).apply { putExtra("isSetup", true) }
            passcodeLauncher.launch(intent)
            return
        }

        val lastLock = securityManager.getLastLockTime()
        val timeElapsed = System.currentTimeMillis() - lastLock
        
        if (!isAppUnlocked || (timeElapsed > 30000)) {
            isAppUnlocked = false
            val intent = Intent(this, PasscodeActivity::class.java).apply { putExtra("isSetup", false) }
            passcodeLauncher.launch(intent)
        } else {
            showMainUI()
            checkAndProceed()
        }
    }

    private fun showMainUI() {
        setContent {
            EVIDATheme {
                EvidenceLogScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkAndProceed() {
        if (!isAppUnlocked) return

        val missingGeneral = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingGeneral.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && 
            (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            missingGeneral.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missingGeneral.isNotEmpty()) {
            return
        }

        if (!isUsageStatsAllowed()) return
        if (!Settings.canDrawOverlays(this)) return

        startForensicLogic()
    }

    private fun isUsageStatsAllowed(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startForensicLogic() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isAppUnlocked) {
            securityManager.setLastLockTime(System.currentTimeMillis())
        }
    }
}
