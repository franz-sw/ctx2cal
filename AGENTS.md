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

Subagents run on much cheaper models (`openrouter/meta/muse-ember`), so delegating well-scoped work is encouraged to keep costs low and the primary context focused. Treat delegation as a judgment call rather than a hard rule: delegate when a task is cleanly separable, and handle it directly when a quick single-step edit is simpler or you are already running on a low-cost model (e.g. `muse-ember` or `glm3`).

- **Parallelize when independent**: Spawn multiple subagents simultaneously for non-overlapping work (e.g. parallel `explore` lookups across different areas, or concurrent `fast` tasks touching disjoint files/modules). Avoid parallel writes to the same files.
- **`explore`** (`openrouter/meta/muse-ember`, read-only): Codebase research, tracing behavior, and web/docs lookups before editing.
- **`fast`** (`openrouter/meta/muse-ember`): Straightforward, well-scoped tasks — boilerplate, mechanical/repetitive edits, simple fixes, `module.yaml` / `libs.versions.toml` updates, and running Amper checks (`.\kotlin.bat check`, `.\kotlin.bat build`).
- **`test`** (`openrouter/meta/muse-ember`): Writing, updating, and running unit/UI tests and test doubles via Amper (`.\kotlin.bat test`, `.\kotlin.bat check`).
- **`review`** (`openrouter/meta/muse-ember`, read-only): Reviewing diffs/files for KMP/Amper rule violations, bugs, Compose pitfalls, and missing tests before committing.
- **`commit`** (`openrouter/meta/muse-ember`, git-only): Creating commits, pushing branches, and opening pull requests.
- **Primary agent**: Keep complex architecture, non-trivial debugging, ambiguous design decisions, and integration/review of subagent work in the primary agent.
