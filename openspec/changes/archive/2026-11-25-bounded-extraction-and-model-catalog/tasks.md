# Tasks

## 1. Domain Models, Catalog Entries, and Hardware Suitability Evaluator

- [x] 1.1 Extend `ModelCandidate`, `ModelStatus`, and hardware capability/suitability types in `shared/src/core/domain/model/` (including `id`, `fileName`, `minRamMb`, `approxSizeBytes`, `DeviceCapabilities`, and `ModelSuitability` tiers: `Recommended`, `CpuOnlySlow`, `Incompatible`) and define the 4-model Apache-2.0 ungated catalog (`Qwen3 0.6B INT4`, `Qwen3 1.7B INT4`, `Gemma 4 E2B`, `Gemma 4 E4B`). Verify shared compilation passes (`.\kotlin.bat check`).
- [x] 1.2 Implement the pure-Kotlin `ModelSuitabilityEvaluator` in `shared/src/core/domain/` covering ABI (`arm64-v8a`/`x86_64`), total RAM, free storage (`1.15x` staging/install check), older GPU/SoC CPU-only classification, and top-level warning flag, plus unit tests in `shared/test/` covering P30-class (CPU-only, 6 GB RAM), flagship (recommended), low-RAM (incompatible), and unsupported ABI cases (`.\kotlin.bat test`).
- [x] 1.3 Update `Ports.kt` (`ModelCatalog`, `ModelDownloader`), `SettingsRepository.kt`, `AndroidSettingsRepository.kt`, and `IosUnsupportedPorts.kt` to support per-model paths, active model ID selection (`nano` or catalog `id`), `activeDownloadModelId`, and cached `gpuUnsupported` persistence, with fake updates in `shared/test/` (`.\kotlin.bat check`).

## 2. Bounded Prompt, LiteRT Conversation Inference, and Router Selection

- [x] 2.1 Update `EventJsonParser` in `shared/src/extract/domain/EventJsonParser.kt` to bound pasted input length (`MAX_INPUT_CHARS = 2000`), provide system/user prompt parts with a concise one-shot JSON example, and bound retry context; update/add unit tests in `shared/test/EventJsonParserTest.kt` (`.\kotlin.bat test`).
- [x] 2.2 Refactor `LiteRtExtractor.kt` to use `EngineConfig(maxNumTokens = 1024)` and `Conversation` (`ConversationConfig` with `systemInstruction`, `ThinkingConfig(enableThinking = false)`, `SamplerConfig(topK = 1, topP = 0.95, temperature = 0.0, seed = 0)`, `maxOutputToken = 256`), streaming via `sendMessageAsync` under `withTimeout` with `conversation.cancelProcess()` on timeout or `CancellationException`, and cached `gpuUnsupported` fallback to `Backend.CPU(threadCount = 4)`. Verify Android build compiles (`.\kotlin.bat build`).
- [x] 2.3 Add per-attempt timeout and non-retry-on-timeout/cancel semantics to `GeminiNanoExtractor.kt` and `ExtractViewModel.kt`, and update `ExtractorRouter.kt` to honor the user's selected active model (`nano` vs specific downloaded LiteRT model) with runtime fallback and unit tests in `shared/test/` (`.\kotlin.bat test`).

## 3. Android Hardware Probe, Multi-Model Downloader, and Catalog

- [x] 3.1 Implement `AndroidDeviceCapabilitiesProvider` in `shared/src@android/core/data/llm/` reading ABI, `ActivityManager.MemoryInfo.totalMem`, internal/external `StatFs`, `Build.SOC_MODEL`/`HARDWARE`/`BOARD`, SDK level, Vulkan/GLES renderer string, and `gpuUnsupported` setting; wire it in `AndroidPlatformModule.kt` and verify build (`.\kotlin.bat build`).
- [x] 3.2 Update `ModelDownloaderAndroid.kt` to store models at `filesDir/models/<candidate.id>.litertlm` (cleaning up legacy `model.litertlm`), track `activeDownloadModelId`, enforce per-model storage/size verification, and delete individual models cleanly (`.\kotlin.bat build`).
- [x] 3.3 Update `AndroidModelCatalog.kt` to report Nano status, per-model download & active states, and `ModelSuitability` evaluations across the 4-model catalog, resolving stale active paths to another ready model or Nano (`.\kotlin.bat build`).

## 4. Setup & Extract Presentation and Documentation

- [x] 4.1 Update `ModelSetupViewModel.kt` and `shared/test/ModelSetupViewModelTest.kt` to expose the catalog list, per-model download/active/suitability state, active-model switching (including switching back to Gemini Nano), and confirmed deletion, verifying all ViewModel tests pass (`.\kotlin.bat test`).
- [x] 4.2 Update `ModelSetupScreen.kt` to render the top-level hardware warning banner (when Nano is unavailable and no model is `Recommended`), list all catalog models with suitability badges and disabled Download buttons for `Incompatible` entries, support switching active models, and require confirmation via a Material 3 `AlertDialog` before deleting any downloaded model (`.\kotlin.bat build`).
- [x] 4.3 Update `ExtractScreen.kt` / `ExtractViewModel.kt` to surface the active model name/backend clearly, show elapsed time or bounded extraction status while extracting, and ensure `README.md` replaces the placeholder model note with the finalized ungated catalog and hardware-check summary (`.\kotlin.bat check` and `.\kotlin.bat build`).

## 5. End-to-End Verification

- [x] 5.1 Run full `.\kotlin.bat check` and `.\kotlin.bat build`, and manually verify on Huawei P30 Pro (CPU-only warning banner shown, `Qwen3 0.6B INT4` downloadable, `Gemma 4 E4B` disabled, extraction finishes or cancels cleanly within timeout without hanging) and a Gemini Nano / modern GPU device if available.
