# Design

## Context

See `proposal.md` for motivation and `specs/` for the behavior contracts.

Key codebase constraints discovered during exploration:
- `shared/src@android/core/data/llm/LiteRtExtractor.kt` currently opens `engine.createSession(SessionConfig())` and calls blocking `session.generateContent(listOf(InputData.Text(prompt)))` on `Dispatchers.IO`. Inspecting `litertlm-android-0.19.1` shows `Session` bypasses the `.litertlm` chat template and offers neither `maxOutputToken` nor `ThinkingConfig`, whereas `Engine.createConversation(ConversationConfig(...))` provides `systemInstruction`, `SamplerConfig`, `maxOutputToken`, `ThinkingConfig`, `sendMessageAsync(...) : Flow<Message>`, and `cancelProcess()`.
- `ModelDownloaderAndroid.kt` hardcodes a single candidate pointing to `Qwen3-0.6B.litertlm` (`613.7 MB` INT8, labeled `380 MB`) written to one fixed file (`filesDir/models/model.litertlm`). Its smaller INT4 sibling (`Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`, `343.5 MB`) is ungated, Apache-2.0, and halves peak RSS (~2.0 GB vs ~4.3 GB).
- `litertlm-android-0.19.1` provides no static pre-download "is GPU supported?" probe without initializing an `Engine` with a `.litertlm` file, so pre-download suitability relies on Android device signals (ABI, RAM, GPU/EGL/Vulkan/SoC, storage) paired with runtime GPU-fallback caching.
- `ExtractorRouter` and `AndroidModelCatalog` currently ignore `SettingsRepository.preferredBackend()` (always preferring Nano over LiteRT).

## Goals / Non-Goals

**Goals:**
- Guarantee bounded, cancellable, non-thinking single-event extraction in `LiteRtExtractor` and bounded retries/timeouts across both extractors and `ExtractViewModel`.
- Provide an ungated Apache-2.0 multi-model catalog (`Qwen3 0.6B INT4`, `Qwen3 1.7B INT4`, `Gemma 4 E2B`, `Gemma 4 E4B`) with per-model disk files, active-model switching (including Nano), and `AlertDialog`-confirmed deletion.
- Evaluate device hardware capabilities in pure testable domain logic fed by an Android hardware probe, disabling incompatible downloads and surfacing a clear Setup warning when no accelerated model is supported.

**Non-Goals:**
- Supporting gated Hugging Face models or user Hugging Face token authentication.
- Keeping an active `Engine` pinned in memory between extractions (deferred; would increase idle app memory footprint).
- Supporting iOS on-device inference (iOS remains unsupported with updated port stubs).
- Runtime token-streaming UI in `ExtractScreen` (extraction still exposes `Extracting` -> `Review`/`Error`, though an elapsed-time indicator or cancel responsiveness improvement is part of the bounded UX).

## Decisions

### 1. Migrate `LiteRtExtractor` from `Session` to `Conversation`
- **Decision**: Use `EngineConfig(..., maxNumTokens = 1024)` and `engine.createConversation(ConversationConfig(systemInstruction = Contents.of(...), samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.0, seed = 0), maxOutputToken = 256, thinkingConfig = ThinkingConfig(enableThinking = false)))`, consuming `conversation.sendMessageAsync(userMessage)` as a `Flow<Message>` inside `withTimeout(EXTRACTION_TIMEOUT_MS)` (e.g., 60s per attempt) and calling `conversation.cancelProcess()` in `finally` / `catch` on `TimeoutCancellationException` or `CancellationException`.
- **Rationale**: Applies each model's embedded Jinja chat template (`Qwen3` and `Gemma 4`), disables `<think>` / `<|think|>` reasoning tokens that otherwise stall weak CPUs, caps KV-cache allocation (`1024` vs `4096+` default), caps decode tokens (`256` is ample for one calendar event JSON), and makes Cancel + timeout actually interrupt native JNI work.
- **Alternative**: Keep `Session.runPrefill`/`runDecode` with manual prompt templates -> rejected because `Qwen3` and `Gemma 4` use distinct chat/thinking tags (`<|im_start|>` vs `<|turn>`) and `Conversation` handles both transparently from `.litertlm` metadata.

