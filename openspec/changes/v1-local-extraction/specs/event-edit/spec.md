# Spec Delta

## Purpose

Lets users correct every field of the extracted event — title, dates, description, location — before sharing it or downloading it.

## ADDED Requirements

### Requirement: Edit all event fields

The system SHALL let the user edit title, start date/time, end date/time, all-day flag, description, and location of the extracted draft before any share or download action.

#### Scenario: Editing fields updates the event

- **WHEN** the user changes one or more fields in the event form
- **THEN** subsequent share and download actions use the edited values

#### Scenario: Invalid edit ranges are prevented

- **WHEN** the user sets an end that is before the start for a non-all-day event
- **THEN** the system prevents sharing or downloading until the range is corrected
