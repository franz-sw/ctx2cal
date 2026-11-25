# Spec Delta

## MODIFIED Requirements

### Requirement: Report available model
The system SHALL show on the Model Setup screen whether a device-provided model is available and whether a downloadable open model has been downloaded. It SHALL also cover downloadable and downloading system-model states and the unsupported-platform state, and SHALL offer the LiteRT fallback and refresh whenever the system model is not ready.

#### Scenario: System model detected
- **WHEN** the setup screen is opened on a device with the system model available
- **THEN** the system reports the system model as the active default without requiring any download

#### Scenario: Downloadable model status
- **WHEN** the user opens Model Setup on a device without the system model
- **THEN** the system reports the downloadable model state as not downloaded, downloading, or ready

#### Scenario: System model pending or unsupported
- **WHEN** Model Setup is opened while the system model is downloadable, downloading, or unsupported on the platform
- **THEN** the system explains the pending or unsupported state and still offers refresh and any applicable fallback action

### Requirement: Download open model
The system SHALL let the user download, with visible progress, the offered open model for on-device use, and SHALL persist the downloaded model and the selected backend across app restarts. The system SHALL allow cancellation and deletion, SHALL gate the download on sufficient free storage, SHALL verify file size before marking the model ready, and SHALL recover from stale persisted paths pointing to missing files.

#### Scenario: Successful download makes extraction available
- **WHEN** the user downloads the open model and it completes
- **THEN** the system marks the model ready and extraction uses it when the system model is unavailable

#### Scenario: Download failure is recoverable
- **WHEN** a model download fails or is interrupted
- **THEN** the system shows a recoverable error and retains the option to retry the download

#### Scenario: Cancelled download leaves no model
- **WHEN** the user cancels an in-flight download
- **THEN** the system stops the download, shows no ready state, and retains the option to retry

#### Scenario: Truncated download is not ready
- **WHEN** a download completes with a file far below the expected size
- **THEN** the system reports the download as incomplete and does not mark the model ready

#### Scenario: Stale persisted path recovers
- **WHEN** a persisted model path points to a missing or empty file
- **THEN** the system clears the stale reference and offers the download instead of reporting the model ready
