package de.franzsw.ctx2cal.core.di

import de.franzsw.ctx2cal.extract.presentation.ExtractViewModel
import de.franzsw.ctx2cal.setup.presentation.ModelSetupViewModel
import de.franzsw.ctx2cal.test_playground.presentation.ModelTestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Shared application module: ViewModels over the platform-provided ports. */
val appModule: Module = module {
    viewModel { ExtractViewModel(get(), get(), get(), get(), get(), settings = get()) }
    viewModel { ModelSetupViewModel(get(), get(), get()) }
    viewModel { (modelId: String, modelName: String) ->
        ModelTestViewModel(
            modelId = modelId,
            modelName = modelName,
            modelTester = get(),
            settings = get(),
        )
    }
}
