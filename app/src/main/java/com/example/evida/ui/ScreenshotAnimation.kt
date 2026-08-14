package com.example.evida.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun ScreenshotAnimation(bitmap: Bitmap, onAnimationComplete: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Quick White Flash
        flashAlpha.animateTo(0.6f, animationSpec = tween(100))
        flashAlpha.animateTo(0f, animationSpec = tween(200))
        
        // 2. Shrink and Fade effect
        scale.animateTo(0.85f, animationSpec = tween(400))
        alpha.animateTo(0f, animationSpec = tween(300))
        
        onAnimationComplete()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The captured screenshot preview
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale.value)
                .alpha(alpha.value),
        )
        
        // Flash overlay effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value))
        )
    }
}
