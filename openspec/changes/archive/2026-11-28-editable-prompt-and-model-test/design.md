# Design

## Context

See `proposal.md` for motivation. In the current codebase:
- `EventJsonParser.buildSystemInstruction()` returns the hardcoded system prompt, and `EventJsonParser.splitForConversation(prompt)` splits a combined prompt by matching that exact hardcoded prefix.
- `SettingsRepository` / `AndroidSettingsRepository` stores `preferredBackend`, `modelPath`, `activeModelId`, `activeDownloadId`, `activeDownloadModelId`, and `gpuUnsupported`.
- `EventExtractor` exposes a single `suspend fun extract(prompt: String): ExtractionResult` implemented by `GeminiNanoExtractor`, `LiteRtExtractor`, and `ExtractorRouter`.
- Navigation 3 in `Routes.kt` and `App.kt` registers `ExtractRoute` and `ModelSetup` under `NavKey` polymorphism.

## Goals / Non-Goals

**Goals:**
- Persist an optional global custom system prompt in `SettingsRepository` and wire it cleanly into both `ModelSetupScreen` and event extraction without breaking `EventJsonParser.splitForConversation` or existing tests.
- Provide a clean, platform-agnostic `ModelTester` (or raw prompt runner) port in `shared/` with Android implementation delegating to the exact target model (`nano` or a specific downloaded `modelId`) without JSON parsing, retry, or cross-model fallback.
- Add `ModelTest(val modelId: String, val modelName: String)` to Navigation 3 routes, with its own `ModelTestViewModel` and `ModelTestScreen`.

**Non-Goals:**
- Per-model saved system prompts (system prompt in Setup is global; test-screen edits are ephemeral to that test session).
- Multi-turn chat history, streaming token output, or sampler parameter sliders in the test screen (single-turn Run + raw response keeps it a lightweight "LLM shed").
- iOS LLM backend support (iOS remains unsupported for on-device inference as in v1).

## Decisions

### 1. Custom system prompt storage and propagation
- **Decision**: Add `customSystemPrompt(): String?` and `setCustomSystemPrompt(prompt: String?)` to `SettingsRepository` (with default `null` in interface for test fakes). `EventJsonParser` adds an optional `systemInstruction: String? = null` parameter to `buildPromptParts` and `buildPrompt`, and `ExtractViewModel` reads `settings.customSystemPrompt()?.takeIf { it.isNotBlank() }` when building the prompt.
- **Handling `LiteRtExtractor` split**: Currently `LiteRtExtractor` calls `EventJsonParser.splitForConversation(prompt)`, which only recognizes the default `buildSystemInstruction()` prefix. If a custom system prompt is used, `splitForConversation` would fail to strip the custom prefix and pass the whole combined string as `userPrompt`. To fix this cleanly, `splitForConversation(prompt: String, systemInstruction: String? = null)` (or passing `settings?.customSystemPrompt()` into `splitForConversation`) will match the active system instruction prefix when present.
- **Alternative considered**: Changing `EventExtractor.extract` signature to take `(systemPrompt, userPrompt)`. Rejected to avoid breaking existing `EventExtractor` implementations/fakes and tests unnecessarily; `splitForConversation` with an optional `systemInstruction` keeps `EventExtractor` backwards-compatible.

### 2. Dedicated `ModelTester` port for raw per-model inference
- **Decision**: Introduce a small domain port `ModelTester` in `Ports.kt`:
  ```kotlin
  sealed interface RawPromptResult {
      data class Success(val output: String, val durationMs: Long) : RawPromptResult
      data class Failure(val reason: String) : RawPromptResult
  }

  interface ModelTester {
      suspend fun runPrompt(
          modelId: String,
          systemPrompt: String,
          userPrompt: String,
      ): RawPromptResult
  }
  ```
- **Routing**: On Android, `AndroidModelTester(nano: GeminiNanoExtractor, liteRt: LiteRtExtractor, downloader: ModelDownloader)` dispatches directly:
  - If `modelId == ModelCatalogEntries.NANO_MODEL_ID`: calls a new `suspend fun generateRaw(prompt: String): RawPromptResult` on `GeminiNanoExtractor` (combining `systemPrompt` and `userPrompt` if `systemPrompt` is non-blank, using `withTimeout(attemptTimeoutMs)` and foreground checks, without `EventJsonParser` or retry).
  - Otherwise: resolves the model file via `downloader.downloadedModelPath(modelId)` and calls a new `suspend fun generateRaw(modelPath: String, systemPrompt: String, userPrompt: String): RawPromptResult` on `LiteRtExtractor` (using `ConversationConfig(systemInstruction = ..., thinkingConfig = ThinkingConfig(enableThinking = false), ...)` with `MAX_OUTPUT_TOKENS` / timeout / native `cancelProcess()` on cancellation or timeout, without `EventJsonParser` or retry).
- **Alternative considered**: Reusing `ExtractorRouter` / `EventExtractor` with a special flag. Rejected because `ExtractorRouter` intentionally falls back across models and `EventExtractor` returns `CalendarEventDraft`, whereas the test screen must target one exact model and return raw text.

### 3. Navigation & ViewModel lifecycle for `ModelTest`
- **Decision**: Add `@Serializable data class ModelTest(val modelId: String, val modelName: String) : Route` to `Routes.kt` and register it in `App.kt`'s `SavedStateConfiguration` polymorphic `NavKey` serializers module. `ModelTestViewModel` takes `modelId`, `modelName`, `ModelTester`, and `SettingsRepository` (via Koin `parametersOf`), pre-filling `systemPrompt` from `settings.customSystemPrompt()?.takeIf { it.isNotBlank() } ?: EventJsonParser.buildSystemInstruction()`.
- **UI**: `TopAppBar` shows `"Test · $modelName"` (or `"Model test"`) with the existing `← Back` button. `ModelTestScreen` has:
  - Target model header chip/subtitle
  - OutlinedTextField for **System prompt** (multi-line, pre-filled, with a small "Clear" / "Reset" convenience action)
  - OutlinedTextField for **User prompt** (multi-line)
  - **Run** button (or **Cancel** while running)
  - **Output** card showing raw response text + elapsed time (or `ErrorCard` on failure).

## Risks / Trade-offs

- **Large custom system prompt in `LiteRtExtractor`**: A user-edited or test system prompt that is very long could exceed the model's context window or leave little room for output. → Mitigation: `LiteRtExtractor` already bounds `ConversationConfig` and handles engine errors/timeouts cleanly, surfacing any failure message directly on the test screen.
- **Gemini Nano single-string prompt**: ML Kit GenAI `generateContent(String)` has no separate `systemInstruction` parameter. → Mitigation: Combine `systemPrompt` and `userPrompt` with `\n\n` when `systemPrompt` is non-blank, which is consistent with how Nano extraction already works.
- **State loss on process death vs. simplicity**: `ModelTestScreen` is a lightweight scratchpad; keeping `ModelTestViewModel` state in `StateFlow` (without `SavedStateHandle` persistence for test inputs) is sufficient, while the Setup system prompt itself is persisted in `SettingsRepository`.
