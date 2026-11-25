# Spec Delta

## Purpose

Defines the on-device LLM contract that turns pasted text into one structured calendar-event draft, with provider selection and strict output validation.

## ADDED Requirements

### Requirement: Structured single-event output

The system SHALL prompt the on-device model with the pasted text plus today's date and device timezone, and SHALL validate the model's response as exactly one event with title, start, end, all-day flag, description, and location.

#### Scenario: Relative dates resolve against today

- **WHEN** the text contains a relative date phrase such as "next Friday"
- **THEN** the extracted event's date reflects the injected today/timezone reference

#### Scenario: Invalid model output is rejected

- **WHEN** the model returns malformed JSON or an event that fails validation
- **THEN** the system retries at most once and otherwise reports a failure without creating a draft

### Requirement: Provider selection and fallback

The system SHALL use the auto-detected device system model as the primary extraction backend, fall back to a downloaded open model when the system model is unavailable, and report an error when neither is available.

#### Scenario: System model preferred

- **WHEN** both the system model and a downloaded open model are available
- **THEN** extraction uses the system model

#### Scenario: Open-model fallback

- **WHEN** the system model is unavailable and a downloaded open model is ready
- **THEN** extraction uses the downloaded open model

### Requirement: On-device privacy and foreground use

The system SHALL run extraction entirely on-device with no network calls carrying user text, and SHALL surface an error when extraction is requested while the app is not in the foreground.

#### Scenario: Background extraction blocked

- **WHEN** extraction is attempted while the app is not in the foreground on the system-model backend
- **THEN** the system reports a recoverable error rather than running inference
