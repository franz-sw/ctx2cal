package de.franzsw.ctx2cal.core.platform

import androidx.compose.ui.platform.ClipEntry

/**
 * Plain text carried by a [ClipEntry], or null if it holds no text.
 *
 * The new [androidx.compose.ui.platform.Clipboard] API (which replaces the
 * deprecated `ClipboardManager`) only exposes the opaque [ClipEntry] from
 * common code — it has no common text accessor yet (CMP-7624). Both current
 * platforms can read the text synchronously, but this is `suspend` to match
 * `Clipboard.getClipEntry()` and stay correct for a future async platform.
 */
expect suspend fun ClipEntry.plainTextOrNull(): String?
