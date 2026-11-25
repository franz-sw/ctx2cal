# Spec Delta

## Purpose

Lets users save the edited event as a portable calendar file in the device Downloads folder for use in any calendar app.

## ADDED Requirements

### Requirement: Download event as ICS file

The system SHALL let the user download the edited event as an RFC 5545 `.ics` file saved into the device Downloads folder, containing one event with title, start, end, description, and location escaped per the standard.

#### Scenario: Successful download confirmation

- **WHEN** the user taps Download with a valid edited event
- **THEN** the system saves the `.ics` file to Downloads and confirms the save to the user

#### Scenario: Export failure is recoverable

- **WHEN** saving the `.ics` file fails
- **THEN** the system shows a recoverable error and leaves the edited event intact on screen
