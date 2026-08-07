package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.components.common.BrandLogo
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import androidx.compose.ui.text.style.TextAlign

@Composable
fun WelcomeSplash(modifier: Modifier = Modifier) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val branding = LocalChat360Branding.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
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
            text = branding.welcomeHeading,
            fontFamily = typography.headFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = branding.disclaimerText,
            fontFamily = typography.textFamily,
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = TextAlign.Center,
            )
    }
}
