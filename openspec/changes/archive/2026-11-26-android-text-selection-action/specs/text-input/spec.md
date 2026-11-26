## ADDED Requirements

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
