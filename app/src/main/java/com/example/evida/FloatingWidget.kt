package com.example.evida

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun FloatingWidget(
    isSecure: Boolean,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit
) {
    var isClicked by remember { mutableStateOf(false) }
    val statusColor = if (isSecure) Color(0xFF4CAF50) else Color(0xFFFF5252)
    
    // Animate scale: shrink slightly then pop back
    val scale by animateFloatAsState(
        targetValue = if (isClicked) 0.8f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "ScaleAnimation"
    )

    // Pulse animation for the integrity ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingAlpha"
    )

    LaunchedEffect(isClicked) {
        if (isClicked) {
            delay(150)
            isClicked = false
        }
    }

    Box(
        modifier = Modifier
            .size(70.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { 
                    isClicked = true
                    onClick() 
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Glowing Integrity Ring
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = statusColor,
                radius = size.minDimension / 2.1f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                alpha = ringAlpha
            )
        }

        // Inner Body
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFF212121), Color.Black)
                    )
                )
                .background(statusColor.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSecure) Icons.Default.Shield else Icons.Default.GppMaybe,
                contentDescription = "Capture Evidence",
                tint = if (isSecure) Color.White else statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
