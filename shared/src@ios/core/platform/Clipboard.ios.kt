package de.franzsw.ctx2cal.core.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun ClipEntry.plainTextOrNull(): String? = getPlainText()
