package com.chat360.chatbot.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors

/**
 * Emoji picker restricted to a single category (smileys/people), with search and skin-tone
 * variants disabled. Uses a curated representative subset rather than the full ~500-glyph set
 * for that category.
 */
private val SMILEYS_AND_PEOPLE = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
    "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸",
    "🤩", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
    "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
    "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
    "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
    "😦", "😧", "😮", "😲", "😴", "🤤", "😪", "😵", "🤐", "🥴",
    "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "👋", "🤚", "✋", "👌",
    "🤞", "✌️", "🤟", "👍", "👎", "👊", "👏", "🙌", "🙏", "❤️",
)

@Composable
fun EmojiPickerPanel(onEmojiSelected: (String) -> Unit) {
    val colors = LocalChat360Colors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .height(260.dp)
            .background(colors.backgroundElevated, RoundedCornerShape(12.dp)),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(SMILEYS_AND_PEOPLE) { emoji ->
            Text(
                text = emoji,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable { onEmojiSelected(emoji) }
                    .padding(6.dp),
            )
        }
    }
}
