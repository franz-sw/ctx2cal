package de.franzsw.ctx2cal.core.domain.model

/** What on-device model capability is currently available. */
sealed interface ModelStatus {
    data class NanoAvailable(val version: String? = null) : ModelStatus {
        val displayName: String
            get() = if (version.isNullOrBlank()) "Gemini Nano" else "Gemini Nano $version"
    }
    data object NanoDownloadable : ModelStatus
    data object NanoDownloading : ModelStatus
    data object NanoUnavailable : ModelStatus
    data class LiteRtReady(
        val modelPath: String,
        val modelId: String? = null,
        val modelName: String? = null,
    ) : ModelStatus
    data object LiteRtNotDownloaded : ModelStatus
    data class Downloading(
        val progressBytes: Long?,
        val totalBytes: Long?,
        val modelId: String? = null,
    ) : ModelStatus
    data object Unsupported : ModelStatus
    data object None : ModelStatus
}

/** A `.litertlm` model offered in the ungated catalog. */
data class ModelCandidate(
    val id: String,
    val name: String,
    val url: String,
    val fileName: String,
    val approxSizeBytes: Long?,
    val minRamMb: Int,
    val description: String = "",
    val license: String = "Apache-2.0",
) {
    /** Disk filename inside `filesDir/models/` — always uses `.litertlm` extension for LiteRT-LM. */
    val localFileName: String get() = "$id.litertlm"
}

/** Snapshot of device hardware capabilities relevant to on-device LLM suitability. */
data class DeviceCapabilities(
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val internalFreeBytes: Long,
    val externalFreeBytes: Long? = null,
    val sdkInt: Int = 0,
    val socModel: String = "",
    val hardware: String = "",
    val board: String = "",
    val gpuRenderer: String = "",
    val hasVulkan: Boolean = false,
    val gpuUnsupportedCached: Boolean = false,
)

/** Hardware suitability tier for a catalog model on the current device. */
sealed interface ModelSuitability {
    data object Recommended : ModelSuitability
    data class CpuOnlySlow(val reason: String) : ModelSuitability
    data class Incompatible(val reason: String) : ModelSuitability
}

/** Per-model domain/UI view combining catalog metadata, download/active state, and suitability. */
data class CatalogModelState(
    val candidate: ModelCandidate,
    val downloadedPath: String?,
    val isActive: Boolean,
    val suitability: ModelSuitability,
) {
    val isDownloaded: Boolean get() = downloadedPath != null
}

/** Atomic snapshot of system + catalog model availability, suitability, and active selection. */
data class ModelCatalogSnapshot(
    val nanoStatus: ModelStatus,
    val models: List<CatalogModelState>,
    val activeModelId: String?,
    val showNoAcceleratedWarning: Boolean,
)

/** Curated catalog of verified ungated Apache-2.0 `.litertlm` models. */
object ModelCatalogEntries {
    const val NANO_MODEL_ID = "nano"

    val QWEN3_0_6B_INT4 = ModelCandidate(
        id = "qwen3-0.6b-int4",
        name = "Qwen3 0.6B INT4",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        approxSizeBytes = 343_490_560L,
        minRamMb = 3_584,
        description = "Smallest & fastest (~343 MB, 4 GB+ RAM)",
        license = "Apache-2.0",
    )

    val QWEN3_1_7B_INT4 = ModelCandidate(
        id = "qwen3-1.7b-int4",
        name = "Qwen3 1.7B INT4",
        url = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        approxSizeBytes = 977_184_032L,
        minRamMb = 5_376,
        description = "Balanced quality & size (~977 MB, 6 GB+ RAM)",
        license = "Apache-2.0",
    )

    val GEMMA_4_E2B = ModelCandidate(
        id = "gemma-4-e2b",
        name = "Gemma 4 E2B",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        approxSizeBytes = 2_588_147_712L,
        minRamMb = 7_168,
        description = "High quality (~2.59 GB, 8 GB+ RAM)",
        license = "Apache-2.0",
    )

    val GEMMA_4_E4B = ModelCandidate(
        id = "gemma-4-e4b",
        name = "Gemma 4 E4B",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        approxSizeBytes = 3_659_530_240L,
        minRamMb = 10_752,
        description = "Highest accuracy (~3.66 GB, 12 GB+ RAM)",
        license = "Apache-2.0",
    )

    val all: List<ModelCandidate> = listOf(
        QWEN3_0_6B_INT4,
        QWEN3_1_7B_INT4,
        GEMMA_4_E2B,
        GEMMA_4_E4B,
    )

    fun findById(id: String?): ModelCandidate? =
        if (id == null) null else all.firstOrNull { it.id == id }
}
