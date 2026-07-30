package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Ports the IMAGE_BUTTON node - a Carousel-shaped card per slide, each with its own buttons. */
@Composable
fun ImageButtonsContent(
    message: ChatMessage,
    content: BotContent.ImageButtons,
    isLiveChat: Boolean,
    onButtonClick: (BotContent.ImageButtons.Card, BotContent.ImageButtons.Button) -> Unit,
) {
    Column {
        PlainTextContent(message.text)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(content.cards) { card -> ImageButtonCard(card, content.submitType, message.repliesEnabled && !isLiveChat, onButtonClick) }
        }
    }
}

@Composable
private fun ImageButtonCard(
    card: BotContent.ImageButtons.Card,
    submitType: String,
    enabled: Boolean,
    onButtonClick: (BotContent.ImageButtons.Card, BotContent.ImageButtons.Button) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)),
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(1.4f),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            card.description?.let {
                Text(it, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textPrimary, maxLines = 2)
            }
            card.buttons.forEach { button ->
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = {
                        if (button.type == "web_url" && button.url != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(button.url)))
                        } else {
                            onButtonClick(card, button)
                        }
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentContrast, disabledContainerColor = colors.textDisabled),
                    modifier = Modifier.width(200.dp),
                ) {
                    Text(button.text, fontFamily = typography.textFamily, fontSize = 13.sp)
                }
            }
        }
    }
}
