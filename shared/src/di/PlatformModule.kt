package com.example.ctx2cal.di

import org.koin.dsl.module

/** Platform overlay: real Android beans (see `androidPlatformModule`). */
expect val platformModule: org.koin.core.module.Module
