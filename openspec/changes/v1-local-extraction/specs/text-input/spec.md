# Spec Delta

## Purpose

Lets users paste free-form text containing a date or event description and get one editable calendar event back on the Extract screen.

## ADDED Requirements

### Requirement: Paste text and start extraction

The system SHALL let the user paste multi-line text on the Extract screen and trigger extraction of exactly one event draft from that text.

#### Scenario: Successful extraction pre-fills review

- **WHEN** the user pastes text and taps Extract and a model is available
- **THEN** the system shows a loading state, then reveals the editable event form pre-filled from the extracted draft

#### Scenario: Re-extract after editing text

- **WHEN** the user edits the pasted text and taps Extract again
- **THEN** the system replaces the current draft with the new extraction result and keeps the edited text visible

### Requirement: Combined input and review screen

The system SHALL present text input, extraction status, and the editable event form on the same Extract screen without requiring navigation between input and review.

#### Scenario: Review actions available in place

- **WHEN** the editable form is showing
- **THEN** the user can edit fields and invoke share or download without leaving the screen

#### Scenario: State survives rotation and process death

- **WHEN** the device rotates or the process is killed and restored during input or review
- **THEN** both the pasted text and the current draft are restored

### Requirement: Extraction failure feedback

The system SHALL show an inline error when extraction is unavailable or yields no event, with a path to Model Setup when no model is available.

#### Scenario: No model available

- **WHEN** the user taps Extract and no on-device model is available
- **THEN** the system shows an error stating no model is available and offers a button that opens Model Setup

#### Scenario: No event found in text

- **WHEN** extraction completes but the text contains no recognizable event or date
- **THEN** the system shows an error stating no event was found and retains the pasted text for editing
