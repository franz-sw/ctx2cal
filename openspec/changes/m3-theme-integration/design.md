# Design

## Context

See proposal.md for motivation. Current state: `App.kt:41` uses bare `MaterialTheme { }` (M3 defaults). `core/presentation/design_system/theme/AppTheme.kt` exists but is unused; `Style.kt:30-31` declares `expect` light/dark color schemes with no `actual`s anywhere. `Colors.kt` mixes `md_theme_*` indigo with ~100 legacy Sachkunde colors, `Color.kt` exposes quiz-specific `UiColor`, and `FullWidthDialog.kt` imports a non-existent `de.franzsw.sachkunde.core.domain.logger.DefaultLogger`. Screens (`ExtractScreen`, `ModelSetupScreen`) use raw M3 widgets with no design-system components. Package root is `com.example.ctx2cal` for the app and `de.franzsw.sachkunde.*` for the design system.

## Goals / Non-Goals

**Goals:**
- Wire the existing `AppTheme` + `md_theme_*` indigo palette through the whole app (light/dark).
- Adopt design-system tokens/components on Extract + Setup screens without changing ViewModel contracts or screen logic.
- Prune Sachkunde legacy so the design system is calendar-app-only.
- Unify package root to `de.franzsw.ctx2cal.*` (breaking).

**Non-Goals:**
- New seed/dynamic color, custom fonts, full custom type/shapes, edge-to-edge, new screens, or ViewModel/logic changes.

## Decisions

### Theming host: `AppTheme` in `App.kt` (rejected: bare `MaterialTheme` keep)
Replace bare `MaterialTheme { }` with `AppTheme(darkTheme = isSystemInDarkTheme()) { }` wrapping `KoinApplication`. This makes the whole Navier/Scaffold tree inherit the scheme with one change and keeps dark-mode logic in one place. Adding per-screen themes was rejected — it would fragment dark/light state.

### Color actuals: strict `md_theme_*` via existing `default*Scheme()` (rejected: legacy surface overrides)
Implement `actual val Light/DarkColorScheme` in `src@android` and `src@ios` by delegating to `defaultLightColorScheme()` / `defaultDarkColorScheme()` with their default `md_theme_*` surfaces (`0xFFFEFBFF` light / `0xFF1B1B1F` dark). The legacy `surface` params (`BackgroundAccent1Light` peach, `BoxGrey`) are ignored because they conflict with the indigo seed and are Sachkunde leftovers. Rejected alternative: re-tuning surfaces — no new brand decision was requested.

### Typography: keep `bodyLarge` override only (rejected: full custom scale + shapes)
Keep the existing `Typography = bodyLarge 16.sp/24.sp` and let remaining roles fall back to M3 defaults. `LCHeadline` (headlineSmall/primary), dialogs (titleMedium), and labels (labelMedium) already work against M3 defaults, so a full scale adds churn without a brand-font request. Shapes stay on M3 defaults; components already default to `CardDefaults.elevatedShape`.

### Component adoption: keep the 5 generic components (rejected: blanket migration + blanket delete)
Keep and adopt: `LCHeadline`, `HintCard`/`ErrorCard`, `CustomElevatedCard`, `DefaultLoadingScreen`, and the action-button pair. Map: Extracting -> `DefaultLoadingScreen`; Error/no-model -> `ErrorCard`/hint; `EventForm` -> `CustomElevatedCard`; primary CTAs (`Extract event`, `Share to calendar`) -> action button. Delete/park the rest (`FullWidthDialog`, `SachkundeAlertDialog`, `CommonWebView`, `PlatformContainer`, `FormatedContent`, tab/radial quiz components). Rationale: kept components only depend on `colorScheme`+`typography`; deleted ones carry missing `actual`s, missing logger, quiz semantics, or German strings (`Warnung`, `Bestätigen`) that don't match the English UI.

### Component naming + language: neutral `App*` names, English strings (rejected: keep `Sachkunde*`)
Rename `SachkundeActionButton`/`SachkundeAlertDialog` (if kept) to neutral `App*` and translate hardcoded German to English. The app's UI is English (e.g., "Paste text containing one event"); preserving the `Sachkunde` prefix leaks a prior product's brand into a calendar app. This is cosmetic at the API level — tasks rename in place with no behavior change.

### Package rename: unify to `de.franzsw.ctx2cal.*` (rejected: keep two roots)
Move every `com.example.ctx2cal.*` package/import to `de.franzsw.ctx2cal.*`, matching the design system. Update `androidApp/module.yaml` (`namespace` + `applicationId`), iOS `PRODUCT_BUNDLE_IDENTIFIER` (`de.franzsw.ctx2cal.ContextToCalendar`), and Compose resources `packageName` (`de.franzsw.ctx2cal.resources`). Update `AGENTS.md`/`README.md` references. Rejected alternative (keep `com.example` + `sachkunde` roots) preserves the drift `design.md:86` already flagged.

### Prune scope: delete legacy palette (rejected: keep for compat)
Shrink `Colors.kt` to `md_theme_*` only; delete `UiColor.getColor()` legacy vals and `ColorToHEX` if unused after grep. Delete the broken `DefaultLogger` import by deleting `FullWidthDialog` rather than inventing a logger. No screen references legacy colors today, so deletion is safe.

## Risks / Trade-offs

- [Risk] Rename changes Android identity → Mitigation: accepted as breaking; old install/settings orphaned, document in release notes; fresh install expected pre-release.
- [Risk] Nav3 saved back stacks serialized under old FQ names won't restore after rename → Mitigation: acceptable one-time process-death edge; no migration.
- [Risk] `actual` schemes duplicated in android/ios → Mitigation: both delegate to the same `default*Scheme()`; drift impossible.
- [Risk] Button metrics change (action buttons are max-400dp full-width with inline spinner) → Mitigation: verify Extract/Review rows on a device; fall back to plain themed `Button` if layout regresses.
- [Trade-off] Keeping `bodyLarge`-only typography limits brand control → Mitigation: deliberate; revisit only if a custom font is ever requested.

## Migration Plan

Single change, no data migration (settings only). Rollback: revert the change's files; old `applicationId` install would need reinstall if the new ID was already installed.

## Open Questions

- Edge-to-edge (`enableEdgeToEdge` in `MainActivity`) intentionally deferred — does not affect theme contract; decide separately.
- Fine-grained sizes/padding per component (e.g., `HintCard` 10.dp radius vs M3 default) — tuned after visual check, not a spec blocker.
