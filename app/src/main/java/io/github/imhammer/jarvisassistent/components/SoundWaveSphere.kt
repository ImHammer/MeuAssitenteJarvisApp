package io.github.imhammer.jarvisassistent.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SoundWaveSphere(rmsDb: Float, isListening: Boolean, modifier: Modifier = Modifier) {
    // Normaliza o valor de rmsDb para uma escala mais útil (ex: 0 a 1)
    val normalizedRms = ((rmsDb - (-2f)) / (12f - (-2f))).coerceIn(0f, 1f)

    // O tamanho alvo será 150dp quando não estiver escutando e até 250dp quando estiver.
    val targetSize = if (isListening) (150 + (normalizedRms * 100)).dp else 150.dp

    // Anima a mudança de tamanho de forma suave
    val animatedSize by animateDpAsState(
        targetValue = targetSize,
        label = "sphereSizeAnimation"
    )

    Canvas(modifier = modifier.size(animatedSize)) {
        drawCircle(
            color = Color.Blue, // Mude a cor se desejar
            radius = this.size.minDimension / 2
        )
    }
}