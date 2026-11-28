# Design

## Context

See `proposal.md` for motivation. Currently:
- `EventJsonParser` (`shared/src/extract/domain/EventJsonParser.kt`) includes `"allDay"` in the LLM JSON schema and examples, and `buildSystemInstruction()` only shows `"allDay": false` examples.
- `IsoDateTime.isValid()` (`shared/src/core/domain/model/CalendarEventDraft.kt`) strictly enforces `0..23` hours, rejecting `T24:00:00`.
- `IcsBuilder.build()` (`shared/src/core/data/ics/IcsBuilder.kt`) and `AndroidCalendarSharer.resolveMillis()` (`shared/src@android/core/data/calendar/AndroidCalendarSharer.kt`) duplicate an `if (end <= start) nextDate(start) else end` rule that treats single-day all-day `endIso` as inclusive and multi-day `endIso` as exclusive.

## Goals / Non-Goals

**Goals:**
- Remove `"allDay"` from the LLM prompt/schema so on-device models express all-day vs timed events purely via date (`YYYY-MM-DD`) vs datetime (`YYYY-MM-DDTHH:mm:ss`) formats.
- Infer `allDay` in `EventJsonParser` using Option B: `true` when both `start` and `end` are valid date-only strings, `false` when `end` is empty (preserving partial extraction).
- Normalize `T24:00:00` before `IsoDateTime.isValid()` checks in `EventJsonParser` while keeping `IsoDateTime.isValid("...T24:00:00") == false` for strict calendar validation.
- Make `CalendarEventDraft.endIso` for all-day events unambiguously **inclusive** across UI, parser, `.ics` export, and Android calendar share.

**Non-Goals:**
- Removing `CalendarEventDraft.allDay` or the "All day" checkbox from `ExtractScreen`.
- Changing `IsoDateTime.isValid()` to accept `24:00:00` globally (strict validation remains unchanged).
- Auto-filling missing `end` on partial events (e.g. *"Dinner Friday, time TBD"* stays `isValid == false` so the user completes it).

## Decisions

- **Option B for `allDay` inference in `EventJsonParser.parse()`**:
  - *Decision*: `val inferredAllDay = explicitAllDayTrue || (startRaw.isNotEmpty() && endRaw.isNotEmpty() && IsoDateTime.isDateOnly(startRaw) && IsoDateTime.isDateOnly(endRaw))`.
  - *Rationale*: Distinguishes true all-day events (`"2026-11-28"` to `"2026-11-28"`) from partial timed events where time is unknown (`"2026-11-27"` to `""`). Honoring an explicit `"allDay": true` preserves backward compatibility with custom user-saved system prompts.
  - *Alternative considered*: Option A (any date-only `start` -> `allDay = true`), rejected because it conflates "time unknown" partial drafts with all-day events.

- **Pre-validation normalization of `T24:00:00` in `EventJsonParser`**:
  - *Decision*: Normalize `T24:00:00` (or ` 24:00:00` / `T24:00`) in `EventJsonParser.parse()` *before* `IsoDateTime.isValid()`:
    - If `startRaw` is `YYYY-MM-DDT00:00:00` (or `00:00`) and `endRaw` is `YYYY-MM-DDT24:00:00` on the same date, collapse to date-only `start = date`, `end = date` (which then infers `allDay = true`).
    - Otherwise, roll `YYYY-MM-DDT24:00:00` over to `IsoDateTime.nextDate(date) + "T00:00:00"`.
  - *Rationale*: Recovers from small-model `24:00` hallucinations without weakening `IsoDateTime.isValid()` or `CalendarEventDraft` validation.

- **Prompt & Title cleanup in `EventJsonParser`**:
  - *Decision*: Remove `"allDay"` from `Schema:` and `Examples:` in `buildSystemInstruction()`, add a concise rule (`For all-day events, use YYYY-MM-DD for both start and end (inclusive last day). Never use 24:00.`), and include both a timed example and a date-only all-day example. Also collapse whitespace in `title` via `.replace(Regex("\\s+"), " ").trim()`.
  - *Note*: Existing tests in `EventJsonParserTest` that assert legacy prompt strings (`"Never guess"`, `"Example:"`, `"Partial example:"`) will be aligned with the current/updated prompt wording.

- **Single inclusive-to-exclusive boundary helper**:
  - *Decision*: Add `CalendarEventDraft.exclusiveAllDayEndDate(): String` (returning `IsoDateTime.nextDate(IsoDateTime.toDateOnly(endIso))`) and call it from both `IcsBuilder.build()` and `AndroidCalendarSharer.resolveMillis()`.
  - *Rationale*: Eliminates duplicated logic and fixes the off-by-one bug for multi-day all-day events.

## Risks / Trade-offs

- **[Risk] Existing custom system prompt saved by a user still mentions `"allDay"`** -> Mitigation: `EventJsonParser.parse()` still accepts `"allDay": true` if present, so custom prompts do not break.
- **[Risk] `IcsBuilderTest` and `EventJsonParserTest` have assertions tied to the old prompt/exclusive-`endIso` behavior** -> Mitigation: Update those unit tests explicitly as part of the change tasks and run `.\kotlin.bat test -m shared`.
