# Proposal

## Why

The v1 extraction, sharing, and ICS code was hardened after `v1-local-extraction` was completed — fixing ISO-8601 validation, RFC 5545 compliance, model-fallback behavior, download cancellation, and error feedback — without adding any new unit tests, leaving stale ViewModel tests and unpinned regressions.

## What Changes

- Pin the post-v1 hardening with **Tier 1 (common-pure) unit tests** only: `IsoDateTime` calendar math, `CalendarEventDraft` validation, `EventJsonParser` robustness, `IcsBuilder` RFC 5545 output, `ExtractorRouter` selection/fallback, and the `ExtractViewModel` / `ModelSetupViewModel` state machines via fakes.
- Extract testable pure logic for Android-platform behavior that cannot run in plain unit tests (DownloadManager error/pause reason mapping, model-file integrity gate) so it lives in common code and is covered by Tier 1 tests; Android `data/` keeps only OS effects.
- Clarify that Compose UI, Navigation 3 back-stack wiring, DownloadManager/MediaStore/Intent, ML Kit, and LiteRT JNI remain covered by `.\kotlin.bat build` + manual device checks, not by new test infrastructure (no Robolectric, Turbine, AssertK, or Compose test rule in this change).

## Capabilities

### New Capabilities

None — this change pins behavior already shipped in v1; it does not introduce new durable system behaviors.

### Modified Capabilities

- `event-extraction`: Structured output, provider selection, and retry-context requirements change to state runtime LiteRT fallback on Nano failure, cancel-safe foreground handling, and retry prompts that include the original context.
- `event-edit`: Validation requirement changes to per-field, calendar-strict validation (invalid dates, mixed date/datetime, range) and date/time pickers instead of raw ISO text.
- `model-setup`: Download requirement changes to cancel/pause/delete, free-space gating, verify-before-ready, and stale-path recovery; status reporting changes to cover `NanoDownloadable`/`NanoDownloading`/`Unsupported`.
- `ics-download`: RFC 5545 requirement changes to CRLF, line folding, exclusive all-day `DTEND`, and derived filenames.
- `text-input`: Failure-feedback requirement changes so `NoEvent` is a retained-text notice (not a silent reset), and extraction/download use single-flight + cancel semantics.

## Impact

- **Code**: `shared/src/core/domain/model/CalendarEventDraft.kt`, `shared/src/extract/domain/EventJsonParser.kt`, `shared/src/core/data/ics/IcsBuilder.kt`, `shared/src/core/domain/routing/ExtractorRouter.kt`, `shared/src/extract/presentation/ExtractViewModel.kt`, `shared/src/setup/presentation/ModelSetupViewModel.kt`; new pure mappers extracted from `shared/src@android/core/data/llm/ModelDownloaderAndroid.kt`.
- **Dependencies**: None added. Tests use existing `kotlin.test` + `kotlinx-coroutines-test` in `shared/module.yaml`.
- **Platform**: Android `data/` unchanged except delegating to new pure mappers; iOS stubs unchanged.
- **Risk**: Two existing tests (`shared/test/ExtractViewModelTest.kt`, `shared/test/PlatformTest.kt`) are stale/non-assertive and must be updated or removed as part of this change.
