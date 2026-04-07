package top.jlen.vod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadingPane(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = UiPalette.Accent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = UiPalette.TextSecondary)
        }
    }
}

@Composable
fun EmptyPane(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.BackgroundBottom),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = UiPalette.TextMuted)
    }
}
