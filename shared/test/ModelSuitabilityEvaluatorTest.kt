package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.catalog.ModelSuitabilityEvaluator
import de.franzsw.ctx2cal.core.domain.model.DeviceCapabilities
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelSuitabilityEvaluatorTest {

    private val flagshipCapabilities = DeviceCapabilities(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        totalRamMb = 12_288L,
        internalFreeBytes = 20_000_000_000L,
        externalFreeBytes = 20_000_000_000L,
        sdkInt = 35,
        socModel = "SM8650",
        hardware = "qcom",
        board = "pineapple",
        gpuRenderer = "Adreno (TM) 750",
        hasVulkan = true,
        gpuUnsupportedCached = false,
    )

    private val p30ProCapabilities = DeviceCapabilities(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        totalRamMb = 5_700L, // Real usable RAM on 6 GB Huawei P30 Pro
        internalFreeBytes = 10_000_000_000L,
        externalFreeBytes = 10_000_000_000L,
        sdkInt = 29, // Android 10 (EMUI)
        socModel = "",
        hardware = "kirin980",
        board = "VOG",
        gpuRenderer = "Mali-G76",
        hasVulkan = true,
        gpuUnsupportedCached = false,
    )

    @Test
    fun `flagship device recommends all four catalog models`() {
        val results = ModelSuitabilityEvaluator.evaluateAll(flagshipCapabilities)
        assertEquals(4, results.size)
        for (candidate in ModelCatalogEntries.all) {
            assertIs<ModelSuitability.Recommended>(results[candidate.id])
        }
        assertFalse(
            ModelSuitabilityEvaluator.shouldShowNoAcceleratedWarning(
                nanoStatus = ModelStatus.NanoUnavailable,
                suitabilities = results.values,
            )
        )
    }

    @Test
    fun `p30 class 6gb device marks qwen3 models cpu-only and gemma models incompatible`() {
        val results = ModelSuitabilityEvaluator.evaluateAll(p30ProCapabilities)
        assertIs<ModelSuitability.CpuOnlySlow>(results[ModelCatalogEntries.QWEN3_0_6B_INT4.id])
        assertIs<ModelSuitability.CpuOnlySlow>(results[ModelCatalogEntries.QWEN3_1_7B_INT4.id])
        assertIs<ModelSuitability.Incompatible>(results[ModelCatalogEntries.GEMMA_4_E2B.id])
        assertIs<ModelSuitability.Incompatible>(results[ModelCatalogEntries.GEMMA_4_E4B.id])

        assertTrue(
            ModelSuitabilityEvaluator.shouldShowNoAcceleratedWarning(
                nanoStatus = ModelStatus.NanoUnavailable,
                suitabilities = results.values,
            )
        )
    }

    @Test
    fun `low ram device marks all models incompatible`() {
        val lowRam = flagshipCapabilities.copy(totalRamMb = 2_800L)
        val results = ModelSuitabilityEvaluator.evaluateAll(lowRam)
        for (candidate in ModelCatalogEntries.all) {
            val s = assertIs<ModelSuitability.Incompatible>(results[candidate.id])
            assertTrue(s.reason.contains("RAM", ignoreCase = true))
        }
    }

    @Test
    fun `unsupported 32-bit abi marks all models incompatible`() {
        val arm32Only = flagshipCapabilities.copy(supportedAbis = listOf("armeabi-v7a", "armeabi"))
        val results = ModelSuitabilityEvaluator.evaluateAll(arm32Only)
        for (candidate in ModelCatalogEntries.all) {
            val s = assertIs<ModelSuitability.Incompatible>(results[candidate.id])
            assertTrue(s.reason.contains("64-bit", ignoreCase = true))
        }
    }

    @Test
    fun `insufficient storage blocks download with 1_15x margin unless already downloaded`() {
        val size = ModelCatalogEntries.QWEN3_0_6B_INT4.approxSizeBytes!!
        val tightInternal = flagshipCapabilities.copy(internalFreeBytes = (size * 1.10).toLong())
        assertIs<ModelSuitability.Incompatible>(
            ModelSuitabilityEvaluator.evaluate(ModelCatalogEntries.QWEN3_0_6B_INT4, tightInternal)
        )

        val tightExternal = flagshipCapabilities.copy(externalFreeBytes = (size * 1.10).toLong())
        assertIs<ModelSuitability.Incompatible>(
            ModelSuitabilityEvaluator.evaluate(ModelCatalogEntries.QWEN3_0_6B_INT4, tightExternal)
        )

        // If already downloaded, storage check for download staging does not mark it incompatible.
        assertIs<ModelSuitability.Recommended>(
            ModelSuitabilityEvaluator.evaluate(
                ModelCatalogEntries.QWEN3_0_6B_INT4,
                tightInternal,
                isDownloaded = true,
            )
        )
    }

    @Test
    fun `cached gpuUnsupported downgrades modern device to CpuOnlySlow and triggers warning when nano unavailable`() {
        val cachedGpuFail = flagshipCapabilities.copy(gpuUnsupportedCached = true)
        val results = ModelSuitabilityEvaluator.evaluateAll(cachedGpuFail)
        assertIs<ModelSuitability.CpuOnlySlow>(results[ModelCatalogEntries.QWEN3_0_6B_INT4.id])
        assertTrue(
            ModelSuitabilityEvaluator.shouldShowNoAcceleratedWarning(
                nanoStatus = ModelStatus.NanoUnavailable,
                suitabilities = results.values,
            )
        )
        assertFalse(
            ModelSuitabilityEvaluator.shouldShowNoAcceleratedWarning(
                nanoStatus = ModelStatus.NanoAvailable,
                suitabilities = results.values,
            )
        )
    }

    @Test
    fun `older adreno 6xx gpu is classified CpuOnlySlow`() {
        val olderAdreno = flagshipCapabilities.copy(gpuRenderer = "Adreno (TM) 640", socModel = "SM8150")
        assertIs<ModelSuitability.CpuOnlySlow>(
            ModelSuitabilityEvaluator.evaluate(ModelCatalogEntries.QWEN3_0_6B_INT4, olderAdreno)
        )
    }
}
