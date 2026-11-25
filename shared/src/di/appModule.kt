package com.example.ctx2cal.di

import com.example.ctx2cal.presentation.extract.ExtractViewModel
import com.example.ctx2cal.presentation.setup.ModelSetupViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Shared application module: ViewModels over the platform-provided ports. */
val appModule: Module = module {
    viewModel { ExtractViewModel(get(), get(), get()) }
    viewModel { ModelSetupViewModel(get(), get(), get()) }
}
