package com.example.ctx2cal.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object ExtractRoute : Route

@Serializable
data object ModelSetup : Route
