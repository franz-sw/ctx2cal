# Spec Delta

## MODIFIED Requirements

### Requirement: Structured single-event output

The system SHALL prompt the on-device model with the pasted text plus today's date, device timezone, and single-event JSON examples (including a date-only all-day example), and SHALL validate the response as one event (title, start, end, description, location). Validation SHALL infer `allDay` as true when both `start` and `end` are valid date-only values (`YYYY-MM-DD`) and false otherwise (unless explicitly provided as `true` in JSON), SHALL normalize `T24:00:00` end-of-day timestamps, and SHALL reject impossible dates, mixed date/datetime forms, and non-primitive JSON values. A retry SHALL run at most once on validation failure (not on timeout or cancel) with bounded context, else report failure without creating a draft.

#### Scenario: Relative dates resolve against today

- **WHEN** the text contains a relative date phrase such as "next Friday"
- **THEN** the extracted event's date reflects the injected today/timezone reference

#### Scenario: Date-only start and end infer all-day

- **WHEN** the model returns date-only `start` and `end` values without an `allDay` field
- **THEN** the system sets `allDay` to `true` on the extracted draft

#### Scenario: Date-only start with empty end stays partial non-all-day

- **WHEN** the model returns a date-only `start` and an empty `end` without an `allDay` field
- **THEN** the system sets `allDay` to `false` so the draft remains a partial extraction requiring user completion

#### Scenario: End-of-day 24:00 timestamp is normalized

- **WHEN** the model returns `00:00:00` to `24:00:00` on the same calendar date
- **THEN** the system normalizes it to an all-day draft for that date instead of rejecting `24:00:00`

#### Scenario: Invalid model output is rejected

- **WHEN** the model returns malformed JSON or an event that fails validation
- **THEN** the system retries at most once with bounded context and otherwise reports a failure without creating a draft

#### Scenario: Non-primitive field is rejected, not crashed

- **WHEN** the model returns a JSON object/array for a scalar field such as location
- **THEN** the system treats the output as invalid, retries once, and creates no draft

#### Scenario: Impossible calendar date is rejected

- **WHEN** the model returns a date component outside valid calendar bounds such as month 99 or day 99
- **THEN** the system rejects the output as invalid without creating a draft

#### Scenario: Oversized input text is bounded

- **WHEN** the user pastes text exceeding the prompt character limit
- **THEN** the system truncates the pasted text to fit within the bounded prompt budget before running inference
