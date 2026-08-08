package com.chat360.chatbot.ui.components.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Bucket-resamples [amplitudes] (raw 0-32767 MediaRecorder samples) into [barCount] bars and
 * draws them. Used both for live-recording bars and, with [progress] set, for the sent-bubble
 * playback waveform - unified into one component since both just need "bars + how much of them
 * is 'played/elapsed'".
 *
 * @param progress 0f (nothing played/elapsed yet - all bars dim) to 1f (all bars in [color]); null
 *   while live-recording, where every bar is already "live" and drawn at full [color].
 */
@Composable
fun VoiceWaveformBars(
    amplitudes: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    barCount: Int = 32,
    barWidthDp: Int = 3,
    barGapDp: Int = 2,
) {
    val bars = resampleBars(amplitudes, barCount)
    Canvas(
        modifier = modifier
            .height(28.dp)
            .width((barCount * (barWidthDp + barGapDp)).dp),
    ) {
        drawBars(bars, color, progress, barWidthDp, barGapDp)
    }
}

private fun resampleBars(amplitudes: List<Int>, barCount: Int): List<Float> {
    if (amplitudes.isEmpty()) return List(barCount) { 0.08f }
    val maxAmp = (amplitudes.maxOrNull() ?: 1).coerceAtLeast(1)
    val bucketSize = max(1, amplitudes.size / barCount)
    return List(barCount) { i ->
        val start = min(i * bucketSize, amplitudes.size - 1)
        val end = min(start + bucketSize, amplitudes.size)
        val slice = if (end > start) amplitudes.subList(start, end) else listOf(amplitudes[start])
        val avg = slice.average().toFloat()
        max(0.08f, avg / maxAmp)
    }
}

private fun DrawScope.drawBars(bars: List<Float>, color: Color, progress: Float?, barWidthDp: Int, barGapDp: Int) {
    val barWidth = barWidthDp.dp.toPx()
    val barGap = barGapDp.dp.toPx()
    val totalBarWidth = barWidth + barGap
    val playedCount = progress?.let { (it * bars.size).toInt() } ?: bars.size
    bars.forEachIndexed { index, heightFraction ->
        val barHeight = max(barWidth, heightFraction * size.height)
        val x = index * totalBarWidth
        val y = (size.height - barHeight) / 2
        val alpha = if (progress == null || index < playedCount) 1f else 0.35f
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
        )
    }
}
