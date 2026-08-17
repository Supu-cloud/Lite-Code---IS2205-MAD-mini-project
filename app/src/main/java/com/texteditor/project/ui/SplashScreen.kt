package com.texteditor.project.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.texteditor.project.R
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.qntm))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )

    // Cinematic scaling and alpha animations
    val contentScale by animateFloatAsState(
        targetValue = if (progress > 0.05f) 1.05f else 0.9f,
        animationSpec = tween(durationMillis = 4000, easing = EaseOutCubic),
        label = "scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (progress > 0.05f) 1f else 0f,
        animationSpec = tween(durationMillis = 2000),
        label = "alpha"
    )

    LaunchedEffect(progress) {
        if (progress == 1f) {
            delay(3000) // Hold for 3 seconds (slightly reduced for better UX)
            onAnimationFinished()
        }
    }

    // Techie Dark Gradient Background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF030508), // Near black
            Color(0xFF0A192F)  // Deep Navy matching AppInfo
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Enhanced Digital Rain
        DigitalRainBackground()

        // Layer 2: CRT Scanlines effect
        ScanlineOverlay()

        // Layer 3: Main Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                alpha = contentAlpha
            }
        ) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(280.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Logo with "Neon Glow" effect
            Box(contentAlignment = Alignment.Center) {
                // Subtle shadow/glow behind text
                Text(
                    text = "< Lite Code />",
                    color = Color(0xFF64FFDA).copy(alpha = 0.2f),
                    fontSize = 38.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = 2.dp)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "< ",
                        color = Color(0xFF64FFDA), // Teal accent
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Lite Code",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = " />",
                        color = Color(0xFF64FFDA),
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Subtitle with tracking animation
            val letterSpacing by animateFloatAsState(
                targetValue = if (progress > 0.1f) 6f else 2f,
                animationSpec = tween(4000, easing = LinearOutSlowInEasing),
                label = "spacing"
            )

            Text(
                text = "DEVELOPER EDITION",
                color = Color(0xFFCCD6F6).copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = letterSpacing.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun DigitalRainBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val color = Color(0xFF64FFDA).copy(alpha = 0.08f)

    val lines = remember {
        List(30) {
            TechLineState(
                x = Random.nextFloat(),
                size = Random.nextFloat() * 1.5f + 0.5f,
                speed = Random.nextFloat() * 2000 + 3000,
                opacity = Random.nextFloat() * 0.5f + 0.2f
            )
        }
    }

    lines.forEach { line ->
        val yOffset by infiniteTransition.animateFloat(
            initialValue = -0.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(line.speed.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset((Random.nextFloat() * line.speed).toInt())
            ),
            label = "y"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val startY = (yOffset - 0.15f) * size.height
            val endY = yOffset * size.height
            
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = line.opacity)),
                    startY = startY,
                    endY = endY
                ),
                start = Offset(line.x * size.width, startY),
                end = Offset(line.x * size.width, endY),
                strokeWidth = line.size.dp.toPx()
            )
        }
    }
}

@Composable
fun ScanlineOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanlineCount = (size.height / 8f).toInt()
        for (i in 0 until scanlineCount) {
            val y = i * 8f
            drawLine(
                color = Color.Black.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

data class TechLineState(val x: Float, val size: Float, val speed: Float, val opacity: Float)
