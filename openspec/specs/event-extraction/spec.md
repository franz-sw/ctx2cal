# event-extraction Specification

## Purpose

Defines the on-device LLM contract that turns pasted text into one structured calendar-event draft, with provider selection and strict output validation.

## Requirements

### Requirement: Structured single-event output

The system SHALL prompt the on-device model with the pasted text plus today's date and device timezone, and SHALL validate the model's response as exactly one event with title, start, end, all-day flag, description, and location. Validation SHALL reject impossible calendar dates and mixed date/datetime forms, and SHALL treat non-primitive JSON values for any field as invalid rather than crashing. A retry SHALL be attempted at most once and SHALL include the original text/context, and otherwise the system SHALL report a failure without creating a draft.

#### Scenario: Relative dates resolve against today

- **WHEN** the text contains a relative date phrase such as "next Friday"
- **THEN** the extracted event's date reflects the injected today/timezone reference

#### Scenario: Invalid model output is rejected

- **WHEN** the model returns malformed JSON or an event that fails validation
- **THEN** the system retries at most once with the original context and otherwise reports a failure without creating a draft

#### Scenario: Non-primitive field is rejected, not crashed

- **WHEN** the model returns a JSON object/array for a scalar field such as location
- **THEN** the system treats the output as invalid, retries once, and creates no draft

#### Scenario: Impossible calendar date is rejected

- **WHEN** the model returns a date component outside valid calendar bounds such as month 99 or day 99
- **THEN** the system rejects the output as invalid without creating a draft

### Requirement: Provider selection and fallback

The system SHALL use the auto-detected device system model as the primary extraction backend, fall back to a downloaded open model when the system model is unavailable or fails at runtime, and report an error when neither is available. On platforms without on-device support the system SHALL report an unsupported error distinct from a no-model error.

#### Scenario: System model preferred

- **WHEN** both the system model and a downloaded open model are available
- **THEN** extraction uses the system model

#### Scenario: Open-model fallback

- **WHEN** the system model is unavailable and a downloaded open model is ready
- **THEN** extraction uses the downloaded open model

#### Scenario: Runtime fallback

- **WHEN** the system model is available but its extraction fails at runtime and a downloaded open model is ready
- **THEN** extraction attempts the downloaded open model before reporting failure

#### Scenario: Unsupported platform

- **WHEN** extraction is requested on a platform without on-device support
- **THEN** the system reports that extraction is unsupported without offering Model Setup as a remedy

### Requirement: On-device privacy and foreground use

The system SHALL run extraction entirely on-device with no network calls carrying user text, SHALL surface an error when extraction is requested while the app is not in the foreground, and SHALL leave cancellation semantics unchanged by rethrowing cancellation rather than converting it into a failure.

#### Scenario: Background extraction blocked

- **WHEN** extraction is attempted while the app is not in the foreground on the system-model backend
- **THEN** the system reports a recoverable error rather than running inference

#### Scenario: Cancellation is not a failure

- **WHEN** the user cancels an in-flight extraction
- **THEN** the system returns to input state without showing an extraction-failure error
