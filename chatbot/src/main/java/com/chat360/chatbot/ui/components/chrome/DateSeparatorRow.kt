package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A WhatsApp-style centered pill ("Mon, 21 Sep") separating messages sent on different
 * calendar days. */
@Composable
fun DateSeparatorRow(label: String) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.backgroundElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
        }
    }
}

/** Calendar-day key (device-local timezone) used to group messages by day. */
fun chatDateSeparatorKey(timestampMs: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestampMs))

/** "Mon, 21 Sep" (year appended only when not the current year) - abbreviated day-of-week +
 * date, no "Today"/"Yesterday" wording and no full timestamp. */
fun chatDateSeparatorLabel(timestampMs: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val pattern = if (target.get(Calendar.YEAR) == currentYear) "EEE, d MMM" else "EEE, d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMs))
}
