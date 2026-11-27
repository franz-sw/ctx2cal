package de.franzsw.ctx2cal.core.di

import de.franzsw.ctx2cal.core.data.calendar.AndroidCalendarSharer
import de.franzsw.ctx2cal.core.data.ics.AndroidIcsExporter
import de.franzsw.ctx2cal.core.data.llm.AndroidDeviceCapabilitiesProvider
import de.franzsw.ctx2cal.core.data.llm.AndroidModelCatalog
import de.franzsw.ctx2cal.core.data.llm.AndroidModelTester
import de.franzsw.ctx2cal.core.data.llm.GeminiNanoExtractor
import de.franzsw.ctx2cal.core.data.llm.LiteRtExtractor
import de.franzsw.ctx2cal.core.data.llm.ModelDownloaderAndroid
import de.franzsw.ctx2cal.core.data.settings.AndroidSettingsRepository
import de.franzsw.ctx2cal.core.domain.catalog.ModelSuitabilityEvaluator
import de.franzsw.ctx2cal.core.domain.model.ModelCatalogEntries
import de.franzsw.ctx2cal.core.domain.model.ModelSuitability
import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.DeviceCapabilitiesProvider
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.ports.ModelTester
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.core.domain.routing.ExtractorRouter
import de.franzsw.ctx2cal.core.platform.PlatformContextHolder
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android DI overlay: real LLM/download/calendar/ICS beans. */
private fun androidContext(): android.content.Context =
    PlatformContextHolder.value as android.content.Context

actual val platformModule: Module = module {
    single<SettingsRepository> { AndroidSettingsRepository(androidContext()) }
    single<DeviceCapabilitiesProvider> { AndroidDeviceCapabilitiesProvider(androidContext(), get<SettingsRepository>()) }
    single<ModelDownloader> { ModelDownloaderAndroid(androidContext(), get<SettingsRepository>()) }
    single { GeminiNanoExtractor(settings = get<SettingsRepository>()) }
    single {
        LiteRtExtractor(
            modelPathProvider = {
                val settings = get<SettingsRepository>()
                val downloader = get<ModelDownloader>()
                val activeId = settings.activeModelId()
                if (activeId != null && activeId != ModelCatalogEntries.NANO_MODEL_ID) {
                    downloader.downloadedModelPath(activeId) ?: downloader.downloadedModelPath()
                } else {
                    settings.modelPath() ?: downloader.downloadedModelPath()
                }
            },
            settings = get<SettingsRepository>(),
            skipGpuHint = {
                val caps = get<DeviceCapabilitiesProvider>().getCapabilities()
                ModelSuitabilityEvaluator.evaluate(
                    ModelCatalogEntries.QWEN3_0_6B_INT4,
                    caps,
                    isDownloaded = true,
                ) is ModelSuitability.CpuOnlySlow
            },
        )
    }
    single<ModelCatalog> {
        AndroidModelCatalog(
            nano = get<GeminiNanoExtractor>(),
            downloader = get<ModelDownloader>(),
            settings = get<SettingsRepository>(),
            capabilitiesProvider = get<DeviceCapabilitiesProvider>(),
        )
    }
    // Both backends implement EventExtractor, so bare get() would self-resolve — pin concrete types.
    single<EventExtractor> { ExtractorRouter(get<GeminiNanoExtractor>(), get<LiteRtExtractor>(), get<ModelCatalog>()) }
    single<ModelTester> { AndroidModelTester(get<GeminiNanoExtractor>(), get<LiteRtExtractor>(), get<ModelDownloader>()) }
    single<CalendarSharer> { AndroidCalendarSharer(androidContext()) }
    single<IcsExporter> { AndroidIcsExporter(androidContext()) }
}
