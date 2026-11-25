# Spec Delta

## Purpose

Lets users send the edited event into a calendar application they choose, without granting calendar permissions.

## ADDED Requirements

### Requirement: Share event to calendar app

The system SHALL offer a share action that hands the edited event to Android's calendar insert flow, letting the user pick the calendar application, without requiring calendar read/write permission.

#### Scenario: Successful handoff to calendar app

- **WHEN** the user taps Share with an edited event that passes validation
- **THEN** the system opens the calendar insert flow pre-filled with the event's title, times, description, and location

#### Scenario: No calendar app installed

- **WHEN** the user taps Share and no calendar app handles the insert action
- **THEN** the system shows a recoverable error and leaves the edited event intact on screen
