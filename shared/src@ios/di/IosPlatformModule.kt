package de.franzsw.ctx2cal.di

import de.franzsw.ctx2cal.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.domain.ports.EventExtractor
import de.franzsw.ctx2cal.domain.ports.IcsExporter
import de.franzsw.ctx2cal.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.domain.ports.SettingsRepository
import de.franzsw.ctx2cal.platform.UnsupportedCalendarSharer
import de.franzsw.ctx2cal.platform.UnsupportedExtractor
import de.franzsw.ctx2cal.platform.UnsupportedIcsExporter
import de.franzsw.ctx2cal.platform.UnsupportedModelCatalog
import de.franzsw.ctx2cal.platform.UnsupportedDownloader
import de.franzsw.ctx2cal.platform.UnsupportedSettings
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
