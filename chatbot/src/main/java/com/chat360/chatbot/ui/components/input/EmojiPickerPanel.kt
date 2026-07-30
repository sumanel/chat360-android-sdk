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
 * Ports Emojis/index.tsx's picker - restricted to the same single category the source config's
 * (`categories: [{category: Categories.SMILEYS_PEOPLE}]`), search/skin-tones disabled. A curated
 * representative subset stands in for emoji-picker-react's full bundled dataset for that
 * category (~500 glyphs) - same practical scope, not a byte-for-byte data port.
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
