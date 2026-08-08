package com.chat360.chatbot.ui.components.messages.content

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.components.icons.DownloadIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun DownloadMediaContent(content: BotContent.DownloadMedia) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundSunken, RoundedCornerShape(8.dp))
            .clickable { downloadFile(context, content.fileUrl, content.fileName) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = DownloadIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = content.fileName,
            fontFamily = typography.textFamily,
            fontSize = 14.sp,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

private fun downloadFile(context: Context, url: String, fileName: String) {
    val request = DownloadManager.Request(url.toUri())
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}
