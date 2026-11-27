# Proposal

## Why

Currently, the extraction system prompt is hardcoded in `EventJsonParser.buildSystemInstruction()`, and there is no way to inspect or experiment with how individual on-device models (Gemini Nano or downloaded `.litertlm` models) respond to prompts without running the full calendar-event extraction pipeline. Making the system prompt editable in Setup and adding a lightweight per-model test screen ("LLM shed") lets users and developers tune extraction behavior and quickly verify any ready model directly on-device.

## What Changes

- Add an editable global **System prompt** section to `ModelSetupScreen` backed by `SettingsRepository`, with a **Reset to default** action that restores `EventJsonParser.buildSystemInstruction()`.
- Update event extraction (`ExtractViewModel`, `EventJsonParser`, and extractors) to use the persisted custom system prompt when set, falling back to the default system instruction otherwise.
- Add a **Test** button on each ready/available model in `ModelSetupScreen` (both `SystemModelSection` when Gemini Nano is available and `CatalogModelCard` when a catalog model is downloaded).
- Add a new `ModelTest` route and `ModelTestScreen` / `ModelTestViewModel` where users can edit a **System prompt** (pre-filled with the active Setup system prompt) and a **User prompt**, run raw inference against that specific model only (no JSON validation, retry, or cross-model fallback), cancel an in-flight run, and inspect the raw output or error.

## Capabilities

### New Capabilities
- `model-test-playground`: Lightweight on-device LLM test screen launched per ready model from Setup, supporting editable system and user prompts, raw single-turn inference on the selected model, cancellation, and raw output/error display.

### Modified Capabilities
- `model-setup`: Add an editable global system prompt with Reset to default, and a per-model Test action on each available/downloaded model.
- `event-extraction`: Use the persisted custom system prompt (when non-blank/configured) instead of the hardcoded default system instruction for extraction.

## Impact

- `shared/src/core/domain/repository/SettingsRepository.kt` and `shared/src@android/core/data/settings/AndroidSettingsRepository.kt`: persist optional custom system prompt.
- `shared/src/core/domain/ports/Ports.kt`, `shared/src@android/core/data/llm/GeminiNanoExtractor.kt`, `shared/src@android/core/data/llm/LiteRtExtractor.kt`: expose raw single-turn prompt execution (system + user prompt) for a specific model/backend, and support custom system instructions during extraction.
- `shared/src/extract/domain/EventJsonParser.kt` & `shared/src/extract/presentation/ExtractViewModel.kt`: pass/use custom system prompt when configured.
- `shared/src/setup/presentation/ModelSetupViewModel.kt` & `ModelSetupScreen.kt`: system prompt editor, Reset to default, and per-model Test button.
- `shared/src/core/navigation/Routes.kt`, `shared/src/App.kt`, `shared/src/core/di/appModule.kt`, and new `shared/src/test_playground/` (or `setup/presentation/`) `ModelTestViewModel` & `ModelTestScreen`.
