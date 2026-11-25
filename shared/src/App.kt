package de.franzsw.ctx2cal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import de.franzsw.ctx2cal.core.di.appModule
import de.franzsw.ctx2cal.core.di.platformModule
import de.franzsw.ctx2cal.core.navigation.ExtractRoute
import de.franzsw.ctx2cal.core.navigation.ModelSetup
import de.franzsw.ctx2cal.core.presentation.design_system.theme.AppTheme
import de.franzsw.ctx2cal.extract.presentation.ExtractScreen
import de.franzsw.ctx2cal.extract.presentation.ExtractUiState
import de.franzsw.ctx2cal.extract.presentation.ExtractViewModel
import de.franzsw.ctx2cal.setup.presentation.ModelSetupScreen
import de.franzsw.ctx2cal.setup.presentation.ModelSetupViewModel
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@DevelopmentEntryPoint
fun App(
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
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
            val currentRoute = backStack.lastOrNull()

            Scaffold(
                topBar = {
                    val title = if (currentRoute is ModelSetup) "Model setup" else "ContextToCalendar"
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            if (backStack.size > 1) {
                                TextButton(onClick = { backStack.removeLastOrNull() }) {
                                    Text("← Back")
                                }
                            }
                        },
                        actions = {
                            if (currentRoute is ExtractRoute) {
                                TextButton(onClick = { backStack.add(ModelSetup) }) {
                                    Text("Setup")
                                }
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHost) },
            ) { innerPadding ->
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    entryProvider = entryProvider {
                        entry<ExtractRoute> {
                            val vm: ExtractViewModel = koinViewModel()
                            val state = vm.state.collectAsState().value
                            val modelStatus = vm.modelStatus.collectAsState().value

                            LaunchedEffect(currentRoute) {
                                if (currentRoute is ExtractRoute) {
                                    vm.refreshModelStatus()
                                }
                            }

                            LaunchedEffect(sharedText) {
                                if (!sharedText.isNullOrBlank()) {
                                    vm.onReceiveSharedText(sharedText)
                                    onSharedTextConsumed()
                                }
                            }

                            val reviewNotice = (state as? ExtractUiState.Review)
                                ?.takeIf { !it.noticeIsError }
                                ?.notice
                            LaunchedEffect(reviewNotice) {
                                if (reviewNotice != null) {
                                    snackbarHost.showSnackbar(reviewNotice)
                                }
                            }

                            ExtractScreen(
                                state = state,
                                modelStatus = modelStatus,
                                onTextChange = vm::onTextChange,
                                onExtract = vm::onExtract,
                                onCancelExtract = vm::onCancelExtract,
                                onClearText = vm::onClearText,
                                onDismissNotice = vm::onDismissNotice,
                                onDraftChanged = vm::onDraftChanged,
                                onShare = vm::onShare,
                                onDownload = vm::onDownload,
                                onBackToInput = vm::onBackToInput,
                                onOpenSetup = {
                                    if (backStack.lastOrNull() !is ModelSetup) backStack.add(ModelSetup)
                                },
                            )
                        }
                        entry<ModelSetup> {
                            val vm: ModelSetupViewModel = koinViewModel()
                            val state = vm.state.collectAsState().value
                            ModelSetupScreen(
                                state = state,
                                onDownload = vm::onDownload,
                                onRetry = vm::refresh,
                                onCancelDownload = vm::onCancelDownload,
                                onSelectActiveModel = vm::onSelectActiveModel,
                                onRequestDeleteModel = vm::onRequestDeleteModel,
                                onConfirmDeleteModel = vm::onConfirmDeleteModel,
                                onDismissDeleteDialog = vm::onDismissDeleteDialog,
                                onDone = {
                                    if (backStack.size > 1) backStack.removeLastOrNull()
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
