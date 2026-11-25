package com.example.ctx2cal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.example.ctx2cal.di.appModule
import com.example.ctx2cal.di.platformModule
import com.example.ctx2cal.navigation.ExtractRoute
import com.example.ctx2cal.navigation.ModelSetup
import com.example.ctx2cal.presentation.extract.ExtractScreen
import com.example.ctx2cal.presentation.extract.ExtractViewModel
import com.example.ctx2cal.presentation.setup.ModelSetupScreen
import com.example.ctx2cal.presentation.setup.ModelSetupViewModel
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    MaterialTheme {
        KoinApplication(
            application = {
                modules(appModule, platformModule)
            }
        ) {
            val config = remember {
                SavedStateConfiguration {
                    serializersModule = SerializersModule {
                        // NavBackStack serializes as NavKey, not Route — register under NavKey.
                        polymorphic(NavKey::class, ExtractRoute::class, ExtractRoute.serializer())
                        polymorphic(NavKey::class, ModelSetup::class, ModelSetup.serializer())
                    }
                }
            }
            val backStack = rememberNavBackStack(config, ExtractRoute)
            val snackbarHost = remember { SnackbarHostState() }
            Scaffold(
                topBar = {
                    val current = backStack.lastOrNull()
                    val title = if (current is ModelSetup) "Model setup" else "Extract event"
                    TopAppBar(
                        title = { Text(title) },
                        actions = {
                            TextButton(onClick = {
                                if (backStack.lastOrNull() is ExtractRoute) backStack.add(ModelSetup)
                            }) { Text("Setup") }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHost) },
            ) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize(),
                    entryProvider = entryProvider {
                        entry<ExtractRoute> {
                            val vm: ExtractViewModel = koinViewModel()
                            val state = vm.state.collectAsState().value
                            Column {
                                ExtractScreen(
                                    state = state,
                                    onTextChange = vm::onTextChange,
                                    onExtract = vm::onExtract,
                                    onDraftChanged = vm::onDraftChanged,
                                    onShare = vm::onShare,
                                    onDownload = vm::onDownload,
                                    onBackToInput = vm::onBackToInput,
                                    onOpenSetup = { backStack.add(ModelSetup) },
                                )
                            }
                        }
                        entry<ModelSetup> {
                            val vm: ModelSetupViewModel = koinViewModel()
                            val state = vm.state.collectAsState().value
                            ModelSetupScreen(
                                state = state,
                                onDownload = vm::onDownload,
                                onRetry = vm::refresh,
                            )
                        }
                    },
                )
            }
        }
    }
}
