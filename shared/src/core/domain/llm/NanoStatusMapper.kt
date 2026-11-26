package de.franzsw.ctx2cal.core.domain.llm

import de.franzsw.ctx2cal.core.domain.model.ModelStatus

/**
 * Pure-Kotlin mapping for ML Kit GenAI FeatureStatus and on-device base model presence.
 *
 * Int constants mirror `com.google.mlkit.genai.common.FeatureStatus` so shared tests
 * can run without an Android runtime; Android delegates with `FeatureStatus.*`.
 */
object NanoStatusMapper {
    const val FEATURE_UNAVAILABLE = 0
    const val FEATURE_DOWNLOADABLE = 1
    const val FEATURE_DOWNLOADING = 2
    const val FEATURE_AVAILABLE = 3

    private val VERSION_V_REGEX = Regex("""(?:^|[-_\s])v(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
    private val VERSION_NUM_REGEX = Regex("""(?:^|[-_\s])(\d+(?:\.\d+)*)$""")

    fun parseNanoVersion(baseModelName: String?): String? {
        val trimmed = baseModelName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        VERSION_V_REGEX.find(trimmed)?.let { return "v${it.groupValues[1]}" }
        VERSION_NUM_REGEX.find(trimmed)?.let { return "v${it.groupValues[1]}" }
        return trimmed
    }

    fun mapNanoStatus(featureStatus: Int, baseModelName: String?): ModelStatus = when (featureStatus) {
        FEATURE_AVAILABLE -> ModelStatus.NanoAvailable(parseNanoVersion(baseModelName))
        FEATURE_DOWNLOADABLE -> if (!baseModelName.isNullOrBlank()) {
            ModelStatus.NanoAvailable(parseNanoVersion(baseModelName))
        } else {
            ModelStatus.NanoDownloadable
        }
        FEATURE_DOWNLOADING -> if (!baseModelName.isNullOrBlank()) {
            ModelStatus.NanoAvailable(parseNanoVersion(baseModelName))
        } else {
            ModelStatus.NanoDownloading
        }
        else -> ModelStatus.NanoUnavailable
    }

    fun isNanoReady(featureStatus: Int, baseModelName: String?): Boolean =
        mapNanoStatus(featureStatus, baseModelName) is ModelStatus.NanoAvailable
}
