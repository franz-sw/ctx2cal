package de.franzsw.ctx2cal.core.platform

import androidx.compose.ui.platform.ClipEntry

actual suspend fun ClipEntry.plainTextOrNull(): String? {
    val data = clipData
    return if (data.itemCount > 0) data.getItemAt(0)?.text?.toString() else null
}
