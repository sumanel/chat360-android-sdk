package com.chat360.chatbot.ui.components.messages.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Horizontally scrolling cards for a CAROUSEL node; a tap opens the card's link externally. */
@Composable
fun CarouselContent(caption: String, content: BotContent.Carousel) {
    Column {
        if (caption.isNotEmpty()) {
            PlainTextContent(caption)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(content.cards) { card -> CarouselCard(card) }
        }
    }
}

@Composable
private fun CarouselCard(card: BotContent.Carousel.Card) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .let { base ->
                if (card.link != null) {
                    base.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.link))) }
                } else base
            },
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.heading,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.aspectRatio(1f),
        )
        Column(modifier = Modifier.padding(10.dp)) {
            card.heading?.let {
                Text(
                    text = it,
                    fontFamily = typography.textFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }
            card.caption?.let {
                Text(
                    text = it,
                    fontFamily = typography.textFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 3,
                )
            }
        }
    }
}
