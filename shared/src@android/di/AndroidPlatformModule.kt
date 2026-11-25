package de.franzsw.ctx2cal.di

import de.franzsw.ctx2cal.data.calendar.AndroidCalendarSharer
import de.franzsw.ctx2cal.data.ics.AndroidIcsExporter
import de.franzsw.ctx2cal.data.llm.AndroidModelCatalog
import de.franzsw.ctx2cal.data.llm.GeminiNanoExtractor
import de.franzsw.ctx2cal.data.llm.LiteRtExtractor
import de.franzsw.ctx2cal.data.llm.ModelDownloaderAndroid
import de.franzsw.ctx2cal.data.settings.AndroidSettingsRepository
import de.franzsw.ctx2cal.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.domain.ports.EventExtractor
import de.franzsw.ctx2cal.domain.ports.IcsExporter
import de.franzsw.ctx2cal.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.domain.ports.SettingsRepository
import de.franzsw.ctx2cal.domain.routing.ExtractorRouter
import de.franzsw.ctx2cal.platform.PlatformContextHolder
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android DI overlay: real LLM/download/calendar/ICS beans. */
private fun androidContext(): android.content.Context =
    PlatformContextHolder.value as android.content.Context

actual val platformModule: Module = module {
    single<SettingsRepository> { AndroidSettingsRepository(androidContext()) }
    single<ModelDownloader> { ModelDownloaderAndroid(androidContext()) }
    single { GeminiNanoExtractor() }
    single { LiteRtExtractor(modelPathProvider = { get<SettingsRepository>().modelPath() ?: get<ModelDownloader>().downloadedModelPath() }) }
    single<ModelCatalog> { AndroidModelCatalog(get<GeminiNanoExtractor>(), get<ModelDownloader>(), get<SettingsRepository>()) }
    // Both backends implement EventExtractor, so bare get() would self-resolve — pin concrete types.
    single<EventExtractor> { ExtractorRouter(get<GeminiNanoExtractor>(), get<LiteRtExtractor>(), get<ModelCatalog>()) }
    single<CalendarSharer> { AndroidCalendarSharer(androidContext()) }
    single<IcsExporter> { AndroidIcsExporter(androidContext()) }
}
