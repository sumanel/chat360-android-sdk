package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun LinkCardContent(caption: String, content: BotContent.LinkCard) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current

    Column {
        if (caption.isNotEmpty()) PlainTextContent(caption)
        Column(
            modifier = Modifier
                .background(colors.cardBackground, RoundedCornerShape(10.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content.url))) }
                .padding(12.dp),
        ) {
            Text(content.url, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.accent, maxLines = 2)
        }
    }
}
