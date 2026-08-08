package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.R
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * A tappable "upload" row inside the bot bubble, plus (when `enableCameraInput` is set and the
 * allowed types include an image extension) a second row to capture a photo directly instead of
 * picking one. Both rows are shown together whenever both are available.
 */
@Composable
fun FileUploadPromptContent(
    content: BotContent.FileUploadPrompt,
    isLiveChat: Boolean,
    onAttachmentClick: () -> Unit,
    onCameraClick: () -> Unit = {},
) {
    val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp", "jfif")
    val showCameraOption = content.allowCamera && content.allowedExtensions.any { it in imageExtensions }
    Column {
        content.promptText?.takeIf { it.isNotEmpty() }?.let {
            PlainTextContent(it)
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (showCameraOption) {
            UploadRow(
                iconRes = R.drawable.ic_outline_photo_camera_24,
                label = "Take a photo",
                enabled = !isLiveChat,
                onClick = onCameraClick,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        UploadRow(
            iconRes = R.drawable.ic_outline_insert_file_24,
            label = "Upload a file",
            enabled = !isLiveChat,
            onClick = onAttachmentClick,
        )
    }
}

@Composable
private fun UploadRow(iconRes: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundSunken, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(36.dp).background(colors.textPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = label,
            fontFamily = typography.textFamily,
            fontSize = 14.sp,
            color = colors.textPrimary,
        )
    }
}
