# ContextToCalendar

Kotlin Multiplatform + Compose Multiplatform app (Android + iOS), built with **Amper** (JetBrains).

> This is **not** a Gradle project — there is no `build.gradle` / `settings.gradle`. Builds go through the Amper CLI in this repo root.

## Layout

| Path | What it is |
|---|---|
| `project.yaml` | Amper project manifest (lists modules) |
| `shared/` | `kmp/lib` — Compose UI, package `com.example.ctx2cal` (`App.kt`, `Platform.kt` expect/actual) |
| `androidApp/` | `android/app` — `MainActivity` hosting `shared.App()`; namespace `com.example.ctx2cal` |
| `iosApp/` | `ios/app` — Compose + Swift, `ViewController` |
| `libs.versions.toml` | Version catalog |
| `opencode.jsonc` + `AGENTS.md` | OpenCode V2 project config + agent instructions |
| `openspec/` | OpenSpec specs / changes |

## Run

Windows (PowerShell/CMD):

```bat
kotlin.bat --help
kotlin.bat check
kotlin.bat build
```

macOS/Linux: `./kotlin --help`, `./kotlin check`, `./kotlin build`.

Open in Android Studio as an Amper project (not a Gradle sync).

## OpenCode

Project config lives in `opencode.jsonc` (schema `https://opencode.ai/config.json`) with instructions from `AGENTS.md`. Config reloads automatically on save; use `/reload` to force a full reload. Do **not** restart OpenCode after config edits.
