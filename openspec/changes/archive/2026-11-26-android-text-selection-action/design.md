# Design

## Context

`MainActivity` already runs with `android:launchMode="singleTop"` and forwards incoming `ACTION_SEND` (`text/plain`) text into `App(sharedText, onSharedTextConsumed)` -> `ExtractViewModel.onReceiveSharedText(text)`. Currently, `onReceiveSharedText` only fills the text field in `ExtractUiState.Input` without starting extraction, `LaunchedEffect(sharedText)` sits inside `entry<ExtractRoute>` (so it does not run while `ModelSetup` is on top of the back stack), and `MainActivity.onCreate` reads `intent` unconditionally without guarding against configuration-change recreation.

Android's system text-selection toolbar invokes apps via `Intent.ACTION_PROCESS_TEXT` (available since API 23; project `minSdk` is 26). Android does not support manifest-level or dynamic filtering of `ACTION_PROCESS_TEXT` by selected word count or content pattern—visibility is static per `text/plain` intent filter, so short or non-event selections are handled in-app via existing `NoEvent` feedback.

## Goals / Non-Goals

**Goals:**
- Expose an `"Extract event"` chip in Android's system text-selection context menu via `ACTION_PROCESS_TEXT` (`text/plain`).
- Route both `ACTION_PROCESS_TEXT` and `ACTION_SEND` text into `ExtractRoute` and immediately start extraction.
- Cancel any active extraction and return from `ModelSetup` if new external text arrives while the app is already open.
- Guarantee each incoming intent triggers auto-extraction at most once across screen rotation / activity recreation.

**Non-Goals:**
- Filtering or hiding the context-menu chip based on word count, regex, or entity detection (unsupported by Android for third-party `ACTION_PROCESS_TEXT` targets).
- Returning modified replacement text back to the calling app via `setResult` (`EXTRA_PROCESS_TEXT` write-back); this app acts purely as an external viewer/extractor.
- Adding iOS text-selection actions (iOS has no equivalent `ACTION_PROCESS_TEXT` toolbar mechanism; `App.kt` changes remain platform-agnostic).

## Decisions

### 1. `<activity-alias>` for `ACTION_PROCESS_TEXT` instead of putting the filter directly on `MainActivity`
- **Choice**: Add an `<activity-alias>` named `.ProcessTextAlias` targeting `.MainActivity` with `android:exported="true"`, `android:label="@string/process_text_label"` (`"Extract event"`), and the `ACTION_PROCESS_TEXT` + `CATEGORY_DEFAULT` + `text/plain` `<intent-filter>`.
- **Rationale**: Android displays the receiving activity's `android:label` as the text-selection chip label. Putting `ACTION_PROCESS_TEXT` directly on `MainActivity` would either show `"ContextToCalendar"` (too long for the floating toolbar) or risk overriding the launcher name. An `<activity-alias>` isolates the `"Extract event"` label cleanly while still launching `MainActivity` with its `singleTop` behavior.
- **Alternative considered**: Creating a separate trampoline `ProcessTextActivity` that forwards the intent to `MainActivity`. Rejected because an `<activity-alias>` requires no extra Activity class and delivers the intent directly to `MainActivity`.

### 2. Reading `EXTRA_PROCESS_TEXT` as `CharSequence` and guarding single-delivery
- **Choice**: Extract text in `MainActivity.extractIncomingText(intent)` for both `Intent.ACTION_PROCESS_TEXT` (`getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()`) and `Intent.ACTION_SEND` (`getStringExtra(Intent.EXTRA_TEXT)`). In `onCreate`, only process `intent` when `savedInstanceState == null` and mark the intent consumed (`intent.putExtra(EXTRA_CONSUMED, true)`), while continuing to handle `onNewIntent(intent)` for warm launches.
- **Rationale**: Android passes `EXTRA_PROCESS_TEXT` as a `CharSequence` (often `Spannable`), so `getCharSequenceExtra` avoids losing text from apps that attach spans, and `.toString()` strips styling. Guarding with `savedInstanceState == null` plus a consumed marker prevents rotation or activity recreation from re-running extraction and wiping out an ongoing extraction or edited `Review` draft.
- **Alternative considered**: Reading `intent` in `onCreate` unconditionally and relying on `Compose` state. Rejected because activity recreation recreates `pendingSharedText` in `MainActivity` and would re-trigger `LaunchedEffect(sharedText)`.

### 3. Popping `ModelSetup` in `App.kt` before consuming `sharedText`
- **Choice**: Add a `LaunchedEffect(sharedText)` at the `App` scope (above `NavDisplay`) that pops `backStack` back to `ExtractRoute` whenever `!sharedText.isNullOrBlank()` and the top route is not `ExtractRoute`. Keep `LaunchedEffect(sharedText)` inside `entry<ExtractRoute>` to invoke `vm.onReceiveSharedText(sharedText)` and `onSharedTextConsumed()`.
- **Rationale**: `NavDisplay` only composes the active top entry. If `ModelSetup` is currently displayed when a warm `onNewIntent` delivers new text, `ExtractRoute` is not composed and would never consume `sharedText` until the user pressed Back. Popping to `ExtractRoute` makes it active immediately so `ExtractRoute` consumes the text and starts extraction.
- **Alternative considered**: Hoisting `ExtractViewModel` above `NavDisplay`. Rejected because keeping `ExtractViewModel` scoped to `ExtractRoute` matches the existing Navigation 3 + Koin pattern.

### 4. Auto-extracting inside `ExtractViewModel.onReceiveSharedText`
- **Choice**: Update `ExtractViewModel.onReceiveSharedText(text)` to cancel and clear `extractJob` (`extractJob?.cancel(); extractJob = null`), save trimmed text to `savedState`, set `ExtractUiState.Input(cleaned)`, and call `onExtract()`.
- **Rationale**: `onExtract()` already handles single-flight gating, prompt building, timeout, cancellation, and no-model error states. Clearing `extractJob` before calling `onExtract()` ensures a previously running extraction is cleanly superseded without tripping the `extractJob?.isActive == true` guard.
- **Alternative considered**: Triggering `onExtract()` from `App.kt` right after `onReceiveSharedText()`. Rejected because keeping the transition atomic inside the ViewModel makes the behavior directly unit-testable in `ExtractViewModelTest` and avoids Compose side-effect timing races.

## Risks / Trade-offs

- **No-model or short-text selection** -> Auto-extraction runs immediately even if the user highlighted a single unrelated word or has not downloaded a model yet. Mitigation: `onExtract()` already routes no-model failures to `ExtractUiState.Error(isNoModel = true)` with the Setup CTA and routes unparseable text to `ExtractUiState.Input` with the `NoEvent` notice, retaining the text for editing.
- **OEM toolbar overflow placement** -> Android places third-party `ACTION_PROCESS_TEXT` chips after core actions (`Copy`, `Share`, etc.) or inside the overflow menu depending on screen width and OEM rules. Mitigation: Using a concise two-word label (`"Extract event"`) maximizes the chance of fitting in the primary toolbar.
- **Background / foreground timing on cold start** -> `EventExtractor` requires the app to be in the foreground for system-model inference. Since `MainActivity` is brought to the foreground when the user taps the chip and `LaunchedEffect` runs after composition starts, foreground requirements are satisfied; any transient edge case surfaces as a recoverable error with a Retry/Extract button and preserved text.
