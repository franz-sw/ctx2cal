# Tasks

## 1. Build spike — Amper integration (verify before feature code)

- [x] 1.1 Add deps to `libs.versions.toml` + `shared/module.yaml` (`dependencies@android` for ML Kit GenAI, LiteRT-LM; common for Navigation 3, `kotlinx-serialization`, Koin DSL, coroutines); verify `.\kotlin.bat check` compiles
- [x] 1.2 Enable the kotlinx-serialization compiler plugin under Amper and add a `@Serializable sealed NavKey` route; verify the project builds with the plugin active
- [x] 1.3 Add `androidApp` manifest entries (LiteRT GPU native libraries, INTERNET) and verify the merged manifest contains them via APK inspection

## 2. Domain core (pure Kotlin, testable without a phone)

- [x] 2.1 Add `domain/model` (`CalendarEventDraft`, `ExtractionResult`, `ModelStatus`) and `domain/ports` interfaces; verify `.\kotlin.bat check` compiles with no Android imports in common code
- [x] 2.2 Implement single-event JSON validation/parse (title, start/end ISO, all-day, description, location) with one-retry-then-fail; verify unit tests cover malformed JSON, missing fields, and relative-date references in `test/`
- [x] 2.3 Implement the ICS builder (RFC 5545 escaping, UID, DTSTAMP) as pure Kotlin; verify unit tests cover escaping and all-day events in `test/`

## 3. Android platform actuals

- [x] 3.1 Implement `GeminiNanoExtractor` (ML Kit `GenerativeModel`, availability check, foreground-use error mapping); verify on a supported device it returns a validated draft or a documented error
- [x] 3.2 Implement `LiteRtExtractor` + `ModelDownloader` (DownloadManager, progress, persisted file) and `AndroidModelCatalog` selection (Nano > LiteRT > none); verify end-to-end on a device without the system model with the default small `.litertlm`
- [x] 3.3 Implement `AndroidCalendarSharer` (ACTION_INSERT) and MediaStore `IcsExporter` to Downloads; verify share opens the calendar insert flow pre-filled and `.ics` lands in Downloads on Android 10+
- [x] 3.4 Add iOS unsupported stubs for all ports; verify the iOS target compiles and the Extract screen degrades to a no-model error instead of crashing

## 4. Presentation + DI (Koin, navigation, two screens)

- [x] 4.1 Add Navigation 3 host (`Routes.kt` sealed serializable keys, config overload, `KoinApplication` in `App.kt`, `di/appModule.kt`); verify back navigation and rotation restore the Extract screen state
- [x] 4.2 Implement `ExtractViewModel` (Input | Extracting | Review | Error states) + `ExtractScreen` with in-place `EventForm` and share/download actions; verify VM unit tests (fake extractor) cover success, no-event, and no-model paths
- [x] 4.3 Implement `ModelSetupViewModel` + `ModelSetupScreen` (status, download progress, backend selection, retry); verify on-device it reflects Nano/LiteRT/downloaded states and persists across restart

## 5. Integration verification

- [x] 5.2 Document setup, supported devices, model download size, and the Koog-deferral rationale in `README.md`; verify the documented steps run as written
