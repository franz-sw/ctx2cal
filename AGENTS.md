# ContextToCalendar — agent instructions

## Stack

- Kotlin Multiplatform (KMP) with Compose Multiplatform on **Amper** (`kotlin` / `kotlin.bat` CLI).
- Not a Gradle project — there is no `build.gradle`, no `settings.gradle`, no Gradle wrapper.
- Builds and runs go through the Amper CLI (`./kotlin.bat` on Windows).

## Layout

- `project.yaml` — Amper project manifest (lists modules).
- `shared/` — `kmp/lib`, Compose UI for Android + iOS (`de.franzsw.ctx2cal`).
  - `shared/src/App.kt` — main Compose entry.
  - `shared/src/Platform.kt` + `src@android/` + `src@ios/` — `expect`/`actual`.
- `androidApp/` — `android/app`, single `MainActivity` hosting `shared.App()`.
  - `androidApp/module.yaml` — namespace / applicationId.
- `iosApp/` — `ios/app`, Compose + Swift interop.
- `libs.versions.toml` — version catalog for shared libs.
- `build/` — Amper output (gitignored, never edit by hand).

## Rules

- Do not add Gradle files or convert to Gradle without explicit request.
- Edit `module.yaml` / `libs.versions.toml` for deps, not generated `build/` output.
- Keep `shared/` platform-agnostic: platform APIs only in `src@android/` / `src@ios/`.
- Verify with the CLI, e.g. `.\kotlin.bat check` / `.\kotlin.bat build` where applicable.

## Agents

- **`fast` (`openrouter/meta/muse-ember`)**: Delegate straightforward (if you are not already muse ember or glm3), well-scoped tasks that don't require deep reasoning to the `fast` subagent — e.g., boilerplate, mechanical/repetitive edits across files, simple fixes, `module.yaml` / `libs.versions.toml` updates, or running `.\kotlin.bat check` / `.\kotlin.bat build`. Keep complex architecture, non-trivial debugging, and ambiguous design work in the primary agent.
