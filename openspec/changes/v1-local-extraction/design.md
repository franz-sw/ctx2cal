# Design

## Context

Greenfield KMP template (Amper, not Gradle) with a placeholder `App()` in `shared/src/App.kt:26`, Compose + Lifecycle ViewModel deps in `shared/module.yaml`, and no specs. See `proposal.md` for motivation. All v1 LLM/calendar/file behavior is Android-only; shared Compose UI must stay platform-agnostic per `AGENTS.md`.

## Goals / Non-Goals

**Goals:**

- Two-route Navigation 3 app (Extract = input + review combined, Setup) with serializable `NavKey`s and process-death-safe back stack.
- One `EventExtractor` port in shared domain code with two Android backends (Gemini Nano via ML Kit/AICore, LiteRT-LM) plus availability-based selection.
- Koin Classic DSL DI in shared code, started from the root composable.
- Folder-only structure inside existing modules; no new Amper modules.

**Non-Goals:**

- Koog, cloud LLMs, multi-event extraction, direct CalendarProvider insert, GGUF/llama.cpp, iOS LLM support, Koin compiler plugin / annotations in v1.

## Decisions

### Navigation: Navigation 3, sealed serializable routes

`@Serializable sealed interface Route : NavKey` with `ExtractRoute` (start) and `ModelSetup`, registered via `SavedStateConfiguration` + `subclassesOfSealed()` and `rememberNavBackStack(config, ExtractRoute)`. Rejected: Parcelable back stack (Android-only) and string routes (untyped, no process-death safety).

### Combined Extract screen state machine

One ViewModel (`ExtractViewModel`) with states `Input | Extracting | Review | Error`; `inputText` and `draft` held together with `SavedStateHandle` so they restore together. `EventForm` is a reusable composable, not a route. Rejected: separate Review route — passing the draft across routes adds serialization weight and UX friction.

### Extraction port and provider selection

`domain/ports/EventExtractor` (common) implemented in `src@android/` by `GeminiNanoExtractor` (ML Kit GenAI `GenerativeModel`, `checkFeatureStatus`, foreground-only errors) and `LiteRtExtractor` (LiteRT-LM `Engine` + `.litertlm` on GPU backend). `AndroidModelCatalog.select()` returns Nano if available, else LiteRT if a downloaded model file exists, else `None` → error + route to Setup. LLM output validated as single-event JSON; malformed output retried once then rejected. Rejected: Koog (no ML Kit client, beta LiteRT client, wrong abstraction level for single-shot extraction).

### Model download

DownloadManager-based download of the offered `.litertlm` (Hugging Face litert-community) into internal storage with progress, size check, and persisted backend/model-path settings. Rejected: bundling a model in the APK (hundreds of MB–GB) and runtime fetching via app HTTP stack (DownloadManager is the platform-standard path).

### Outputs

Share via `Calendars.ACTION_INSERT` intent (user picks calendar app, no permission). ICS export builds RFC 5545 text (escaped fields, UID, DTSTAMP) and writes it via MediaStore to Downloads (scoped storage on Android 10+). Rejected: direct CalendarContract insert (needs READ/WRITE_CALENDAR) and direct file-path writes (blocked by scoped storage).

### DI: Koin Classic DSL in shared

`koin-compose` + `koin-compose-viewmodel` in common deps; `appModule` with `single`/`viewModel` definitions; `KoinApplication` wrapper in `App.kt`. Rejected: compiler plugin/annotations in v1 (Kotlin 2.3.20+ compiler-plugin hook is unverified under Amper) and manual factories (user chose Koin; testability via fakes).

### Folder structure (folders only, no new modules)

```text
shared/
  module.yaml                  (edit: deps@android for ML Kit/LiteRT; no new module)
  src/
    App.kt                     (KoinApplication + NavDisplay host)
    navigation/Routes.kt
    domain/model/              (CalendarEventDraft, ExtractionResult, ModelStatus)
    domain/ports/              (EventExtractor, ModelCatalog, CalendarSharer, IcsExporter)
    domain/repository/         (SettingsRepository)
    presentation/extract/      (ExtractScreen, ExtractViewModel, ExtractUiState, EventForm)
    presentation/setup/        (ModelSetupScreen, ModelSetupViewModel)
    presentation/components/
    di/appModule.kt
  src@android/
    data/llm/                  (GeminiNanoExtractor, LiteRtExtractor, ModelDownloader,
                                  AndroidModelCatalog)
    data/calendar/AndroidCalendarSharer.kt
    data/ics/                  (IcsExporter via MediaStore)
    di/                        (android wiring overlay)
  src@ios/
    platform/                  (unsupported stubs)
  test/                        (domain, JSON, ICS, ViewModel tests)
androidApp/src/                (manifest entries, MainActivity)
```

## Risks / Trade-offs

- [Risk] Amper + ML Kit/LiteRT/Serialization/Koin integration unproven → Mitigation: tasks.md starts with a build spike (deps, plugin, minimal Nano call on a Pixel) before feature code.
- [Risk] Gemini Nano availability varies by device/RAM/OS/AICore → Mitigation: never block UI on it; explicit Setup screen + LiteRT fallback + no-model error.
- [Risk] LiteRT model downloads are 0.6–4 GB, gated repos need tokens → Mitigation: default to a small model (e.g. Qwen3-0.6B), show progress/size, allow retry.
- [Risk] Small on-device models produce malformed JSON or poor date resolution → Mitigation: strict validation, one retry, today/timezone injection, and full edit-before-share step.
- [Risk] ML Kit foreground-only restriction → Mitigation: map background errors to a recoverable hint; never run inference in services.
- [Trade-off] Single-event v1 loses secondary dates in a paste → Mitigation: accepted per D4; re-paste flow covers it.

## Migration Plan

Not applicable (greenfield). Rollback = revert the change's files; no data migration (settings only).

## Open Questions

- Which exact downloadable model + Hugging Face repo/token handling is the v1 default? (Deferred: does not change specs or task breakdown; must be fixed before the download task ships.)
