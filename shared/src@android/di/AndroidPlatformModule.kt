package com.example.ctx2cal.di

import com.example.ctx2cal.data.calendar.AndroidCalendarSharer
import com.example.ctx2cal.data.ics.AndroidIcsExporter
import com.example.ctx2cal.data.llm.AndroidModelCatalog
import com.example.ctx2cal.data.llm.GeminiNanoExtractor
import com.example.ctx2cal.data.llm.LiteRtExtractor
import com.example.ctx2cal.data.llm.ModelDownloaderAndroid
import com.example.ctx2cal.data.settings.AndroidSettingsRepository
import com.example.ctx2cal.domain.ports.CalendarSharer
import com.example.ctx2cal.domain.ports.EventExtractor
import com.example.ctx2cal.domain.ports.IcsExporter
import com.example.ctx2cal.domain.ports.ModelCatalog
import com.example.ctx2cal.domain.ports.ModelDownloader
import com.example.ctx2cal.domain.ports.SettingsRepository
import com.example.ctx2cal.domain.routing.ExtractorRouter
import com.example.ctx2cal.platform.PlatformContextHolder
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
    single<ModelCatalog> { AndroidModelCatalog(get(), get(), get()) }
    single<EventExtractor> { ExtractorRouter(get(), get(), get()) }
    single<CalendarSharer> { AndroidCalendarSharer(androidContext()) }
    single<IcsExporter> { AndroidIcsExporter(androidContext()) }
}
