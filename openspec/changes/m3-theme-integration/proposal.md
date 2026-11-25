# Proposal

## Why

The app ships with a disconnected design system: `App.kt` renders a bare `MaterialTheme` with default M3 colors while the existing `design_system/theme` (`AppTheme`, `md_theme_*` indigo palette, typography, Sizes) is unused dead code — including `expect` color schemes with no `actual`s. This change wires the existing theme through the whole app so both screens use consistent Material 3 light/dark styling.

## What Changes

- Replace bare `MaterialTheme { }` in `shared/src/App.kt` with `AppTheme { }` so the entire UI inherits the existing color scheme and typography.
- Add missing `actual` light/dark color schemes (strict `md_theme_*`, indigo seed `0xFF4958A9` light / `0xFFBAC3FF` dark) in `src@android` and `src@ios`.
- Adopt design-system tokens and components in `ExtractScreen` and `ModelSetupScreen` (headlines, cards/hints, loading, action buttons) without changing screen logic or ViewModel contracts.
- Prune Sachkunde legacy: remove legacy colors (`UiColor`, flashcard/tier/education/question palettes), unused components, and broken references (missing logger, `expect` without `actual`).
- **BREAKING** Rename package root from `com.example.*` to `de.franzsw.ctx2cal.*` across all Kotlin packages, `androidApp` namespace/`applicationId`, iOS bundle ID, and Compose resources `packageName`. Old saved state under the previous FQ names will not restore; the previous `applicationId` is abandoned.

Non-goals: new brand/seed (indigo kept), dynamic color, full custom type scale or shapes beyond existing tokens, edge-to-edge changes, new screens or features.

## Capabilities

### New Capabilities

- `material3-theme`: cohesive Material 3 themed UI — `AppTheme` with light/dark color schemes, typography, and design-system component adoption for Extract and Setup screens.

### Modified Capabilities

None — `openspec/specs/` is empty (v1 deltas in `v1-local-extraction` are not yet synced); no existing spec-level behavior changes.

## Impact

- **Code**: `shared/src/App.kt`, `core/presentation/design_system/theme/` (actuals + prune), `presentation/extract/`, `presentation/setup/`, platform actuals in `src@android`/`src@ios`.
- **Platform/identity**: Android package `de.franzsw.ctx2cal`, iOS bundle `de.franzsw.ctx2cal.ContextToCalendar`, resources `de.franzsw.ctx2cal.resources`.
- **Dependencies**: none new; M3 already in `shared/module.yaml`.
