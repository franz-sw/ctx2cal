# Spec Delta: event-extraction

## MODIFIED Requirements

### Requirement: Structured single-event output

The system SHALL prompt the on-device model with the pasted text plus today's date, device timezone, and a single-event JSON example, and SHALL validate the response as one event (title, start, end, all-day, description, location). Validation SHALL reject impossible dates, mixed date/datetime forms, and non-primitive JSON values. A retry SHALL run at most once on validation failure (not on timeout or cancel) with bounded context, else report failure without creating a draft.

#### Scenario: Relative dates resolve against today

- **WHEN** the text contains a relative date phrase such as "next Friday"
- **THEN** the extracted event's date reflects the injected today/timezone reference

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

### Requirement: Provider selection and fallback

The system SHALL honor the user's selected active model (system model or a specific downloaded open model), defaulting to the device system model when available or the active downloaded open model otherwise, fall back to a ready alternative when the primary backend fails at runtime, and report an error when neither is available. On platforms without on-device support the system SHALL report an unsupported error distinct from a no-model error.

#### Scenario: System model preferred

- **WHEN** both the system model and a downloaded open model are available and the user has not selected a different active model
- **THEN** extraction uses the system model

#### Scenario: User-selected downloaded model is used

- **WHEN** the user selects a specific downloaded open model as active in Setup
- **THEN** extraction uses that selected downloaded open model

#### Scenario: Open-model fallback

- **WHEN** the system model is unavailable and a downloaded open model is ready
- **THEN** extraction uses the downloaded open model

#### Scenario: Runtime fallback

- **WHEN** the primary backend fails at runtime and an alternative ready model is available
- **THEN** extraction attempts the alternative model before reporting failure

#### Scenario: Unsupported platform

- **WHEN** extraction is requested on a platform without on-device support
- **THEN** the system reports that extraction is unsupported without offering Model Setup as a remedy

## ADDED Requirements

### Requirement: Bounded generation and disabled thinking

On-device open-model inference SHALL execute through chat-templated conversation sessions with thinking mode disabled, deterministic sampling, and explicit context-window and output-token limits.

#### Scenario: Open-model inference bounds output and disables thinking

- **WHEN** extraction runs on a downloaded hybrid-thinking open model
- **THEN** inference disables the thinking budget and caps generated output tokens to a bounded single-event budget

### Requirement: Extraction timeout and native cancellation

Extraction SHALL enforce a finite per-attempt timeout and SHALL actively signal native inference cancellation when timed out or cancelled by the user, without retrying timed-out or cancelled attempts.

#### Scenario: User cancel interrupts active inference

- **WHEN** the user cancels an in-flight extraction on a downloaded open model
- **THEN** the system invokes native generation cancellation and returns to the input state without reporting an error

#### Scenario: Inference timeout surfaces recoverable error

- **WHEN** an extraction attempt exceeds the execution timeout
- **THEN** the system cancels native inference, skips any retry, and displays a recoverable timeout error

### Requirement: Cached GPU acceleration fallback

When GPU backend initialization fails on a device, the open-model extractor SHALL fall back to CPU execution and remember that GPU acceleration is unavailable on subsequent runs.

#### Scenario: GPU initialization failure caches CPU fallback

- **WHEN** GPU backend initialization throws an error during extraction
- **THEN** the extractor falls back to CPU execution and skips GPU initialization on subsequent extractions
