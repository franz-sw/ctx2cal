# Spec Delta

## Purpose

Lets users see which on-device model will serve extraction and, when needed, download an open model to make extraction available.

## ADDED Requirements

### Requirement: Report available model

The system SHALL show on the Model Setup screen whether a device-provided model is available and whether a downloadable open model has been downloaded.

#### Scenario: System model detected

- **WHEN** the setup screen is opened on a device with the system model available
- **THEN** the system reports the system model as the active default without requiring any download

#### Scenario: Downloadable model status

- **WHEN** the user opens Model Setup on a device without the system model
- **THEN** the system reports the downloadable model state as not downloaded, downloading, or ready

### Requirement: Download open model

The system SHALL let the user download, with visible progress, the offered open model for on-device use, and SHALL persist the downloaded model and the selected backend across app restarts.

#### Scenario: Successful download makes extraction available

- **WHEN** the user downloads the open model and it completes
- **THEN** the system marks the model ready and extraction uses it when the system model is unavailable

#### Scenario: Download failure is recoverable

- **WHEN** a model download fails or is interrupted
- **THEN** the system shows a recoverable error and retains the option to retry the download
