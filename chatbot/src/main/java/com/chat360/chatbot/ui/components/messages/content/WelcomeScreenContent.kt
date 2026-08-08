package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.theme.toColorOrNull

/**
 * Renders the WELCOME_SCREEN node's card content and card-tap handling (see
 * [BotContent.WelcomeScreen]'s doc). Pinning whichever WELCOME_SCREEN message is currently the
 * latest outside the scroll list is handled by the caller, not here.
 */
@Composable
fun WelcomeScreenContent(
    message: ChatMessage,
    content: BotContent.WelcomeScreen,
    isLiveChat: Boolean,
    onCardSelected: (BotContent.WelcomeScreen.Card, Int) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current

    Column {
        if (!content.iconUrl.isNullOrBlank()) {
            AsyncImage(model = content.iconUrl, contentDescription = content.title, modifier = Modifier.width(40.dp).height(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            LogoBadge(size = 40.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        content.title?.let {
            Text(it, fontFamily = typography.headFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
        }
        content.description?.let {
            Text(it, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
        }
        if (content.cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(content.cards) { index, card ->
                    val enabled = message.repliesEnabled && !isLiveChat
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                            .background(card.bgColor?.toColorOrNull() ?: colors.cardBackground)
                            .clickable(enabled = enabled) {
                                // external_link cards open externally AND still submit a reply -
                                // opening the link never skips the selection callback.
                                if (card.ctaEnabled && card.ctaType == "external_link" && !card.ctaLink.isNullOrBlank()) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.ctaLink)))
                                }
                                onCardSelected(card, index)
                            }
                            .padding(12.dp),
                    ) {
                        card.title?.let {
                            Text(it, fontFamily = typography.textFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
