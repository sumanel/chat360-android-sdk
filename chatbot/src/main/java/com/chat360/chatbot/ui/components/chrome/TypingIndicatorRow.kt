package com.chat360.chatbot.ui.components.chrome

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.chat360.chatbot.config.LocalChat360UIConfig
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.theme.LocalChat360Colors

/** A compact "bot is responding" bubble - three softly pulsing dots, styled like the AI message
 * bubble but pill-shaped and content-sized, instead of a "X is typing…" text row. */
@Composable
fun TypingIndicatorRow() {
    val colors = LocalChat360Colors.current
    val showBotAvatar = LocalChat360UIConfig.current.features.showBotAvatar
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBotAvatar) {
            LogoBadge(size = 28.dp)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(colors.bubbleAiBackground, RectangleShape)
                .border(1.dp, colors.cardBorder, RectangleShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            repeat(3) { index ->
                if (index > 0) Spacer(modifier = Modifier.size(4.dp))
                TypingDot(delayMillis = index * 160, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int, color: Color) {
    val transition = rememberInfiniteTransition(label = "typingDot")
    val scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "typingDotScale",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = 0.4f + scale * 0.6f
            }
            .background(color, CircleShape),
    )
}