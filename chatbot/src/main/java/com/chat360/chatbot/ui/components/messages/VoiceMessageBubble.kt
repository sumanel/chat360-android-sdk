package com.chat360.chatbot.ui.components.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.VoiceMessageInfo
import com.chat360.chatbot.ui.components.icons.PauseIcon
import com.chat360.chatbot.ui.components.icons.PlayIcon
import com.chat360.chatbot.ui.components.input.VoiceWaveformBars
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.util.rememberVoicePlaybackController
import java.util.Locale

@Composable
fun VoiceMessageBubble(voiceMessage: VoiceMessageInfo) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val playback = rememberVoicePlaybackController()

    val progress = if (playback.durationMs > 0) {
        playback.positionMs.toFloat() / playback.durationMs
    } else {
        0f
    }
    val totalSec = (if (playback.durationMs > 0) playback.durationMs else voiceMessage.durationMs.toInt()) / 1000
    val displaySec = if (playback.isPlaying || playback.positionMs > 0) playback.positionMs / 1000 else totalSec

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (playback.isPlaying) PauseIcon else PlayIcon,
            contentDescription = if (playback.isPlaying) "Pause voice message" else "Play voice message",
            tint = colors.bubbleUserText,
            modifier = Modifier
                .background(colors.bubbleUserText.copy(alpha = 0.15f), CircleShape)
                .clickable { playback.playOrPause(voiceMessage.localFilePath, voiceMessage.remoteUrl) }
                .padding(6.dp)
                .size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        VoiceWaveformBars(
            amplitudes = voiceMessage.amplitudes,
            color = colors.bubbleUserText,
            progress = progress,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = String.format(Locale.getDefault(), "%d:%02d", displaySec / 60, displaySec % 60),
            fontFamily = typography.textFamily,
            fontSize = 11.sp,
            color = colors.bubbleUserText,
        )
    }
}
