package de.franzsw.ctx2cal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import de.franzsw.ctx2cal.core.presentation.design_system.theme.AppTheme
import de.franzsw.ctx2cal.extract.presentation.ExtractScreen
import de.franzsw.ctx2cal.extract.presentation.ExtractUiState

class MainActivity : ComponentActivity() {

    private var pendingSharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        de.franzsw.ctx2cal.core.platform.PlatformContextHolder.value = applicationContext
        extractSharedText(intent)?.let { pendingSharedText = it }

        setContent {
            val darkTheme = isSystemInDarkTheme()
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            App(
                sharedText = pendingSharedText,
                onSharedTextConsumed = { pendingSharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)?.let { pendingSharedText = it }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }
}

@Preview(showBackground = true)
@Composable
fun AppAndroidPreview() {
    AppTheme {
        ExtractScreen(
            state = ExtractUiState.Input("Team sync tomorrow at 10:00 in Room B"),
            onTextChange = {},
            onExtract = {},
            onDraftChanged = {},
            onShare = {},
            onDownload = {},
            onBackToInput = {},
            onOpenSetup = {},
        )
    }
}
