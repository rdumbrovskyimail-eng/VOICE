package com.learnde.app.presentation.client

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.sin

@Composable
fun GeminiWaves(
    amplitudeProvider: () -> Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Бесконечный таймер для движения волн
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive && amplitudeProvider() > 0.1f) 2000 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Сглаживаем амплитуду
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isActive) amplitudeProvider().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(150),
        label = "amp"
    )

    // Цвета в стиле Gemini (Синий, Фиолетовый, Розовый/Голубой)
    val color1 = Color(0xFF4285F4).copy(alpha = 0.6f)
    val color2 = Color(0xFFA142F4).copy(alpha = 0.5f)
    val color3 = Color(0xFF24C1E0).copy(alpha = 0.4f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        // Базовая высота волны + реакция на голос
        val baseWaveHeight = height * 0.15f
        val reactiveHeight = height * 0.4f * animatedAmplitude

        fun drawWave(color: Color, phaseShift: Float, speedMultiplier: Float, heightMultiplier: Float) {
            val path = Path()
            path.moveTo(0f, height) // Начинаем с левого нижнего угла
            
            // Строим кривую волны
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                // Формула волны: sin(x + time)
                val yOffset = sin((normalizedX * 2 * Math.PI) + (time * speedMultiplier) + phaseShift).toFloat()
                val currentWaveHeight = (baseWaveHeight + reactiveHeight) * heightMultiplier
                val y = midY + yOffset * currentWaveHeight
                
                if (x == 0) path.lineTo(x.toFloat(), y)
                else path.lineTo(x.toFloat(), y)
            }
            
            path.lineTo(width, height) // В правый нижний угол
            path.close()

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(color, Color.Transparent),
                    startY = midY - (baseWaveHeight + reactiveHeight),
                    endY = height
                ),
                style = Fill
            )
        }

        // Рисуем 3 слоя волн с разной скоростью и фазой
        drawWave(color3, phaseShift = 0f, speedMultiplier = 1f, heightMultiplier = 1f)
        drawWave(color2, phaseShift = 2f, speedMultiplier = 1.3f, heightMultiplier = 0.8f)
        drawWave(color1, phaseShift = 4f, speedMultiplier = 0.8f, heightMultiplier = 1.2f)
    }
}