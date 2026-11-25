package de.franzsw.ctx2cal.core.di

import de.franzsw.ctx2cal.extract.presentation.ExtractViewModel
import de.franzsw.ctx2cal.setup.presentation.ModelSetupViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Shared application module: ViewModels over the platform-provided ports. */
val appModule: Module = module {
    viewModel { ExtractViewModel(get(), get(), get(), get(), get()) }
    viewModel { ModelSetupViewModel(get(), get(), get()) }
}
