# Spec Delta

## MODIFIED Requirements

### Requirement: Download event as ICS file
The system SHALL let the user download the edited event as an RFC 5545 `.ics` file saved into the device Downloads folder, containing one event with title, start, end, description, and location escaped per the standard. The file SHALL use CRLF lines with 75-character folding, all-day events SHALL use date-only `VALUE=DATE` with an exclusive `DTEND`, and the filename SHALL be derived from the event title and date.

#### Scenario: Successful download confirmation
- **WHEN** the user taps Download with a valid edited event
- **THEN** the system saves the `.ics` file to Downloads and confirms the save to the user

#### Scenario: Export failure is recoverable
- **WHEN** saving the `.ics` file fails
- **THEN** the system shows a recoverable error and leaves the edited event intact on screen

#### Scenario: All-day exclusive end
- **WHEN** the user downloads a single-day all-day event
- **THEN** the ICS `DTEND;VALUE=DATE` is the following day per RFC 5545

#### Scenario: Long lines are folded
- **WHEN** an event field produces an ICS line longer than 75 characters
- **THEN** the system folds the line with CRLF and a continuation space