### 2. Prompt bounding & single-shot contract
- **Decision**: Split prompt concerns cleanly or keep `EventJsonParser.buildPrompt` concise with a max pasted-text cap (`MAX_INPUT_CHARS = 2000`, trimming cleanly), a single minimal JSON example, and an explicit `"Output ONLY the JSON object and stop"` instruction. Retry happens at most once only for `Parsed.Invalid` (never after timeout or cancellation), and uses a trimmed retry context bounded to stay under `maxNumTokens`.
- **Rationale**: Prevents oversized clipboard pastes from blowing past the `1024`-token context limit or causing long prefill stalls on CPU-only devices.

### 3. Runtime GPU fallback caching (`gpuDisabled`)
- **Decision**: Persist a `gpuUnsupported` flag (keyed or cleared on app version change) in `SettingsRepository`. When `Backend.GPU().initialize()` throws on a device (e.g. Mali-G76 / Kirin 980), record `gpuUnsupported = true`, fall back to `Backend.CPU(threadCount = 4)`, and start directly on CPU on subsequent extractions. Also allow the hardware probe's known-unsupported-GPU classification to skip doomed GPU attempts up front or fail fast.
- **Rationale**: Avoids paying repeated GPU initialization failures/hangs on every extraction.
- **Alternative**: Always try `Backend.GPU()` every run -> rejected because failed OpenCL compilation wastes seconds or hangs on older drivers.

### 4. Ungated Apache-2.0 multi-model catalog
- **Decision**: Define a static pure-Kotlin catalog (`ModelCatalogEntries`) with 4 verified ungated Apache-2.0 `.litertlm` entries on `litert-community`:
  1. `qwen3-0.6b-int4` — `litert-community/Qwen3-0.6B` / `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` (`343_490_560L` bytes, min `4` GB RAM)
  2. `qwen3-1.7b-int4` — `litert-community/Qwen3-1.7B` / `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm` (`977_184_032L` bytes, min `6` GB RAM)
  3. `gemma-4-e2b` — `litert-community/gemma-4-E2B-it-litert-lm` / `gemma-4-E2B-it.litertlm` (`2_588_147_712L` bytes, min `8` GB RAM)
  4. `gemma-4-e4b` — `litert-community/gemma-4-E4B-it-litert-lm` / `gemma-4-E4B-it.litertlm` (`3_659_530_240L` bytes, min `12` GB RAM)
- **Rationale**: Covers low-RAM/compact (`344 MB`) up to high-accuracy flagship (`3.66 GB`), avoids gated Gemma 3 / Llama 3.2 repos, avoids `Qwen3.5-0.8B` (needs `>= 0.20.0` or split GPU/CPU files), and keeps all models under Apache-2.0.

### 5. Per-model disk layout, active selection, and `ExtractorRouter`
- **Decision**:
  - Store downloaded models as `filesDir/models/<candidate.id>.litertlm` (important: `.litertlm` extension is required by `litertlm-android` native header check) and migrate/clean up legacy `filesDir/models/model.litertlm` if present.
  - Extend `SettingsRepository` with `activeModelId(): String?` (e.g. `"nano"` or a catalog `candidate.id`, plus `activeDownloadModelId(): String?` alongside `activeDownloadId()`).
  - Update `ExtractorRouter` to read the active selection from `ModelCatalog` / `SettingsRepository`: use the user's selected model when ready (Nano or specific LiteRT model), default to Nano when available and no override is set, and fall back to any other ready model (Nano or another downloaded LiteRT model) if the primary fails at runtime.
- **Rationale**: Enables seamless switching between downloaded models and Nano without deleting files, while preserving automatic fallback safety.

