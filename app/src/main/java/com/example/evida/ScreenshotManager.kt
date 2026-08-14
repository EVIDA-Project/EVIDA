package com.example.evida

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher

class ScreenshotManager(private val context: Context) {

    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun startScreenshotCapture(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
