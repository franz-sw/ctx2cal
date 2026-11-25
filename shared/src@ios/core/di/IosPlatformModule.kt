package de.franzsw.ctx2cal.core.di

import de.franzsw.ctx2cal.core.domain.ports.CalendarSharer
import de.franzsw.ctx2cal.core.domain.ports.EventExtractor
import de.franzsw.ctx2cal.core.domain.ports.IcsExporter
import de.franzsw.ctx2cal.core.domain.ports.ModelCatalog
import de.franzsw.ctx2cal.core.domain.ports.ModelDownloader
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import de.franzsw.ctx2cal.core.platform.UnsupportedCalendarSharer
import de.franzsw.ctx2cal.core.platform.UnsupportedExtractor
import de.franzsw.ctx2cal.core.platform.UnsupportedIcsExporter
import de.franzsw.ctx2cal.core.platform.UnsupportedModelCatalog
import de.franzsw.ctx2cal.core.platform.UnsupportedDownloader
import de.franzsw.ctx2cal.core.platform.UnsupportedSettings
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