### 6. Android hardware probe & pure suitability evaluator
- **Decision**:
  - Add an Android `DeviceCapabilitiesProvider` that reads:
    - `Build.SUPPORTED_ABIS` (`arm64-v8a` or `x86_64` required for bundled JNI)
    - `ActivityManager.MemoryInfo.totalMem` (in GB/MB)
    - `StatFs(filesDir)` and `StatFs(externalFilesDir)` available bytes
    - GPU/SoC signals (`Build.SOC_MODEL` on API 31+, `Build.HARDWARE`, `Build.BOARD`, `EGL14` / `GLES20.GL_RENDERER` off-screen or cached probe, Vulkan hardware level, `Build.VERSION.SDK_INT`)
  - Keep the classification rules in a **pure Kotlin `ModelSuitabilityEvaluator`** in `shared/src/` so all RAM, ABI, storage, GPU-tier, and top-level-warning rules are 100% unit-testable without Android dependencies:
    - `Incompatible(reason)` -> disables Download (non-arm64/x86_64, `totalRamMb < model.minRamMb`, or `freeStorageBytes < model.approxSizeBytes * 1.15`).
    - `CpuOnlySlow(reason)` -> Download allowed with badge (`"CPU only · may be slow"`), triggered when GPU renderer/SoC matches known older/CPU-fallback GPUs (e.g., older Mali-G7x like Mali-G76/G72/G52, older Adreno < 7xx, Android < 12, or cached `gpuUnsupported`).
    - `Recommended` -> RAM, storage, and modern GPU criteria met.
  - Show a top-level warning banner in `ModelSetupScreen` whenever Gemini Nano is unavailable AND no catalog model evaluates to `Recommended`.
- **Rationale**: Pure evaluator + thin Android sensor keeps hardware rules deterministic, transparent, and testable in `commonTest` / `shared/test`.

### 7. Delete confirmation `AlertDialog` in `ModelSetupScreen`
- **Decision**: Tapping Delete on a downloaded model opens an in-screen Material 3 `AlertDialog` (`"Delete <model name>?"` showing freed size, with Confirm / Cancel) in `ModelSetupScreen` (or driven by a `pendingDeleteModelId` state in `ModelSetupViewModel` / composable state) before invoking `onDeleteModel(modelId)`.
- **Rationale**: Prevents accidental loss of 344 MB–3.66 GB downloads.

## Risks / Trade-offs

- **Heuristic GPU detection accuracy** -> Android `GL_RENDERER` / `SOC_MODEL` heuristics cannot 100% predict OpenCL kernel compatibility for every SoC; some mid-tier GPUs could be misclassified as `CpuOnlySlow` or vice versa. *Mitigation*: `CpuOnlySlow` still permits downloading with an honest badge, and `gpuUnsupported` runtime caching corrects optimistic classifications automatically after one failed GPU init.
- **EGL context query on Android** -> Querying `GLES20.GL_RENDERER` requires a tiny pbuffer EGL context (or reading `Build.HARDWARE`/`SOC_MODEL` first). *Mitigation*: Wrap the EGL query in a safe helper on `Dispatchers.Default` with fallback to `Build.HARDWARE`/`SOC_MODEL` if EGL setup fails, and cache the `DeviceCapabilities` snapshot in memory.
- **DownloadManager staging for multi-GB models (Gemma 4 E2B/E4B)** -> Staging in `getExternalFilesDir` and copying to `filesDir` briefly requires ~2x model size (unless `DownloadManager` destination or cleanup runs promptly). *Mitigation*: Gate download against both `filesDir` and `extDir` free space (1.15x each, matching existing `ModelDownloaderAndroid` check) and display the required free space clearly when disabled.
- **Interaction with `stabilization-unit-tests`** -> `stabilization-unit-tests` is currently in-progress (8/10 tasks) and also touches `ModelSetupViewModelTest` / `specs/model-setup`. *Mitigation*: Implement/archive `stabilization-unit-tests` cleanly first or rebase test fixtures when applying this change.
