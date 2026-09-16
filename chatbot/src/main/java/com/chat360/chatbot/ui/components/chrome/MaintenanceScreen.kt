package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.components.common.BrandLogo
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Full-screen branded fallback shown instead of the whole chat UI when the pre-connect
 * under-maintenance check (see ChatViewModel.init) comes back active - no history loads, no
 * socket opens, so a thin input-bar banner would sit over an otherwise-empty screen; this
 * replaces the screen outright instead. */
@Composable
fun MaintenanceScreen(message: String, modifier: Modifier = Modifier) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val branding = LocalChat360Branding.current
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (branding.logo != null) {
            BrandLogo(modifier = Modifier.width(LocalConfiguration.current.screenWidthDp.dp * 0.5f))
        } else {
            LogoBadge(size = 84.dp, cornerRadius = 20.dp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "We'll be right back",
            fontFamily = typography.headFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            fontFamily = typography.textFamily,
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}
