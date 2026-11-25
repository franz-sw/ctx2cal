# Design

## Context

See `proposal.md` for why. The shipped v1 hardening added behavior (calendar-strict validation, runtime LiteRT fallback, download cancel/pause/delete, CRLF/folding/exclusive-DTEND ICS, NoEvent notices, single-flight + cancel) without corresponding tests. `openspec/specs/` now holds the archived v1 base (`event-extraction` 3 req, `model-setup` 2, `text-input` 3, `event-edit`/`calendar-share`/`ics-download` 1 each, `material3-theme` 4). Tests must run under Amper (`.\kotlin.bat check`) with existing deps only (`kotlin.test`, `kotlinx-coroutines-test` in `shared/module.yaml`).

## Goals / Non-Goals

**Goals:**

- Pin every Tier 1 regression with fast common-JVM tests and no device.
- Extract pure Android-agnostic mappers (download reason mapping, model-file integrity) into common code so Android `data/` only performs OS effects.
- Fix two stale test files so `shared/test/` compiles and asserts current behavior.

**Non-Goals:**

- No Robolectric, Turbine, AssertK, MockK, or `compose-ui-test`; no Compose/UI or Navigation tests.
- No DownloadManager/MediaStore/Intent/ML Kit/LiteRT device tests; those stay manual on device + `.\kotlin.bat build`.
- No new production behavior beyond the spec deltas; no new capabilities.

## Decisions

### Test tiering: common-pure only

Tier 1 covers `IsoDateTime`, `CalendarEventDraft` validation, `EventJsonParser`, `IcsBuilder`, `ExtractorRouter`, `ExtractViewModel`, `ModelSetupViewModel`. Rationale: all are `shared/src` (platform-agnostic) and deterministic without a device. Alternatives considered: Robolectric for `DownloadManager`/`MediaStore` (rejected — shadows don't prove cancellation, `IS_PENDING` cleanup, or `ACTION_INSERT` handoff, and double the Amper test classpath), Compose UI tests (rejected — state-machine coverage suffices; UI remains manual).

### Fakes over mocks for ViewModels

Use hand-written fake `EventExtractor`/`ModelCatalog`/`ModelDownloader`/`CalendarSharer`/`IcsExporter` + `kotlinx-coroutines-test` `StandardTestDispatcher` (already present). Rationale: matches existing `shared/test/ExtractViewModelTest.kt` pattern, no new dep. `ModelSetupViewModel` tests observe `state` with `runTest` + `advanceUntilIdle` only.

### Extract Android mappers to common pure functions

Move `DownloadManager` reason→message, pause-reason→message, and `actualSize < expected * 0.5` integrity checks into common pure helpers (e.g. `DownloadErrorMapper`) tested Tier 1. `ModelDownloaderAndroid` delegates after querying real OS state. Rationale: OS mapping logic is the actual regression surface; keeping it in `src@android` makes it untestable. Alternative: test against Robolectric shadows (rejected per Non-Goals).

### Update-or-remove strategy for stale tests

`shared/test/ExtractViewModelTest.kt` is updated to the current 5-param `ExtractViewModel` constructor, `Input(notice)` state, and new `Review` fields. `shared/test/PlatformTest.kt` (`Greeting` dead code) is removed — it asserts only "not blank" and duplicates the template. Alternative: keep `PlatformTest` (rejected — zero regression value).

## Risks / Trade-offs

- [Risk] Over-mocking hides contract breaks → Mitigation: use real `EventJsonParser`/`IcsBuilder`/`ExtractorRouter` in tests, not fakes of the units under test.
- [Risk] Testing ViewModel flows with `advanceUntilIdle` can hide missing cancellations → Mitigation: assert intermediate `Extracting` and cancelled `Input` states explicitly.
- [Risk] UTC-midnight roundtrip tests could diverge from Android `CalendarContract` behavior → Mitigation: Tier 1 asserts pure civil-date math only; Android `CalendarSharer` keeps manual device verification.
- [Trade-off] Single-flight + cancel jobs are hard to observe without Turbine → Mitigation: assert via `state.value` transitions; Turbine deferred to a later change if needed.

## Migration Plan

Land tests incrementally: parser/ICS first, then router/catalog, then ViewModels. Rollback = delete new test files; no production or migration effects. `.\kotlin.bat check` must stay green after each slice.

## Open Questions

- None deferrable: Tier 1 scope and no-new-deps boundary are fixed decisions.
