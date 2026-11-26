# Proposal

## Why

Extracting a calendar event from text found in another Android app currently requires copying the text, opening ContextToCalendar, pasting it, and tapping Extract (or using the Share sheet, which still requires tapping Extract manually). Adding a direct `"Extract event"` action to Android's system text-selection context menu and auto-starting extraction whenever text arrives from outside the app removes that friction and turns any highlighted text into an event draft in one tap.

## What Changes

- Register an Android `ACTION_PROCESS_TEXT` (`text/plain`) entry point labeled `"Extract event"` (via an `<activity-alias>` targeting `MainActivity`) so the app appears directly in Android's system text-selection toolbar across other apps.
- Handle `Intent.EXTRA_PROCESS_TEXT` alongside the existing `ACTION_SEND` (`Intent.EXTRA_TEXT`) path in `MainActivity`, guarding against re-triggering on configuration change or activity recreation.
- Auto-start event extraction immediately when the app receives non-blank external text from either the text-selection context menu (`ACTION_PROCESS_TEXT`) or the Share sheet (`ACTION_SEND`).
- If the app is already open on `ModelSetup` when external text arrives, return to the Extract screen and start extraction right away; if an extraction is already running, cancel it and extract the newly received text.
- Note on Android platform limits: Android does not allow third-party `ACTION_PROCESS_TEXT` actions to filter visibility by selection length or content (e.g., minimum word count), so the action appears whenever text is selected and relies on existing in-app extraction / no-event feedback for non-event selections.

## Capabilities

### New Capabilities

_(None — external text entry extends the existing `text-input` capability.)_

### Modified Capabilities

- `text-input`: Add requirements for receiving text from external Android entry points (`ACTION_PROCESS_TEXT` context-menu action labeled `"Extract event"` and `ACTION_SEND` share sheet), automatically starting extraction on arrival, returning to the Extract screen if another screen is open, cancelling any in-flight extraction, and avoiding duplicate auto-extraction across rotation/recreation.

## Impact

- **Android manifest & resources**: `androidApp/src/AndroidManifest.xml` (`<activity-alias>` with `ACTION_PROCESS_TEXT`), `androidApp/res/values/strings.xml` (`Extract event` label).
- **Android entry point**: `androidApp/src/MainActivity.kt` (extract `EXTRA_PROCESS_TEXT` and `EXTRA_TEXT`, deliver each incoming intent once without re-triggering on rotation).
- **Shared presentation & navigation**: `shared/src/App.kt` (route back to `ExtractRoute` on incoming text), `shared/src/extract/presentation/ExtractViewModel.kt` (auto-start extraction in `onReceiveSharedText`).
- **Tests**: Unit tests in `shared/test/ExtractViewModelTest.kt` covering auto-extraction on incoming external text and cancellation of in-flight extraction.
