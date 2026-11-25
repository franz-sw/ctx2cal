package de.franzsw.ctx2cal.core.domain.catalog

import de.franzsw.ctx2cal.core.domain.model.DeviceCapabilities
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability

/**
 * Pure-Kotlin hardware suitability evaluator for the LiteRT-LM catalog.
 * Keeps all ABI, RAM, free-storage, and CPU-only GPU/SoC classification rules
 * deterministic and testable without Android dependencies.
 */
object ModelSuitabilityEvaluator {

    private const val STORAGE_MULTIPLIER = 1.15
    private const val ANDROID_12_SDK = 31

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    private val OLD_ADRENO_REGEX = Regex("""adreno\s*(?:\(tm\)\s*)?([1-6]\d{2})\b""", RegexOption.IGNORE_CASE)

    private val OLD_GPU_MARKERS = listOf(
        "mali-g76",
        "mali-g72",
        "mali-g71",
        "mali-g52",
        "mali-g51",
        "mali-g31",
        "mali-t",
        "mali-4",
        "powervr",
        "swiftshader",
        "llvmpipe",
        "vivante",
    )

    private val OLD_SOC_MARKERS = listOf(
        "kirin980",
        "kirin 980",
        "hi3680",
        "kirin970",
        "kirin 970",
        "hi3670",
        "kirin810",
        "kirin 810",
        "kirin710",
        "kirin 710",
        "sdm845",
        "sdm855",
        "sm8150",
        "sm8250",
        "sdm710",
        "sdm660",
        "msm8998",
        "msm8996",
        "exynos9810",
        "exynos9820",
        "exynos9825",
    )

    fun evaluate(
        candidate: ModelCandidate,
        capabilities: DeviceCapabilities,
        isDownloaded: Boolean = false,
    ): ModelSuitability {
        if (capabilities.supportedAbis.none { it in SUPPORTED_ABIS }) {
            val abis = capabilities.supportedAbis.joinToString(", ").ifBlank { "unknown" }
            return ModelSuitability.Incompatible("Unsupported CPU architecture ($abis); requires 64-bit arm64-v8a or x86_64.")
        }

        if (capabilities.totalRamMb < candidate.minRamMb) {
            return ModelSuitability.Incompatible(
                "Requires ~${formatGb(candidate.minRamMb.toLong())} GB RAM (device has ${formatGb(capabilities.totalRamMb)} GB)."
            )
        }

        if (!isDownloaded) {
            val approxSize = candidate.approxSizeBytes
            if (approxSize != null && approxSize > 0L) {
                val requiredBytes = (approxSize * STORAGE_MULTIPLIER).toLong()
                val insufficientInternal = capabilities.internalFreeBytes < requiredBytes
                val insufficientExternal =
                    capabilities.externalFreeBytes != null && capabilities.externalFreeBytes < requiredBytes
                if (insufficientInternal || insufficientExternal) {
                    return ModelSuitability.Incompatible(
                        "Needs ${formatSizeMbOrGb(requiredBytes)} free storage."
                    )
                }
            }
        }

        val cpuOnlyReason = cpuOnlyReason(capabilities)
        if (cpuOnlyReason != null) {
            return ModelSuitability.CpuOnlySlow(cpuOnlyReason)
        }

        return ModelSuitability.Recommended
    }

    fun evaluateAll(
        capabilities: DeviceCapabilities,
        candidates: List<ModelCandidate> = ModelCatalogEntries.all,
        downloadedIds: Set<String> = emptySet(),
    ): Map<String, ModelSuitability> = candidates.associate { candidate ->
        candidate.id to evaluate(
            candidate = candidate,
            capabilities = capabilities,
            isDownloaded = candidate.id in downloadedIds,
        )
    }

    fun shouldShowNoAcceleratedWarning(
        nanoStatus: ModelStatus,
        suitabilities: Collection<ModelSuitability>,
    ): Boolean {
        if (nanoStatus is ModelStatus.NanoAvailable ||
            nanoStatus is ModelStatus.NanoDownloadable ||
            nanoStatus is ModelStatus.NanoDownloading ||
            nanoStatus is ModelStatus.Unsupported
        ) {
            return false
        }
        return suitabilities.none { it is ModelSuitability.Recommended }
    }

    private fun cpuOnlyReason(capabilities: DeviceCapabilities): String? {
        if (capabilities.gpuUnsupportedCached) {
            return "GPU acceleration is unavailable on this device; runs on CPU (may be slow)."
        }
        if (capabilities.sdkInt in 1 until ANDROID_12_SDK) {
            return "Older Android OS without supported GPU delegate; runs on CPU (may be slow)."
        }
        val renderer = capabilities.gpuRenderer.lowercase()
        if (renderer.isNotBlank()) {
            if (OLD_GPU_MARKERS.any { renderer.contains(it) } || OLD_ADRENO_REGEX.containsMatchIn(renderer)) {
                return "Older GPU (${capabilities.gpuRenderer.trim()}) lacks LiteRT GPU support; runs on CPU (may be slow)."
            }
        }
        val socSignal = listOf(capabilities.socModel, capabilities.hardware, capabilities.board)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()
        if (socSignal.isNotBlank() && OLD_SOC_MARKERS.any { socSignal.contains(it) }) {
            return "Older SoC lacks LiteRT GPU acceleration; runs on CPU (may be slow)."
        }
        return null
    }

    private fun formatGb(mb: Long): String {
        val tenths = ((mb / 1024.0) * 10 + 0.5).toLong()
        return if (tenths % 10L == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    private fun formatSizeMbOrGb(bytes: Long): String {
        val mb = bytes / 1_000_000L
        return if (mb >= 1000L) {
            val tenths = (bytes / 100_000_000.0 + 0.5).toLong()
            "${tenths / 10}.${tenths % 10} GB"
        } else {
            "$mb MB"
        }
    }
}
