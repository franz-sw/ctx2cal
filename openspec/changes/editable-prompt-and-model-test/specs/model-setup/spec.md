# model-setup Specification Delta

## ADDED Requirements

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
