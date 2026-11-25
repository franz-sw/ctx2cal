# Proposal

## Why

Users often have dates and event details in free text (emails, messages, notes) and have to transcribe them manually into a calendar. ContextToCalendar turns a pasted text block into an editable, shareable calendar event entirely on-device — no cloud LLM calls — starting with Android.

## What Changes

- Add a combined **Extract screen**: paste text, trigger extraction, edit the resulting event fields in place, then share or download — without navigating away.
- Add a **Model Setup screen** showing which on-device model is available and allowing download of an open model.
- Auto-detect a device-provided local LLM (Gemini Nano via ML Kit/AICore, e.g. Nano 2/3/4) as the default extraction backend.
- Add a downloadable open-model backend (LiteRT-LM `.litertlm`, e.g. Gemma/Qwen) as fallback for devices without the system model.
- Add an **edit step**: all event fields (title, start, end, all-day, description, location) are editable before sharing or export.
- Add **share to Calendar app** via Android `ACTION_INSERT` intent (no calendar permission required).
- Add **download as `.ics`** to the device Downloads folder via MediaStore (RFC 5545).
- Add multi-screen navigation (Extract + Setup) via Navigation 3 with serializable `NavKey` routes.
- Add Koin (Classic DSL) for dependency injection in the shared module, starting Koin from the root composable.

Non-goals for v1: multi-event extraction (one paste → exactly one event; other dates ignored), cloud LLMs, Koog, direct CalendarProvider insert, GGUF/llama.cpp, fine-tuned models, background extraction, iOS LLM support.

## Capabilities

### New Capabilities

- `text-input`: Pasting free text and extracting exactly one calendar-event draft, with loading, failure, and re-extract states on the combined Extract screen.
- `model-setup`: Reporting on-device model availability (Gemini Nano auto-detected vs. downloadable LiteRT-LM model) and downloading/selecting an open model.
- `event-extraction`: The on-device LLM contract — prompt with injected date/timezone, strict JSON event output, validation, and provider selection/fallback including the no-model error.
- `event-edit`: Editing all fields of the extracted draft before sharing or export.
- `calendar-share`: Sharing the edited event to a Calendar app via Android insert intent.
- `ics-download`: Exporting the edited event as an RFC 5545 `.ics` file to the Downloads folder.

### Modified Capabilities

None — greenfield change; no existing specs in `openspec/specs/`.

## Impact

- **Code**: New folders in existing `shared/` module (`navigation/`, `di/`, `domain/`, `presentation/`) and platform actuals in `shared/src@android/` + iOS unsupported stubs in `src@ios/`. No new Amper modules.
- **Dependencies**: Navigation 3, `kotlinx-serialization` (+ compiler plugin), Koin (Classic DSL; no compiler plugin in v1), ML Kit GenAI, LiteRT-LM Android, coroutines. Versions via `libs.versions.toml`, wired in `shared/module.yaml` (`dependencies@android` for Android-only libs).
- **Platform**: Android-only LLM/calendar/ICS implementation in v1; shared Compose UI stays platform-agnostic. `androidApp/` manifest gets native-library declarations for the LiteRT GPU backend plus `INTERNET` for model download.
- **Risk**: Amper + ML Kit/LiteRT AAR integration and Koin-under-Amper need a build spike before feature code.
