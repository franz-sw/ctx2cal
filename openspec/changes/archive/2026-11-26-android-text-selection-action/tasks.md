# Tasks

## 1. Shared Auto-Extraction & Navigation

- [x] 1.1 Update `ExtractViewModel.onReceiveSharedText(text)` in `shared/src/extract/presentation/ExtractViewModel.kt` to trim non-blank text, cancel and clear any active `extractJob`, persist the text in `SavedStateHandle`, and immediately call `onExtract()`. Verify in `shared/test/ExtractViewModelTest.kt` with unit tests covering auto-extraction on incoming text, ignoring blank text, and cancelling an in-flight extraction when new external text arrives (`./kotlin.bat check`).
- [x] 1.2 Update `shared/src/App.kt` so an incoming non-blank `sharedText` pops `backStack` back to `ExtractRoute` whenever another route (such as `ModelSetup`) is active, allowing `ExtractRoute` to consume `sharedText` and start extraction immediately. Verify by inspection and `./kotlin.bat check`.

## 2. Android Text-Selection Entry Point & Intent Handling

- [x] 2.1 Add `process_text_label` (`Extract event`) to `androidApp/res/values/strings.xml` and declare an exported `<activity-alias>` targeting `.MainActivity` with `<intent-filter>` for `android.intent.action.PROCESS_TEXT`, `android.intent.category.DEFAULT`, and `text/plain` in `androidApp/src/AndroidManifest.xml`. Verify the manifest merges and compiles via `./kotlin.bat build`.
- [x] 2.2 Update `MainActivity` in `androidApp/src/MainActivity.kt` to extract text from both `Intent.ACTION_PROCESS_TEXT` (`getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()`) and `Intent.ACTION_SEND` (`getStringExtra(Intent.EXTRA_TEXT)`), and consume each incoming intent once (`savedInstanceState == null` + consumed marker on the intent) so configuration changes do not re-trigger extraction. Verify compilation and tests via `./kotlin.bat check`.

## 3. End-to-End Verification

- [x] 3.1 Run `./kotlin.bat check` and `./kotlin.bat build` to confirm all shared unit tests and Android/KMP builds pass.
- [ ] 3.2 On an Android device or emulator, highlight text in another app, verify the `"Extract event"` chip appears in the text-selection context menu, tap it from cold start, warm start (while on `ModelSetup`), and after rotation, and confirm extraction auto-starts once per selection; also verify Share sheet (`ACTION_SEND`) auto-starts extraction.
