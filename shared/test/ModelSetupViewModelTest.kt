package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.CatalogModelState
import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogSnapshot
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.DownloadProgress
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.setup.presentation.ModelSetupUiState
import de.franzsw.ctx2cal.setup.presentation.ModelSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCatalog(
        var status: ModelStatus,
        var candidate: ModelCandidate?,
        var suspendMillis: Long = 0L,
        var snapshotOverride: (() -> ModelCatalogSnapshot)? = null,
    ) : ModelCatalog {
        var statusCalls: Int = 0
        var candidateCalls: Int = 0
        val selectedModelIds: MutableList<String> = mutableListOf()
        var activeModelId: String? = null

        override suspend fun currentStatus(): ModelStatus {
            statusCalls++
            if (suspendMillis > 0L) delay(suspendMillis)
            return status
        }

        override suspend fun availableCandidate(): ModelCandidate? {
            candidateCalls++
            return candidate
        }

        override suspend fun catalogSnapshot(): ModelCatalogSnapshot {
            snapshotOverride?.let { return it() }
            val s = status
            val c = candidate
            val models = if (c != null) {
                listOf(
                    CatalogModelState(
                        candidate = c,
                        downloadedPath = (s as? ModelStatus.LiteRtReady)?.modelPath,
                        isActive = s is ModelStatus.LiteRtReady,
                        suitability = ModelSuitability.Recommended,
                    )
                )
            } else emptyList()
            return ModelCatalogSnapshot(
                nanoStatus = if (s is ModelStatus.NanoAvailable || s is ModelStatus.NanoDownloadable || s is ModelStatus.NanoDownloading) s else ModelStatus.NanoUnavailable,
                models = models,
                activeModelId = when (s) {
                    is ModelStatus.NanoAvailable -> ModelCatalogEntries.NANO_MODEL_ID
                    is ModelStatus.LiteRtReady -> c?.id
                    else -> null
                },
                showNoAcceleratedWarning = false,
            )
        }

        override suspend fun selectActiveModel(modelId: String) {
            selectedModelIds.add(modelId)
            activeModelId = modelId
        }
    }

    private class FakeDownloader(
        var emissions: List<DownloadProgress> = emptyList(),
        var deleteResult: Boolean = true,
    ) : ModelDownloader {
        var downloadCalls: Int = 0
        var cancelCalls: Int = 0
        var deleteCalls: Int = 0
        val deletedModelIds: MutableList<String> = mutableListOf()
        val paths: MutableMap<String, String> = mutableMapOf()

        override fun download(candidate: ModelCandidate): Flow<DownloadProgress> {
            downloadCalls++
            val snapshot = emissions.toList()
            return flow {
                for (emission in snapshot) emit(emission)
            }
        }

        override fun downloadedModelPath(): String? = paths.values.firstOrNull()

        override fun downloadedModelPath(modelId: String): String? = paths[modelId]

        override fun downloadedModelPaths(): Map<String, String> = paths.toMap()

        override fun cancelDownload() {
            cancelCalls++
        }

        override fun deleteModel(): Boolean {
            deleteCalls++
            paths.clear()
            return deleteResult
        }

        override fun deleteModel(modelId: String): Boolean {
            deleteCalls++
            deletedModelIds.add(modelId)
            paths.remove(modelId)
            return deleteResult
        }
    }

    private class FakeSettings : SettingsRepository {
        var preferredBackendValue: String? = null
        var modelPathValue: String? = null
        var activeModelIdValue: String? = null
        var activeDownloadIdValue: Long? = null
        var activeDownloadModelIdValue: String? = null
        var gpuUnsupportedValue: Boolean = false
        var setBackendCalls: Int = 0
        var setModelPathCalls: Int = 0

        override fun preferredBackend(): String? = preferredBackendValue

        override fun setPreferredBackend(name: String?) {
            setBackendCalls++
            preferredBackendValue = name
        }

        override fun modelPath(): String? = modelPathValue

        override fun setModelPath(path: String?) {
            setModelPathCalls++
            modelPathValue = path
        }

        override fun activeModelId(): String? = activeModelIdValue

        override fun setActiveModelId(id: String?) {
            activeModelIdValue = id
        }

        override fun activeDownloadId(): Long? = activeDownloadIdValue

        override fun setActiveDownloadId(id: Long?) {
            activeDownloadIdValue = id
        }

        override fun activeDownloadModelId(): String? = activeDownloadModelIdValue

        override fun setActiveDownloadModelId(id: String?) {
            activeDownloadModelIdValue = id
        }

        override fun isGpuUnsupported(): Boolean = gpuUnsupportedValue

        override fun setGpuUnsupported(unsupported: Boolean) {
            gpuUnsupportedValue = unsupported
        }
    }

    private val candidate = ModelCatalogEntries.QWEN3_0_6B_INT4

    private fun vm(
        catalog: FakeCatalog,
        downloader: FakeDownloader = FakeDownloader(),
        settings: FakeSettings = FakeSettings(),
    ): ModelSetupViewModel = ModelSetupViewModel(catalog, downloader, settings)

    @Test
    fun `initial Loading then Ready with status and candidate`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val vm = vm(catalog)
        assertIs<ModelSetupUiState.Loading>(vm.state.value)
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertEquals(ModelStatus.LiteRtNotDownloaded, ready.status)
        assertEquals(candidate, ready.candidate)
        assertNull(ready.download)
    }

    @Test
    fun `refresh resets to Loading`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val vm = vm(catalog)
        advanceUntilIdle()
        assertIs<ModelSetupUiState.Ready>(vm.state.value)
        catalog.status = ModelStatus.NanoAvailable()
        catalog.suspendMillis = 5_000L
        vm.refresh()
        runCurrent()
        assertIs<ModelSetupUiState.Loading>(vm.state.value)
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertEquals(ModelStatus.NanoAvailable(), ready.status)
        assertEquals(candidate, ready.candidate)
    }

    @Test
    fun `download Running updates progress without repolling catalog status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Running(receivedBytes = 100L, totalBytes = 343_490_560L))
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        val statusCallsAfterRefresh = catalog.statusCalls
        vm.onDownload()
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        val dl = assertIs<DownloadProgress.Running>(ready.download)
        assertEquals(100L, dl.receivedBytes)
        assertEquals(ModelStatus.LiteRtNotDownloaded, ready.status)
        assertEquals(1, downloader.downloadCalls)
        assertEquals(statusCallsAfterRefresh, catalog.statusCalls)
    }

    @Test
    fun `download Paused updates progress without repolling catalog status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(
                DownloadProgress.Paused(receivedBytes = 50L, totalBytes = 343_490_560L, reason = "Waiting for Wi-Fi…")
            )
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        val statusCallsAfterRefresh = catalog.statusCalls
        vm.onDownload()
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        val dl = assertIs<DownloadProgress.Paused>(ready.download)
        assertEquals("Waiting for Wi-Fi…", dl.reason)
        assertEquals(statusCallsAfterRefresh, catalog.statusCalls)
    }

    @Test
    fun `download Done persists path and refreshes status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val settings = FakeSettings()
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Done("/data/qwen3-0.6b-int4.litertlm"))
        )
        val vm = vm(catalog, downloader, settings)
        advanceUntilIdle()
        catalog.status = ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm", candidate.id, candidate.name)
        vm.onDownload()
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        val dl = assertIs<DownloadProgress.Done>(ready.download)
        assertEquals("/data/qwen3-0.6b-int4.litertlm", dl.path)
        assertEquals(ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm", candidate.id, candidate.name), ready.status)
        assertEquals("/data/qwen3-0.6b-int4.litertlm", settings.modelPathValue)
        assertEquals(candidate.id, settings.activeModelIdValue)
        assertEquals("litertlm", settings.preferredBackendValue)
    }

    @Test
    fun `download Failed refreshes status and keeps progress`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Failed("Network error"))
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        catalog.status = ModelStatus.LiteRtNotDownloaded
        vm.onDownload()
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        val dl = assertIs<DownloadProgress.Failed>(ready.download)
        assertEquals("Network error", dl.reason)
    }

    @Test
    fun `download Cancelled clears progress and refreshes status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Cancelled)
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        vm.onDownload()
        advanceUntilIdle()
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertNull(ready.download)
        assertEquals(ModelStatus.LiteRtNotDownloaded, ready.status)
    }

    @Test
    fun `cancel download delegates and refreshes`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Running(receivedBytes = 10L, totalBytes = 343_490_560L))
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        vm.onDownload()
        advanceUntilIdle()
        assertIs<ModelSetupUiState.Ready>(vm.state.value)
        vm.onCancelDownload()
        advanceUntilIdle()
        assertEquals(1, downloader.cancelCalls)
        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertNull(ready.download)
    }

    @Test
    fun `delete model delegates and clears preferred backend`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm"), candidate)
        val downloader = FakeDownloader()
        val settings = FakeSettings()
        settings.preferredBackendValue = "litertlm"
        val vm = vm(catalog, downloader, settings)
        advanceUntilIdle()
        vm.onDeleteModel()
        advanceUntilIdle()
        assertEquals(1, downloader.deleteCalls)
        assertNull(settings.preferredBackendValue)
        assertEquals(1, settings.setBackendCalls)
        assertIs<ModelSetupUiState.Ready>(vm.state.value)
    }

    @Test
    fun `incompatible model download is blocked`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.NanoUnavailable, ModelCatalogEntries.QWEN3_0_6B_INT4)
        catalog.snapshotOverride = {
            ModelCatalogSnapshot(
                nanoStatus = ModelStatus.NanoUnavailable,
                models = listOf(
                    CatalogModelState(
                        candidate = ModelCatalogEntries.QWEN3_0_6B_INT4,
                        downloadedPath = null,
                        isActive = false,
                        suitability = ModelSuitability.CpuOnlySlow("CPU only"),
                    ),
                    CatalogModelState(
                        candidate = ModelCatalogEntries.GEMMA_4_E4B,
                        downloadedPath = null,
                        isActive = false,
                        suitability = ModelSuitability.Incompatible("Requires ~10.5 GB RAM"),
                    ),
                ),
                activeModelId = null,
                showNoAcceleratedWarning = true,
            )
        }
        val downloader = FakeDownloader()
        val vm = vm(catalog, downloader)
        advanceUntilIdle()

        val ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertTrue(ready.showNoAcceleratedWarning)
        assertEquals(2, ready.models.size)

        // Attempt to download Incompatible Gemma 4 E4B — must be ignored.
        vm.onDownload(ModelCatalogEntries.GEMMA_4_E4B.id)
        advanceUntilIdle()
        assertEquals(0, downloader.downloadCalls)

        // Compatible CpuOnlySlow Qwen3 0.6B INT4 IS downloadable.
        vm.onDownload(ModelCatalogEntries.QWEN3_0_6B_INT4.id)
        advanceUntilIdle()
        assertEquals(1, downloader.downloadCalls)
    }

    @Test
    fun `switching active model between downloaded models and Gemini Nano updates state`() = runTest(dispatcher) {
        var currentActive = ModelCatalogEntries.QWEN3_0_6B_INT4.id
        val catalog = FakeCatalog(ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm"), ModelCatalogEntries.QWEN3_0_6B_INT4)
        catalog.snapshotOverride = {
            ModelCatalogSnapshot(
                nanoStatus = ModelStatus.NanoAvailable(),
                models = listOf(
                    CatalogModelState(
                        candidate = ModelCatalogEntries.QWEN3_0_6B_INT4,
                        downloadedPath = "/data/qwen3-0.6b-int4.litertlm",
                        isActive = currentActive == ModelCatalogEntries.QWEN3_0_6B_INT4.id,
                        suitability = ModelSuitability.Recommended,
                    ),
                    CatalogModelState(
                        candidate = ModelCatalogEntries.QWEN3_1_7B_INT4,
                        downloadedPath = "/data/qwen3-1.7b-int4.litertlm",
                        isActive = currentActive == ModelCatalogEntries.QWEN3_1_7B_INT4.id,
                        suitability = ModelSuitability.Recommended,
                    ),
                ),
                activeModelId = currentActive,
                showNoAcceleratedWarning = false,
            )
        }
        val vm = vm(catalog)
        advanceUntilIdle()

        // Switch to 1.7B
        currentActive = ModelCatalogEntries.QWEN3_1_7B_INT4.id
        catalog.status = ModelStatus.LiteRtReady("/data/qwen3-1.7b-int4.litertlm", currentActive, "Qwen3 1.7B INT4")
        vm.onSelectActiveModel(currentActive)
        advanceUntilIdle()
        var ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertEquals(ModelCatalogEntries.QWEN3_1_7B_INT4.id, ready.activeModelId)

        // Switch back to Gemini Nano
        currentActive = ModelCatalogEntries.NANO_MODEL_ID
        catalog.status = ModelStatus.NanoAvailable()
        vm.onSelectActiveModel(ModelCatalogEntries.NANO_MODEL_ID)
        advanceUntilIdle()
        ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertEquals(ModelCatalogEntries.NANO_MODEL_ID, ready.activeModelId)
        assertEquals(ModelStatus.NanoAvailable(), ready.status)
        assertEquals(
            listOf(ModelCatalogEntries.QWEN3_1_7B_INT4.id, ModelCatalogEntries.NANO_MODEL_ID),
            catalog.selectedModelIds,
        )
    }

    @Test
    fun `delete confirmation dialog preserves model on dismiss and deletes only target on confirm`() = runTest(dispatcher) {
        val downloader = FakeDownloader()
        downloader.paths[ModelCatalogEntries.QWEN3_0_6B_INT4.id] = "/data/qwen3-0.6b-int4.litertlm"
        downloader.paths[ModelCatalogEntries.QWEN3_1_7B_INT4.id] = "/data/qwen3-1.7b-int4.litertlm"

        val catalog = FakeCatalog(ModelStatus.LiteRtReady("/data/qwen3-0.6b-int4.litertlm"), ModelCatalogEntries.QWEN3_0_6B_INT4)
        catalog.snapshotOverride = {
            ModelCatalogSnapshot(
                nanoStatus = ModelStatus.NanoUnavailable,
                models = listOf(
                    CatalogModelState(
                        candidate = ModelCatalogEntries.QWEN3_0_6B_INT4,
                        downloadedPath = downloader.paths[ModelCatalogEntries.QWEN3_0_6B_INT4.id],
                        isActive = ModelCatalogEntries.QWEN3_0_6B_INT4.id in downloader.paths,
                        suitability = ModelSuitability.Recommended,
                    ),
                    CatalogModelState(
                        candidate = ModelCatalogEntries.QWEN3_1_7B_INT4,
                        downloadedPath = downloader.paths[ModelCatalogEntries.QWEN3_1_7B_INT4.id],
                        isActive = false,
                        suitability = ModelSuitability.Recommended,
                    ),
                ),
                activeModelId = if (ModelCatalogEntries.QWEN3_0_6B_INT4.id in downloader.paths) ModelCatalogEntries.QWEN3_0_6B_INT4.id else ModelCatalogEntries.QWEN3_1_7B_INT4.id,
                showNoAcceleratedWarning = false,
            )
        }
        val vm = vm(catalog, downloader)
        advanceUntilIdle()

        vm.onRequestDeleteModel(ModelCatalogEntries.QWEN3_1_7B_INT4.id)
        var ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertEquals(ModelCatalogEntries.QWEN3_1_7B_INT4, ready.pendingDeleteCandidate)

        // Dismiss preserves file
        vm.onDismissDeleteDialog()
        ready = assertIs<ModelSetupUiState.Ready>(vm.state.value)
        assertNull(ready.pendingDeleteCandidate)
        assertEquals(0, downloader.deleteCalls)

        // Request and confirm deletes only target
        vm.onRequestDeleteModel(ModelCatalogEntries.QWEN3_1_7B_INT4.id)
        vm.onConfirmDeleteModel()
        advanceUntilIdle()
        assertEquals(listOf(ModelCatalogEntries.QWEN3_1_7B_INT4.id), downloader.deletedModelIds)
        assertTrue(ModelCatalogEntries.QWEN3_0_6B_INT4.id in downloader.paths)
    }
}
