package de.franzsw.ctx2cal.prototype

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

/**
 * Spike prototype for task 1.4: minimal Gemini Nano call.
 * Call [nanoProbe] from a foreground coroutine on a Pixel-class device
 * and record either the returned text or the unavailable status.
 */
suspend fun nanoProbe(): String {
    val model = Generation.getClient()
    return try {
        val status = model.checkStatus()
        when (status) {
            FeatureStatus.AVAILABLE -> {
                val response = model.generateContent("Say hello in a few words.")
                "AVAILABLE: " + (response.candidates.firstOrNull()?.text ?: "<no candidates>")
            }
            FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE: model is downloadable but not downloaded"
            FeatureStatus.DOWNLOADING -> "DOWNLOADING: model download is in progress"
            else -> "UNAVAILABLE: Gemini Nano is not available on this device"
        }
    } finally {
        model.close()
    }
}
