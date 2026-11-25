# Spec Delta

## MODIFIED Requirements

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

### Requirement: Extraction failure feedback
The system SHALL show an inline error when extraction is unavailable or yields no event, with a path to Model Setup when no model is available. A no-event result SHALL retain the pasted text and show an explanatory notice rather than silently resetting to input.

#### Scenario: No model available
- **WHEN** the user taps Extract and no on-device model is available
- **THEN** the system shows an error stating no model is available and offers a button that opens Model Setup

#### Scenario: No event found in text
- **WHEN** extraction completes but the text contains no recognizable event or date
- **THEN** the system shows a notice stating no event was found and retains the pasted text for editing
