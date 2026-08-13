package com.chat360.chatbot.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.R
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.components.icons.ArrowUpIcon
import com.chat360.chatbot.ui.components.icons.CheckIcon
import com.chat360.chatbot.ui.components.icons.PauseIcon
import com.chat360.chatbot.ui.components.icons.PlayIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.util.VoicePlaybackController
import java.util.Locale

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
}

/**
 * Swaps in for [ChatInputBar]'s whole row while a voice note is being recorded or reviewed:
 * while recording, shows a waveform with cancel/stop; while reviewing, shows play/delete/send
 * with upload progress/error.
 */
@Composable
fun VoiceRecorderBar(
    isRecording: Boolean,
    liveAmplitudes: List<Int>,
    elapsedMs: Long,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    draftAmplitudes: List<Int>,
    draftDurationMs: Long,
    draftUploading: Boolean,
    draftUploadProgress: Int,
    draftError: String?,
    playbackController: VoicePlaybackController,
    draftLocalFilePath: String?,
    onSendDraft: () -> Unit,
    onCancelDraft: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Column {
        draftError?.let {
            Text(
                text = it,
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = ActiveRed,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .background(colors.inputBackground, RoundedCornerShape(12.dp))
                .border(1.dp, colors.inputBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_close_24),
                contentDescription = if (isRecording) "Cancel recording" else "Discard voice message",
                tint = ActiveRed,
                modifier = Modifier
                    .clickable(enabled = !draftUploading, onClick = if (isRecording) onCancelRecording else onCancelDraft)
                    .padding(6.dp)
                    .size(20.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))

            if (isRecording) {
                VoiceWaveformBars(
                    amplitudes = liveAmplitudes,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                    progress = null,
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = formatMs(elapsedMs),
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Icon(
                    imageVector = CheckIcon,
                    contentDescription = "Stop and review",
                    tint = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onStopRecording)
                        .padding(6.dp)
                        .size(22.dp),
                )
            } else {
                val progress = if (playbackController.durationMs > 0) {
                    playbackController.positionMs.toFloat() / playbackController.durationMs
                } else {
                    0f
                }
                Icon(
                    imageVector = if (playbackController.isPlaying) PauseIcon else PlayIcon,
                    contentDescription = if (playbackController.isPlaying) "Pause preview" else "Play preview",
                    tint = colors.accent,
                    modifier = Modifier
                        .clickable(enabled = !draftUploading) {
                            playbackController.playOrPause(draftLocalFilePath, null)
                        }
                        .padding(6.dp)
                        .size(20.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                VoiceWaveformBars(
                    amplitudes = draftAmplitudes,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                    progress = progress,
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = formatMs(draftDurationMs) + if (draftUploading) " · $draftUploadProgress%" else "",
                    fontFamily = typography.textFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accent, CircleShape)
                        .clickable(enabled = !draftUploading, onClick = onSendDraft),
                    contentAlignment = Alignment.Center,
                ) {
                    if (draftUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accentContrast,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = ArrowUpIcon,
                            contentDescription = "Send voice message",
                            tint = colors.accentContrast,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
