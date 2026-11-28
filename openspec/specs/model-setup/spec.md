# model-setup Specification

## Purpose

Lets users see which on-device model will serve extraction and, when needed, download an open model to make extraction available.

## Requirements

### Requirement: Report available model

The system SHALL show on the Model Setup screen whether a device-provided system model is available and SHALL list the offered ungated open models along with each model's download state, size, hardware suitability, and active selection status. It SHALL also cover downloadable and downloading system-model states and the unsupported-platform state, and SHALL offer refresh whenever the system model is not ready.

#### Scenario: System model detected

- **WHEN** the setup screen is opened on a device with the system model available
- **THEN** the system reports the system model and makes it available as the default active option without requiring any download

#### Scenario: Downloadable model status

- **WHEN** the user opens Model Setup on an Android device
- **THEN** the system lists all cataloged ungated open models with each model's state (not downloaded, downloading, downloaded, or active)

#### Scenario: System model pending or unsupported

- **WHEN** Model Setup is opened while the system model is downloadable, downloading, or unsupported on the platform
- **THEN** the system explains the pending or unsupported state and still offers refresh and any applicable catalog or fallback information

### Requirement: Download open model

The system SHALL let the user download any compatible model from the catalog with visible progress, keep multiple downloaded models stored independently on disk, allow switching the active model between downloaded open models and an available system model across restarts, allow cancellation and deletion, gate downloads on sufficient free storage, verify file size before marking a model ready, and recover from stale persisted paths pointing to missing files.

#### Scenario: Successful download makes extraction available

- **WHEN** the user downloads an offered open model and it completes
- **THEN** the system stores it under its per-model path, marks it downloaded, and sets it as the active extraction model

#### Scenario: Multiple models kept and switchable on disk

- **WHEN** the user downloads a second open model while another model is already downloaded
- **THEN** both models remain stored on disk and the user can switch which downloaded model (or available system model) is active

#### Scenario: Download failure is recoverable

- **WHEN** a model download fails or is interrupted
- **THEN** the system shows a recoverable error and retains the option to retry the download

#### Scenario: Cancelled download leaves no model

- **WHEN** the user cancels an in-flight download
- **THEN** the system stops the download, does not mark that model ready, and retains previously downloaded models intact

#### Scenario: Truncated download is not ready

- **WHEN** a download completes with a file far below the expected size
- **THEN** the system reports the download as incomplete and does not mark the model ready

#### Scenario: Stale persisted path recovers

- **WHEN** a persisted active model path points to a missing or empty file
- **THEN** the system clears the stale reference and falls back to another ready model or offers download instead of reporting it ready

### Requirement: Evaluate hardware suitability per model

On Android, the system SHALL inspect device hardware capabilities (CPU ABI, total RAM, GPU/SoC signals, and available storage) and assign each catalog model a suitability tier: recommended, supported on CPU (may be slow), or incompatible.

#### Scenario: Older CPU-only device receives slow-execution indicator

- **WHEN** Model Setup is opened on a 64-bit ARM device without recognized GPU acceleration for LiteRT-LM and with sufficient RAM for a small model
- **THEN** that model displays a CPU-only slow-execution indicator rather than a recommended badge

### Requirement: Block incompatible model downloads

When a catalog model exceeds the device's hardware capabilities (unsupported ABI, insufficient total RAM, or insufficient free storage), the system SHALL disable its Download action and show the incompatibility reason.

#### Scenario: Insufficient RAM disables oversized model download

- **WHEN** a catalog model requires more RAM than the device provides
- **THEN** the Setup screen marks the model incompatible with the RAM reason and disables its Download button

### Requirement: Warn when no accelerated model is supported

When the system model is unavailable and every catalog model is either incompatible or restricted to slow CPU-only execution, Model Setup SHALL display a prominent warning banner explaining the device limitation before the user downloads a model.

#### Scenario: Warning shown on unsupported or CPU-only device

- **WHEN** Model Setup is opened on a device with no Gemini Nano and no GPU-recommended catalog model
- **THEN** the Setup screen displays a warning banner stating that no accelerated model is supported and local extraction may be slow or unavailable

### Requirement: Confirm model deletion

Deleting a downloaded open model from Model Setup SHALL require explicit user confirmation in an alert dialog before removing the file from disk.

#### Scenario: Confirming deletion removes model and updates active selection

- **WHEN** the user taps Delete on a downloaded model and confirms in the alert dialog
- **THEN** the system deletes only that model's file and falls back the active selection to another downloaded model, Gemini Nano if available, or none

#### Scenario: Dismissing delete dialog preserves model

- **WHEN** the user taps Delete on a downloaded model and cancels or dismisses the alert dialog
- **THEN** the model file remains on disk and active selection is unchanged

### Requirement: Edit and reset global extraction system prompt

Model Setup SHALL display an editable global system prompt field initialized to the persisted custom system prompt or the default extraction system prompt, persist edits across restarts, and provide a Reset to default action that restores the default extraction system prompt.

#### Scenario: Editing the system prompt persists across restarts

- **WHEN** the user edits the system prompt in Model Setup
- **THEN** the updated system prompt is persisted and remains shown after leaving and reopening Model Setup or restarting the app

#### Scenario: Resetting the system prompt restores default

- **WHEN** the user taps Reset to default in the system prompt section
- **THEN** the custom system prompt override is cleared and the field reverts to the default extraction system prompt

### Requirement: Offer per-model Test action for ready models

Model Setup SHALL display a Test button on each ready model entry (an available system model and each downloaded open model) that opens the Model Test screen for that model, and SHALL NOT offer Test for models that are not ready or not downloaded.

#### Scenario: Test button shown for available system model

- **WHEN** the system model is available in Model Setup
- **THEN** the system model card displays a Test button alongside its selection action

#### Scenario: Test button shown for downloaded open model

- **WHEN** an open catalog model is downloaded in Model Setup
- **THEN** that model card displays a Test button alongside Use/Delete

#### Scenario: Test button hidden for undownloaded open model

- **WHEN** an open catalog model is not downloaded
- **THEN** that model card does not display a Test button
