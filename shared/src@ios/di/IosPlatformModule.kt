package com.example.ctx2cal.di

import com.example.ctx2cal.domain.ports.CalendarSharer
import com.example.ctx2cal.domain.ports.EventExtractor
import com.example.ctx2cal.domain.ports.IcsExporter
import com.example.ctx2cal.domain.ports.ModelCatalog
import com.example.ctx2cal.domain.ports.ModelDownloader
import com.example.ctx2cal.domain.ports.SettingsRepository
import com.example.ctx2cal.platform.UnsupportedCalendarSharer
import com.example.ctx2cal.platform.UnsupportedExtractor
import com.example.ctx2cal.platform.UnsupportedIcsExporter
import com.example.ctx2cal.platform.UnsupportedModelCatalog
import com.example.ctx2cal.platform.UnsupportedDownloader
import com.example.ctx2cal.platform.UnsupportedSettings
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS DI overlay: unsupported stubs — the UI degrades to a no-model error. */
actual val platformModule: Module = module {
    single<SettingsRepository> { UnsupportedSettings() }
    single<ModelDownloader> { UnsupportedDownloader() }
    single<ModelCatalog> { UnsupportedModelCatalog() }
    single<EventExtractor> { UnsupportedExtractor() }
    single<CalendarSharer> { UnsupportedCalendarSharer() }
    single<IcsExporter> { UnsupportedIcsExporter() }
}
