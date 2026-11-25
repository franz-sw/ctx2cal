package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.model.ModelCandidate
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
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
    ) : ModelCatalog {
        var statusCalls: Int = 0
        var candidateCalls: Int = 0

        override suspend fun currentStatus(): ModelStatus {
            statusCalls++
            if (suspendMillis > 0L) delay(suspendMillis)
            return status
        }

        override suspend fun availableCandidate(): ModelCandidate? {
            candidateCalls++
            return candidate
        }
    }

    private class FakeDownloader(
        var emissions: List<DownloadProgress> = emptyList(),
        var deleteResult: Boolean = true,
    ) : ModelDownloader {
        var downloadCalls: Int = 0
        var cancelCalls: Int = 0
        var deleteCalls: Int = 0

        override fun download(candidate: ModelCandidate): Flow<DownloadProgress> {
            downloadCalls++
            val snapshot = emissions.toList()
            return flow {
                for (emission in snapshot) emit(emission)
            }
        }

        override fun downloadedModelPath(): String? = null

        override fun cancelDownload() {
            cancelCalls++
        }

        override fun deleteModel(): Boolean {
            deleteCalls++
            return deleteResult
        }
    }

    private class FakeSettings : SettingsRepository {
        var preferredBackendValue: String? = null
        var modelPathValue: String? = null
        var activeDownloadIdValue: Long? = null
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

        override fun activeDownloadId(): Long? = activeDownloadIdValue

        override fun setActiveDownloadId(id: Long?) {
            activeDownloadIdValue = id
        }
    }

    private val candidate = ModelCandidate(
        id = "qwen3-0.6b-int4",
        name = "Qwen3 0.6B INT4",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        approxSizeBytes = 343_490_560L,
        minRamMb = 3_584,
    )

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
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertIs<ModelSetupUiState.Ready>(ready)
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
        catalog.status = ModelStatus.NanoAvailable
        catalog.suspendMillis = 5_000L
        vm.refresh()
        runCurrent()
        assertIs<ModelSetupUiState.Loading>(vm.state.value)
        advanceUntilIdle()
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertEquals(ModelStatus.NanoAvailable, ready.status)
        assertEquals(candidate, ready.candidate)
    }

    @Test
    fun `download Running updates progress without repolling catalog status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Running(receivedBytes = 100L, totalBytes = 380_000_000L))
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        val statusCallsAfterRefresh = catalog.statusCalls
        vm.onDownload()
        advanceUntilIdle()
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertIs<DownloadProgress.Running>(ready.download)
        assertEquals(100L, (ready.download as DownloadProgress.Running).receivedBytes)
        assertEquals(ModelStatus.LiteRtNotDownloaded, ready.status)
        assertEquals(1, downloader.downloadCalls)
        assertEquals(statusCallsAfterRefresh, catalog.statusCalls)
    }

    @Test
    fun `download Paused updates progress without repolling catalog status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(
                DownloadProgress.Paused(receivedBytes = 50L, totalBytes = 380_000_000L, reason = "Waiting for Wi-Fi…")
            )
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        val statusCallsAfterRefresh = catalog.statusCalls
        vm.onDownload()
        advanceUntilIdle()
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertIs<DownloadProgress.Paused>(ready.download)
        assertEquals("Waiting for Wi-Fi…", (ready.download as DownloadProgress.Paused).reason)
        assertEquals(statusCallsAfterRefresh, catalog.statusCalls)
    }

    @Test
    fun `download Done persists path and refreshes status`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val settings = FakeSettings()
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Done("/data/model.litertlm"))
        )
        val vm = vm(catalog, downloader, settings)
        advanceUntilIdle()
        catalog.status = ModelStatus.LiteRtReady("/data/model.litertlm")
        vm.onDownload()
        advanceUntilIdle()
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertIs<DownloadProgress.Done>(ready.download)
        assertEquals("/data/model.litertlm", (ready.download as DownloadProgress.Done).path)
        assertEquals(ModelStatus.LiteRtReady("/data/model.litertlm"), ready.status)
        assertEquals("/data/model.litertlm", settings.modelPathValue)
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
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertIs<DownloadProgress.Failed>(ready.download)
        assertEquals("Network error", (ready.download as DownloadProgress.Failed).reason)
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
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertNull(ready.download)
        assertEquals(ModelStatus.LiteRtNotDownloaded, ready.status)
    }

    @Test
    fun `cancel download delegates and refreshes`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtNotDownloaded, candidate)
        val downloader = FakeDownloader(
            emissions = listOf(DownloadProgress.Running(receivedBytes = 10L, totalBytes = 380_000_000L))
        )
        val vm = vm(catalog, downloader)
        advanceUntilIdle()
        vm.onDownload()
        advanceUntilIdle()
        assertIs<ModelSetupUiState.Ready>(vm.state.value)
        vm.onCancelDownload()
        advanceUntilIdle()
        assertEquals(1, downloader.cancelCalls)
        val ready = vm.state.value as ModelSetupUiState.Ready
        assertNull(ready.download)
    }

    @Test
    fun `delete model delegates and clears preferred backend`() = runTest(dispatcher) {
        val catalog = FakeCatalog(ModelStatus.LiteRtReady("/data/model.litertlm"), candidate)
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
}
