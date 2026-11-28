# model-test-playground Specification

## Purpose

Provides a lightweight on-device LLM test screen ("LLM shed") launched from Model Setup for any ready model, allowing users to run raw system and user prompts and inspect raw model output.

## Requirements

### Requirement: Launch model test screen for a specific ready model

The system SHALL allow the user to open the Model Test screen from Model Setup for any ready model (available system model or downloaded open model) and SHALL display that target model's name on the test screen.

#### Scenario: Opening test screen for Gemini Nano

- **WHEN** the user taps the Test button on the available Gemini Nano card in Model Setup
- **THEN** the system navigates to the Model Test screen targeting Gemini Nano

#### Scenario: Opening test screen for a downloaded open model

- **WHEN** the user taps the Test button on a downloaded open model card in Model Setup
- **THEN** the system navigates to the Model Test screen targeting that specific downloaded open model

### Requirement: Pre-fill and edit system and user prompts

The Model Test screen SHALL provide an editable system prompt field pre-filled with the active extraction system prompt from Setup and an editable user prompt field.

#### Scenario: System prompt is pre-filled from active Setup prompt

- **WHEN** the user opens the Model Test screen while a custom system prompt is saved in Setup
- **THEN** the system prompt field on the Model Test screen is pre-filled with that saved custom system prompt

#### Scenario: System prompt defaults when no custom prompt is saved

- **WHEN** the user opens the Model Test screen while no custom system prompt is saved in Setup
- **THEN** the system prompt field on the Model Test screen is pre-filled with the default extraction system prompt

### Requirement: Run raw inference on the target model only

When the user runs a test prompt, the system SHALL execute single-turn raw inference against only the selected target model using the entered system and user prompts, without JSON schema validation, automatic retry, or fallback to another model, and SHALL display the raw text output.

#### Scenario: Running a prompt on a downloaded open model

- **WHEN** the user enters a system prompt and user prompt and taps Run on a downloaded open model
- **THEN** the system runs raw inference on that open model with the given system instruction and user prompt and displays the raw response text

#### Scenario: Running a prompt on Gemini Nano

- **WHEN** the user enters a system prompt and user prompt and taps Run on Gemini Nano
- **THEN** the system runs raw inference on Gemini Nano with the combined system and user prompts and displays the raw response text

#### Scenario: Empty user prompt cannot be run

- **WHEN** the user prompt field is blank
- **THEN** the Run action is disabled or does not start inference

#### Scenario: Target model failure does not fall back

- **WHEN** raw inference fails on the selected target model
- **THEN** the system displays an error on the Model Test screen without falling back to another model

### Requirement: Support cancellation and timeout during test inference

The system SHALL allow the user to cancel an in-flight test run and SHALL enforce a finite timeout, cancelling native open-model generation when timed out or cancelled.

#### Scenario: User cancels an in-flight test run

- **WHEN** the user taps Cancel while a test inference is running
- **THEN** the system cancels inference and returns the test screen to an idle state without showing a failure error

#### Scenario: Test inference times out

- **WHEN** a test inference exceeds the execution timeout
- **THEN** the system cancels native inference and displays a recoverable timeout error
