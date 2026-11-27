# event-extraction Specification Delta

## ADDED Requirements

### Requirement: Custom extraction system prompt

When a custom system prompt is saved in settings, event extraction SHALL use that custom system prompt in place of the default system instruction while continuing to inject today's date, device timezone, and the bounded pasted text as the user prompt. When no custom system prompt is saved or it is blank, extraction SHALL use the default system instruction.

#### Scenario: Extraction uses saved custom system prompt

- **WHEN** the user has saved a non-blank custom system prompt in Model Setup and runs event extraction
- **THEN** the extractor uses the saved custom system prompt as the system instruction alongside the date/timezone-annotated user prompt

#### Scenario: Extraction uses default system prompt after reset

- **WHEN** no custom system prompt is saved (or after Reset to default) and the user runs event extraction
- **THEN** the extractor uses the default built-in system instruction
