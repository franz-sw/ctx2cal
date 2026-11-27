# Proposal

## Why

Small on-device models (Gemini Nano and LiteRT) frequently struggle with the `"allDay"` boolean in the extraction JSON—emitting `"allDay": false` alongside synthetic `00:00:00` to `24:00:00` times that fail `IsoDateTime` validation, because the prompt examples only show `"allDay": false`. Meanwhile, the internal `CalendarEventDraft` treats multi-day all-day `endIso` inconsistently (`end <= start` is treated as inclusive while `end > start` is treated as already exclusive), causing multi-day all-day events to lose a day on export.

## What Changes

- Remove `"allDay"` from the LLM extraction JSON schema and prompt; let the model emit date-only `YYYY-MM-DD` strings when no time is present.
- Infer `allDay` in `EventJsonParser` using **Option B**:
  - `allDay = true` when both `start` and `end` are valid date-only (`YYYY-MM-DD`) values.
  - `allDay = false` when `end` is empty (preserving partial-extraction behavior for events like *"Dinner Friday, time TBD"*).
  - If a legacy/custom prompt or model still emits `"allDay": true`, continue honoring it; if a model emits `T24:00:00`, normalize it (same-day `00:00:00`..`24:00:00` becomes `allDay = true` with `start = end = date`; otherwise `T24:00:00` rolls over to next-day `T00:00:00`).
- Standardize `CalendarEventDraft` all-day `endIso` semantics to **inclusive last day** across the app, converting to RFC 5545 / `CalendarContract` exclusive `DTEND` (+1 day) in one shared helper used by both `IcsBuilder` and `AndroidCalendarSharer`.
- Collapse multi-whitespace / literal newlines in extracted `title` so stray line breaks from model output do not pollute single-line titles.
- Keep the user-facing **All day** checkbox in `ExtractScreen` / `CalendarEventDraft` unchanged.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `event-extraction`: Remove `allDay` from the LLM JSON contract (infer `allDay` from date-only `start` and `end`), add `T24:00:00` normalization, and update prompt examples to include a date-only all-day example.
- `ics-download`: Clarify that multi-day all-day drafts use inclusive `endIso` and export an exclusive `DTEND;VALUE=DATE` (`endIso + 1 day`).

## Impact

- `shared/src/extract/domain/EventJsonParser.kt` (prompt schema/examples, `parse()`, `T24:00:00` normalization, title whitespace cleanup)
- `shared/src/core/domain/model/CalendarEventDraft.kt` (shared `exclusiveAllDayEndDate()` helper)
- `shared/src/core/data/ics/IcsBuilder.kt` (use `exclusiveAllDayEndDate()`)
- `shared/src@android/core/data/calendar/AndroidCalendarSharer.kt` (use `exclusiveAllDayEndDate()`)
- `shared/test/EventJsonParserTest.kt`, `shared/test/IcsBuilderTest.kt` (and any affected tests in `ModelSetupViewModelTest`, `ModelTestViewModelTest`, `ExtractViewModelTest`)
