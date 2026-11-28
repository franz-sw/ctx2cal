# Tasks

## 1. Custom System Prompt Persistence & Extraction Wiring

- [x] 1.1 Add `customSystemPrompt(): String?` and `setCustomSystemPrompt(prompt: String?)` to `SettingsRepository` and implement SharedPreferences persistence in `AndroidSettingsRepository`; verify with unit tests or `.\kotlin.bat check`.
- [x] 1.2 Update `EventJsonParser` (`buildPromptParts`, `buildPrompt`, `splitForConversation`) to accept an optional `systemInstruction: String?`, update `ExtractViewModel` and `LiteRtExtractor` to use `SettingsRepository.customSystemPrompt()` when non-blank, and verify with `EventJsonParser` and `ExtractViewModel` unit tests.

## 2. Raw Model Testing Port & Android Backends

- [x] 2.1 Add `RawPromptResult` and `ModelTester` interface to `shared/src/core/domain/ports/Ports.kt` and provide an unsupported default/stub in iOS/common DI so all targets compile under `.\kotlin.bat check`.
- [x] 2.2 Add `generateRaw(...)` to `GeminiNanoExtractor` and `LiteRtExtractor` (single-turn, no JSON parsing or retry, enforcing timeout and native `cancelProcess()` on cancel/timeout), and implement `AndroidModelTester` routing directly by `modelId` without fallback; wire into `AndroidPlatformModule` and verify compilation and unit tests.

## 3. Setup Screen System Prompt Editor & Per-Model Test Button

- [x] 3.1 Add `systemPrompt: String` and `isDefaultSystemPrompt: Boolean` to `ModelSetupUiState.Ready` plus `onSystemPromptChange(String)` and `onResetSystemPrompt()` in `ModelSetupViewModel`; add unit tests covering edit persistence and reset to default.
- [x] 3.2 Update `ModelSetupScreen` to render the editable System Prompt card with a **Reset to default** button, and add a **Test** button on `SystemModelSection` (when `NanoAvailable`) and `CatalogModelCard` (when `entry.isDownloaded`) that invokes `onTestModel(modelId, modelName)`.

## 4. Model Test Playground Screen & Navigation

- [x] 4.1 Add `@Serializable data class ModelTest(val modelId: String, val modelName: String) : Route` to `Routes.kt`, register it in `App.kt` under `NavKey` polymorphism, and wire navigation from `ModelSetup` to `ModelTest` with top-bar title and back navigation.
- [x] 4.2 Implement `ModelTestViewModel` (pre-filling `systemPrompt` from `SettingsRepository.customSystemPrompt()` or `EventJsonParser.buildSystemInstruction()`, supporting `onRun()`, `onCancel()`, and raw output/duration/error state) and register it in `appModule.kt`; verify with `ModelTestViewModel` unit tests covering prefill, run success, failure, and cancellation.
- [x] 4.3 Implement `ModelTestScreen` with target model header, editable System prompt and User prompt fields, Run/Cancel action, and raw output / error display; verify full project build and test suite with `.\kotlin.bat check`.
