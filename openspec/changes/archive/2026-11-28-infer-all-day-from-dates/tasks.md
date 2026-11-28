# Tasks

## 1. Inclusive all-day end-date boundary

- [x] 1.1 Add `CalendarEventDraft.exclusiveAllDayEndDate()` in `shared/src/core/domain/model/CalendarEventDraft.kt` returning `IsoDateTime.nextDate(IsoDateTime.toDateOnly(endIso))`, and add a unit test in `shared/test/CalendarEventDraftTest.kt` verifying single-day and multi-day exclusive end dates pass via `.\kotlin.bat test -m shared --include-classes="de.franzsw.ctx2cal.CalendarEventDraftTest"`.
- [x] 1.2 Update `IcsBuilder.build()` (`shared/src/core/data/ics/IcsBuilder.kt`) and `AndroidCalendarSharer.resolveMillis()` (`shared/src@android/core/data/calendar/AndroidCalendarSharer.kt`) to use `draft.exclusiveAllDayEndDate()`, and update/extend `shared/test/IcsBuilderTest.kt` for multi-day inclusive-to-exclusive `DTEND` so `.\kotlin.bat test -m shared --include-classes="de.franzsw.ctx2cal.IcsBuilderTest"` passes.

## 2. Date-only all-day inference and 24:00 normalization

- [x] 2.1 Update `EventJsonParser.buildSystemInstruction()` in `shared/src/extract/domain/EventJsonParser.kt` to remove `"allDay"` from the JSON schema/examples, instruct the model to use `YYYY-MM-DD` for all-day events (inclusive last day) and never `24:00`, and include a date-only all-day example alongside the timed example; update prompt assertions in `shared/test/EventJsonParserTest.kt`.
- [x] 2.2 Update `EventJsonParser.parse()` to (a) collapse whitespace in `title`, (b) normalize `T24:00:00` / `T24:00` before `IsoDateTime.isValid()` (collapsing same-day `00:00`..`24:00` to date-only all-day, otherwise rolling `24:00` to next-day `00:00:00`), and (c) infer `allDay = true` when both `start` and `end` are date-only (`YYYY-MM-DD`) while keeping `allDay = false` when `end` is empty (or honoring explicit `"allDay": true`). Add unit tests in `shared/test/EventJsonParserTest.kt` covering all cases and verify `.\kotlin.bat test -m shared --include-classes="de.franzsw.ctx2cal.EventJsonParserTest"` passes.

## 3. Verification

- [x] 3.1 Run `.\kotlin.bat test -m shared` and `.\kotlin.bat check` to verify all unit tests and project checks pass with no regressions.
