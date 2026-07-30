package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.QuickReplyButton
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.theme.toColorOrNull

/**
 * Ports the TEXT_CAROUSEL node - both wire generations (`type1`/`type2`) render through this one
 * card shape (see [BotContent.TextCarousel]'s doc for the ported-simplification note).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextCarouselContent(
    message: ChatMessage,
    content: BotContent.TextCarousel,
    isLiveChat: Boolean,
    onCardTap: (text: String, clickedIndex: Int, targetId: String?) -> Unit,
) {
    val enabled = message.repliesEnabled && !isLiveChat
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(content.cards) { index, card -> TextCarouselCard(card, index, enabled, onCardTap) }
        }
        if (content.dynamicButtons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.dynamicButtons.forEachIndexed { i, button ->
                    QuickReplyButton(
                        text = button.title,
                        enabled = enabled,
                        selected = false,
                        onClick = { onCardTap(button.title, -(i + 1), button.componentUuid ?: button.targetId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextCarouselCard(
    card: BotContent.TextCarousel.Card,
    index: Int,
    enabled: Boolean,
    onCardTap: (String, Int, String?) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current
    val background = card.bgColor?.toColorOrNull() ?: colors.cardBackground

    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .background(background)
            .let { base ->
                if (card.redirectLink != null) {
                    base.clickable(enabled = enabled) {
                        if (card.redirectType == "link") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.redirectLink)))
                        } else {
                            onCardTap(card.content ?: card.name.orEmpty(), index, card.redirectLink)
                        }
                    }
                } else {
                    base
                }
            }
            .padding(12.dp),
    ) {
        if (!card.bgImage.isNullOrBlank()) {
            AsyncImage(model = card.bgImage, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(1.6f))
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (!card.iconText.isNullOrBlank() || !card.iconUrl.isNullOrBlank()) {
            Row {
                if (!card.iconUrl.isNullOrBlank()) {
                    AsyncImage(model = card.iconUrl, contentDescription = null, modifier = Modifier.width(16.dp).aspectRatio(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                card.iconText?.let { Text(it, fontFamily = typography.textFamily, fontSize = 12.sp, color = colors.textSecondary) }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        card.content?.let {
            Text(it, fontFamily = typography.textFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.textPrimary)
        }
        card.footerText?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, fontFamily = typography.textFamily, fontSize = 12.sp, color = colors.textSecondary)
        }
        if (card.ctaButtons.size == 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.ctaButtons.forEach { cta ->
                    Button(
                        onClick = {
                            if (cta.type == "link") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cta.link)))
                            } else {
                                onCardTap(cta.name, index, cta.link)
                            }
                        },
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentContrast),
                        modifier = Modifier.width(90.dp),
                    ) {
                        Text(cta.name, fontFamily = typography.textFamily, fontSize = 12.sp)
                    }
                }
            }
        }
        card.buttons.forEach { button ->
            Spacer(modifier = Modifier.height(6.dp))
            QuickReplyButton(
                text = button.label,
                enabled = enabled,
                selected = false,
                onClick = { onCardTap(button.label, index, button.link) },
            )
        }
    }
}
