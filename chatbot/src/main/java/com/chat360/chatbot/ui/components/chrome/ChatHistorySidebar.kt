package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Temporary history drawer. Entries stay presentational until persistence is implemented. */
@Composable
fun ChatHistorySidebar(
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    isTrainingMode: Boolean,
    onAssistantModeChanged: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .background(colors.backgroundElevated),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(74.dp).border(1.dp, colors.line).clickable(onClick = onDismiss).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("<", fontSize = 24.sp, color = colors.accent)
            Spacer(Modifier.width(12.dp))
            Text("Menu", fontFamily = typography.textFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("AI Chatbot - History", fontFamily = typography.textFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(49.dp).background(colors.accent).clickable(onClick = onNewChat),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", fontSize = 25.sp, color = colors.accentContrast)
                Spacer(Modifier.width(12.dp))
                Text("New chat", fontFamily = typography.textFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.accentContrast)
            }
            Spacer(Modifier.height(28.dp))
            HistoryGroup("TODAY", listOf("CRETA vs VENUE comparison" to "10:42 AM", "EMI calculation for SX(O)" to "9:15 AM"))
            HistoryGroup("YESTERDAY", listOf("Nearest dealership in Pune" to "6:02 PM"))
            HistoryGroup("LAST 7 DAYS", listOf("Best family SUV recommend..." to "Mon"))
            HistoryGroup("OLDER", listOf("Petrol vs EV running cost" to "3 wks ago"))
        }
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, colors.line).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("ASSISTANT MODE", fontFamily = typography.textFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                ModeOption("Training", Modifier.weight(1f), isTrainingMode) { onAssistantModeChanged(true) }
                ModeOption("Customer", Modifier.weight(1f), !isTrainingMode) { onAssistantModeChanged(false) }
            }
            Spacer(Modifier.height(18.dp))
            Text("APPEARANCE", fontFamily = typography.textFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                ModeOption("Light", Modifier.weight(1f), !isDarkTheme) { onThemeChanged(false) }
                ModeOption("Dark", Modifier.weight(1f), isDarkTheme) { onThemeChanged(true) }
            }
        }
    }
}

@Composable
private fun HistoryGroup(title: String, items: List<Pair<String, String>>) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(title, fontFamily = typography.textFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
    Spacer(Modifier.height(12.dp))
    items.forEach { (name, time) ->
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.Top) {
            Text("o", fontSize = 19.sp, color = colors.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = typography.textFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
            }
            Text("...", fontSize = 16.sp, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ModeOption(text: String, modifier: Modifier, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(
        text = text,
        fontFamily = typography.textFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colors.accent else colors.textPrimary,
        modifier = modifier
            .background(if (selected) colors.backgroundElevated else colors.backgroundSunken)
            .then(if (selected) Modifier.border(1.dp, colors.line, RoundedCornerShape(0.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
