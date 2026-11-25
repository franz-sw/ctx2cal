# Tasks

## 1. Pure-model tests: IsoDateTime + draft validation

- [x] 1.1 Add `IsoDateTime` unit tests (`shared/test`) covering impossible dates (month/day/time/offset bounds, leap-year 2026-02-29 rejection, 2024-02-29 acceptance), `normalize`/`toBasic` seconds padding, `compare`, `nextDate`, and UTC-midnight roundtrip; verify new tests pass with `.\kotlin.bat check`
- [x] 1.2 Add `CalendarEventDraft` validation tests covering per-field errors, mixed date/datetime rejection, all-day date-only rules, and `end < start` after normalization; verify new tests pass with `.\kotlin.bat check`

## 2. Parser and ICS regression tests

- [x] 2.1 Extend `EventJsonParserTest` with non-primitive-field rejection, braces-inside-strings unwrap, fenced/prose stripping, and retry-prompt original-context inclusion; verify new tests pass with `.\kotlin.bat check`
- [x] 2.2 Extend `IcsBuilderTest` with CRLF, 75-character folding, exclusive all-day `DTEND`, date-only `VALUE=DATE` stripping, and `suggestedFileName`; verify new tests pass with `.\kotlin.bat check`

## 3. Router, catalog, and download-mapper tests

- [x] 3.1 Add `ExtractorRouter` tests with fakes covering Nano-preferred, LiteRT fallback, Nano runtime-Failure fallback, no-model (`isNoModel=true`), and `Unsupported`; verify new tests pass with `.\kotlin.bat check`
- [x] 3.2 Extract download error/pause mapping and model-integrity gate to common pure helpers and add Tier 1 tests for error reasons, pause reasons, and truncated-size rejection; verify new tests pass with `.\kotlin.bat check`

## 4. ViewModel state-machine tests

- [x] 4.1 Update `ExtractViewModelTest` to current constructor/state and cover `NoEvent` notice, cancel-extract, single-flight/double-tap guards, draft-edit notice clearing, `onShare`/`onDownload` validation blocking, and stale `isNoModel` clearing on `refreshModelStatus`; verify new tests pass with `.\kotlin.bat check`
- [x] 4.2 Add `ModelSetupViewModelTest` with fakes covering `Loading` reset, `Ready` status/candidate, download `Running`/`Paused`/`Done`/`Failed`/`Cancelled` transitions, cancel/delete, and no catalog re-poll on progress; verify new tests pass with `.\kotlin.bat check`
- [x] 4.3 Remove stale `shared/test/PlatformTest.kt` (`Greeting` dead code) and verify `.\kotlin.bat check` stays green across all `shared/test/`

## 5. Integration verification

- [x] 5.1 Run `.\kotlin.bat check` and `.\kotlin.bat build`, verify Tier 1 suite passes, Android assembles, and manual device checklist (Nano > LiteRT, cancel download, all-day ICS, share intent) matches the updated specs
