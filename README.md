# ContextToCalendar

Kotlin Multiplatform + Compose Multiplatform app (Android + iOS), built with **Amper** (JetBrains).

> This is **not** a Gradle project — there is no `build.gradle` / `settings.gradle`. Builds go through the Amper CLI in this repo root.

## Layout

| Path | What it is |
|---|---|
| `project.yaml` | Amper project manifest (lists modules) |
| `shared/` | `kmp/lib` — Compose UI, package `de.franzsw.ctx2cal` (`App.kt`, `Platform.kt` expect/actual) |
| `androidApp/` | `android/app` — `MainActivity` hosting `shared.App()`; namespace `de.franzsw.ctx2cal` |
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

## v1 on-device extraction

ContextToCalendar turns a pasted text block into one editable calendar-event draft **entirely on-device** — no cloud LLM calls, no network carrying user text.

### Supported devices

| Backend | Requirement |
|---|---|
| Gemini Nano (ML Kit GenAI / AICore) | Pixel-class device with AICore + RAM for the system model; Gemini Nano availability varies by device, OS, and RAM — the app never blocks on it. |
| LiteRT-LM (downloaded `.litertlm`) | Any Android device with GPU (GPU backend, declared via manifest native libraries) + internet for the model download. |
| iOS | Not supported in v1 — the UI degrades to a no-model error instead of crashing. |

### Model download

The fallback model is downloaded **once**, via `DownloadManager` (platform-standard path), into internal storage:

- File: `filesDir/models/model.litertlm`
- Default candidate: **Qwen3 0.6B** (`.litertlm`, from the `litert-community` Hugging Face repo) — a small model chosen over larger Gemma/Qwen variants to keep the download tractable; expect roughly **0.4 GB**.
- Gated repos need a Hugging Face token — the current placeholder URL is **not** gated; fix the exact repo/URL before shipping (see "Open questions" below).
- Progress is shown in the Setup screen; on completion the path + `litertlm` backend are persisted (SharedPreferences) and survive restart.

> **Open question (from design):** which exact downloadable model + Hugging Face repo/token handling is the v1 default? The candidate URL in `ModelDownloaderAndroid.kt` is a placeholder; confirm + pin it before release.

### Why no Koog?

Koog is a general-purpose LLM framework: it has **no ML Kit GenAI client** (Gemini Nano), its LiteRT-LM client is beta-grade, and its abstraction level is wrong for single-shot structured extraction. v1 needs exactly one thing — one JSON event per paste, on-device — so the app talks to ML Kit/LiteRT directly behind a single `EventExtractor` port and validates the JSON strictly (one retry, then fail). A Koog adapter could be added behind that port later if a second provider appears.

### Flow

Paste text → Extract (Nano > LiteRT > no-model error) → edit all fields in place → **Share to calendar** (ACTION_INSERT, user picks the calendar app, no permission) or **Download .ics** (MediaStore → Downloads, RFC 5545).
