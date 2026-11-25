# Spec Delta

## MODIFIED Requirements

### Requirement: Edit all event fields
The system SHALL let the user edit title, start date/time, end date/time, all-day flag, description, and location of the extracted draft before any share or download action. Date and time edits SHALL use date and time pickers rather than raw ISO text, validation SHALL be per-field and calendar-strict, and share and download SHALL be disabled until the draft is valid.

#### Scenario: Editing fields updates the event
- **WHEN** the user changes one or more fields in the event form
- **THEN** subsequent share and download actions use the edited values

#### Scenario: Invalid edit ranges are prevented
- **WHEN** the user sets an end that is before the start for a non-all-day event
- **THEN** the system prevents sharing or downloading until the range is corrected

#### Scenario: Field-level validation
- **WHEN** the user clears a required field or enters a mixed date/datetime form
- **THEN** the form shows a field-level message for that field and disables share and download

#### Scenario: All-day toggle normalizes dates
- **WHEN** the user toggles All day on or off
- **THEN** the form converts date/datetime values to the matching representation for the new mode
