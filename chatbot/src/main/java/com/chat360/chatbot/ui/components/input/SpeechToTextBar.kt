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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * The compact "Listening…"/Stop indicator shown while dictating - mirrors SpeechToText/index.tsx's
 * own small status row (no waveform of its own; that's [VoiceRecorderBar]'s domain for actual
 * voice-message capture, a deliberately separate concern from text dictation).
 */
@Composable
fun SpeechToTextBar(
    isListening: Boolean,
    error: String?,
    onStop: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .background(colors.inputBackground, RoundedCornerShape(12.dp))
                .border(1.dp, colors.inputBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isListening) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(10.dp))
            }
            Text(
                text = if (isListening) "Listening…" else if (error != null) "Error" else "Not listening",
                fontFamily = typography.textFamily,
                fontSize = 13.sp,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(colors.backgroundSunken, RoundedCornerShape(8.dp))
                    .clickable(onClick = onStop)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Stop",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                )
            }
        }
        error?.let {
            Text(
                text = it,
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = ActiveRed,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }
}
