# Tasks

## 1. Package rename (breaking)

- [x] 1.1 Rename all `com.example.ctx2cal.*` packages/imports to `de.franzsw.ctx2cal.*` (shared + src@android/src@ios + androidApp + iosApp + tests) and verify `grep com.example` returns no matches in source/config
- [x] 1.2 Update `shared/module.yaml` resources `packageName`, `androidApp/module.yaml` namespace/`applicationId`, iOS `PRODUCT_BUNDLE_IDENTIFIER`, and `AGENTS.md`/`README.md` package references and verify `.\kotlin.bat check` compiles

## 2. Theme wiring

- [x] 2.1 Add `actual` Light/DarkColorScheme in `src@android` and `src@ios` delegating to `defaultLight/DarkColorScheme()` with strict `md_theme_*` surfaces and verify `.\kotlin.bat check` compiles for both targets
- [x] 2.2 Wrap app in `AppTheme` (`isSystemInDarkTheme`) in `App.kt` replacing bare `MaterialTheme { }` and verify Extract screen renders indigo primary in light and dark via manual light/dark toggle

## 3. Prune legacy design system

- [x] 3.1 Shrink `Colors.kt` to `md_theme_*` only; delete `UiColor.getColor()` legacy vals and unused components (`FullWidthDialog`, `PlatformContainer`, quiz/legacy cards) and verify `.\kotlin.bat check` compiles with no missing-logger/actual errors
- [x] 3.2 Rename kept components to neutral `App*` names and English strings, and verify no leftover German literals (`Warnung`, `Bestätigen`) in `design_system/`

## 4. Screen adoption

- [x] 4.1 Migrate `ExtractScreen` to tokens + `LCHeadline`/`HintCard`/`ErrorCard`/`DefaultLoadingScreen`/action buttons (Extracting/Error/Review states) and verify manual pass through Input/Extracting/Review/Error in light and dark
- [x] 4.2 Migrate `ModelSetupScreen` to tokens + cards/hints/progress styling and verify manual pass through Loading/Failed/Ready + download progress in light and dark

## 5. Integration verification

- [x] 5.1 Run `.\kotlin.bat check` and `openspec validate m3-theme-integration` and verify both pass; run existing unit tests and verify no regression
- [x] 5.2 Check action-button layout (max-400dp + inline spinner) on a device for Extract/Share rows and verify no overflow/regression vs plain `Button`; fallback to plain themed `Button` if needed
