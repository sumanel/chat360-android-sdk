package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.QuickReplyButton
import com.chat360.chatbot.ui.components.icons.PlayIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * Renders a MEDIA node. Images load inline via Coil; video/audio (no ExoPlayer dependency) show
 * a thumbnail-less play chip that opens the file in an external player. [content.dynamicButtons]
 * render as quick-reply pills below, reusing the same `carousel-text-reply` wire path and index
 * convention as TextCarouselContent's own dynamic buttons - see that file's onCardTap for the
 * shared -(i+1) encoding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaContent(
    message: ChatMessage,
    content: BotContent.Media,
    isLiveChat: Boolean,
    onDynamicButtonClick: (text: String, clickedIndex: Int, targetId: String?) -> Unit = { _, _, _ -> },
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current
    val caption = message.text
    val enabled = message.repliesEnabled && !isLiveChat

    Column {
        when (content.kind) {
            BotContent.MediaKind.IMAGE -> {
                AsyncImage(
                    model = content.url,
                    contentDescription = content.title ?: "Image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            BotContent.MediaKind.VIDEO, BotContent.MediaKind.AUDIO, BotContent.MediaKind.OTHER -> {
                val label = when (content.kind) {
                    BotContent.MediaKind.VIDEO -> "Play video"
                    BotContent.MediaKind.AUDIO -> "Play audio"
                    else -> "Open attachment"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.backgroundSunken)
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content.url)))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = PlayIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = content.title ?: label,
                        fontFamily = typography.textFamily,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                    )
                }
            }
        }
        if (content.title != null && content.kind == BotContent.MediaKind.IMAGE) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = content.title, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
        }
        if (caption.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PlainTextContent(caption)
        }
        if (content.dynamicButtons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.dynamicButtons.forEachIndexed { i, button ->
                    QuickReplyButton(
                        text = button.title,
                        enabled = enabled,
                        selected = false,
                        onClick = { onDynamicButtonClick(button.title, -(i + 1), button.componentUuid ?: button.targetId) },
                    )
                }
            }
        }
    }
}
