# Proposal

## Why

On older Android devices without Gemini Nano (such as the Huawei P30 Pro on Kirin 980), event extraction currently appears to generate forever: `LiteRtExtractor` invokes raw `Session.generateContent()` with no chat template, no token limit, no timeout, no cancellation hook (`Session.cancelProcess()`), and thinking-enabled defaults on `Qwen3-0.6B`, while `ModelDownloaderAndroid` downloads a 613.7 MB INT8 model with no hardware check. At the same time, Setup only offers one hardcoded placeholder model instead of letting users choose from multiple ungated models sized for their device's hardware.

## What Changes

- **Bounded, cancellable LiteRT-LM inference**: Replace raw `Session.generateContent()` in `LiteRtExtractor` with `Conversation` (`ConversationConfig` using `systemInstruction`, `ThinkingConfig(enableThinking = false)`, deterministic `SamplerConfig`, capped `maxNumTokens` / `maxOutputToken`, and `sendMessageAsync`), enforced by a per-attempt timeout and `conversation.cancelProcess()` on timeout or user cancel.
- **Prompt and retry bounds**: Cap pasted input text length, provide a concise one-shot JSON example in the system/extraction prompt, and avoid unbounded retry loops or full-prompt re-expansion when a call times out or is cancelled.
- **GPU capability awareness and fallback caching**: Probe Android hardware capabilities (ABI, total RAM, GPU/GLES/Vulkan/SoC signals, free storage) and persist GPU initialization failures so devices where OpenCL/GPU delegation fails or stalls do not repeatedly hang trying `Backend.GPU()`.
- **Multi-model ungated catalog & storage**: Replace the single hardcoded candidate with a curated catalog of ungated Apache-2.0 `.litertlm` models (`Qwen3 0.6B INT4`, `Qwen3 1.7B INT4`, `Gemma 4 E2B`, `Gemma 4 E4B`), stored per-model on disk so users can keep multiple models, switch the active model (including switching back to Gemini Nano when available), and delete any downloaded model via a confirmation `AlertDialog`.
- **Hardware suitability & warning UI**: Evaluate every catalog model against the device's hardware before download, display suitability badges (`Recommended`, `CPU only · may be slow`, or `Incompatible`), disable the Download action when RAM/ABI/storage requirements are not met, and show a top-level warning banner when no accelerated/recommended model is available on the device.

## Capabilities

### New Capabilities

None — all behaviors evolve the existing `event-extraction` and `model-setup` capabilities.

### Modified Capabilities

- `event-extraction`: Bound on-device inference with chat-templated `Conversation` execution, disabled thinking tokens, explicit token/context limits, input-length caps, timeouts, native cancellation, cached GPU-init fallback, and honoring the user's selected active model.
- `model-setup`: Replace the single fixed candidate with a multi-model ungated catalog, per-model storage and switching, confirmation `AlertDialog` on model deletion, Android hardware suitability gating (disabling downloads that exceed device RAM/ABI/storage), and a device-level warning banner when no recommended/accelerated model is supported.

## Impact

- **Shared domain & presentation**: `shared/src/core/domain/model/CalendarEventDraft.kt` (`ModelCandidate`, `ModelStatus`, hardware suitability types), `shared/src/core/domain/ports/Ports.kt` (`ModelCatalog`, `ModelDownloader`), `shared/src/core/domain/repository/SettingsRepository.kt`, `shared/src/core/domain/routing/ExtractorRouter.kt`, `shared/src/extract/domain/EventJsonParser.kt`, `shared/src/extract/presentation/ExtractViewModel.kt`, `shared/src/extract/presentation/ExtractScreen.kt`, `shared/src/setup/presentation/ModelSetupViewModel.kt`, `shared/src/setup/presentation/ModelSetupScreen.kt`.
- **Android platform layer**: `shared/src@android/core/data/llm/LiteRtExtractor.kt`, `GeminiNanoExtractor.kt`, `AndroidModelCatalog.kt`, `ModelDownloaderAndroid.kt`, new Android hardware capability probe, `shared/src@android/core/data/settings/AndroidSettingsRepository.kt`, `shared/src@android/core/di/AndroidPlatformModule.kt`.
- **iOS platform layer**: `shared/src@ios/core/platform/IosUnsupportedPorts.kt` updated for any expanded `ModelCatalog`/`ModelDownloader` methods.
- **Tests & Docs**: `shared/test/ModelSetupViewModelTest.kt`, `shared/test/EventJsonParserTest.kt`, `shared/test/ExtractViewModelTest.kt`, new pure tests for catalog/suitability/routing, and `README.md` (resolving the placeholder model note).
