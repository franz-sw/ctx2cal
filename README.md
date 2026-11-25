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
| Gemini Nano (ML Kit GenAI / AICore) | Supported Android devices with AICore + RAM for the system model; selected by default when available. |
| LiteRT-LM (`.litertlm`) | 64-bit Android (`arm64-v8a` / `x86_64`) with sufficient RAM and storage; uses GPU acceleration when supported and automatically caches CPU fallback on older GPUs. |
| iOS | Not supported in v1 — the UI degrades cleanly to an unsupported state. |

### Open-model catalog & hardware checks

The Setup screen evaluates device hardware (ABI, total RAM, free internal/external storage, Android SDK, SoC, and GLES/Vulkan GPU signals) before offering downloads from a curated **Apache-2.0 ungated** catalog on `litert-community`:

| Model | File | Approx. size | Min RAM |
|---|---|---|---|
| **Qwen3 0.6B INT4** | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | ~343 MB | 4 GB |
| **Qwen3 1.7B INT4** | `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm` | ~977 MB | 6 GB |
| **Gemma 4 E2B** | `gemma-4-E2B-it.litertlm` | ~2.59 GB | 8 GB |
| **Gemma 4 E4B** | `gemma-4-E4B-it.litertlm` | ~3.66 GB | 12 GB |

- **Storage**: Each model is downloaded via `DownloadManager` and stored independently at `filesDir/models/<id>.litertlm` so multiple models can be kept and switched (including switching back to Gemini Nano). Deleting a model requires confirmation in an alert dialog.
- **Suitability badges**: Models are labeled `Recommended`, `CPU only · may be slow` (older GPUs/SoCs or cached GPU fallback), or `Incompatible` (download disabled when ABI/RAM/storage requirements are not met). When neither Gemini Nano nor a GPU-recommended model is available, Setup displays a top-level warning banner.
- **Bounded inference**: LiteRT-LM runs through `Conversation` with model chat templates, disabled thinking tokens, deterministic sampling, capped input/context/output budgets, finite per-attempt timeouts, and native `cancelProcess()` on timeout or user cancellation.

### Why no Koog?

Koog is a general-purpose LLM framework: it has **no ML Kit GenAI client** (Gemini Nano), its LiteRT-LM client is beta-grade, and its abstraction level is wrong for single-shot structured extraction. v1 needs exactly one thing — one JSON event per paste, on-device — so the app talks to ML Kit/LiteRT directly behind a single `EventExtractor` port and validates the JSON strictly (one retry, then fail). A Koog adapter could be added behind that port later if a second provider appears.

### Flow

Paste text → Extract (Nano > LiteRT > no-model error) → edit all fields in place → **Share to calendar** (ACTION_INSERT, user picks the calendar app, no permission) or **Download .ics** (MediaStore → Downloads, RFC 5545).
