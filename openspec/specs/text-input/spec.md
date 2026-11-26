# text-input Specification

## Purpose

Lets users paste free-form text containing a date or event description and get one editable calendar event back on the Extract screen.

## Requirements

### Requirement: Paste text and start extraction

The system SHALL let the user paste multi-line text on the Extract screen and trigger extraction of exactly one event draft from that text. Extraction and download SHALL be single-flight and SHALL allow user cancellation, and editing the draft SHALL clear any prior saved/export notice.

#### Scenario: Successful extraction pre-fills review

- **WHEN** the user pastes text and taps Extract and a model is available
- **THEN** the system shows a loading state, then reveals the editable event form pre-filled from the extracted draft

#### Scenario: Re-extract after editing text

- **WHEN** the user edits the pasted text and taps Extract again
- **THEN** the system replaces the current draft with the new extraction result and keeps the edited text visible

#### Scenario: Cancel extraction

- **WHEN** the user taps Cancel while extraction is loading
- **THEN** the system returns to the input state with the pasted text intact

### Requirement: Combined input and review screen

The system SHALL present text input, extraction status, and the editable event form on the same Extract screen without requiring navigation between input and review.

#### Scenario: Review actions available in place

- **WHEN** the editable form is showing
- **THEN** the user can edit fields and invoke share or download without leaving the screen

#### Scenario: State survives rotation and process death

- **WHEN** the device rotates or the process is killed and restored during input or review
- **THEN** both the pasted text and the current draft are restored

### Requirement: Extraction failure feedback

The system SHALL show an inline error when extraction is unavailable or yields no event, with a path to Model Setup when no model is available. A no-event result SHALL retain the pasted text and show an explanatory notice rather than silently resetting to input.

#### Scenario: No model available

- **WHEN** the user taps Extract and no on-device model is available
- **THEN** the system shows an error stating no model is available and offers a button that opens Model Setup

#### Scenario: No event found in text

- **WHEN** extraction completes but the text contains no recognizable event or date
- **THEN** the system shows a notice stating no event was found and retains the pasted text for editing

### Requirement: Text-selection context menu action

The system SHALL register an Android plain-text process-text entry point labeled `"Extract event"` so the app appears in the system text-selection context menu across external apps and delivers the selected text to the app.

#### Scenario: Context menu chip launches app with selected text

- **WHEN** the user selects text in another Android app and taps `"Extract event"` in the text-selection context menu
- **THEN** ContextToCalendar opens with the selected plain text ready for extraction

#### Scenario: Blank text-selection payload is ignored

- **WHEN** the app is launched via the text-selection context menu with blank or missing text
- **THEN** the app opens without modifying existing input text or starting extraction

### Requirement: Auto-start extraction on incoming external text

When non-blank text arrives from the text-selection context menu or the system share sheet, the system SHALL navigate to the Extract screen if another screen is currently shown, cancel any in-flight extraction, populate the input with the trimmed text, and immediately start extraction.

#### Scenario: Auto-extraction starts from cold or warm launch

- **WHEN** the app receives non-blank external text via the text-selection context menu or share sheet
- **THEN** the Extract screen displays the loading state for extraction of the trimmed text without requiring the user to tap Extract

#### Scenario: Incoming text interrupts Model Setup or active extraction

- **WHEN** the app is on the Model Setup screen or actively extracting earlier text and receives new external text
- **THEN** the system returns to the Extract screen, cancels any prior extraction, and starts extracting the newly received text

#### Scenario: Auto-extraction with no model available

- **WHEN** external text triggers auto-extraction while no on-device model is available
- **THEN** the Extract screen preserves the received text and displays the no-model error with a path to Model Setup

### Requirement: Single-delivery of external text intents

The system SHALL consume each incoming external text launch intent once so configuration changes or activity recreation do not re-trigger extraction for the same text.

#### Scenario: Device rotation after incoming text launch

- **WHEN** the device rotates after an external text launch has already initiated extraction
- **THEN** the system does not restart extraction or discard the current extraction or review state
