# material3-theme Specification

## Purpose

Provides a consistently themed Material 3 experience across Extract and Setup, with light/dark color schemes and shared typography.

## Requirements

### Requirement: Themed Material 3 app shell
The system SHALL display the application in Material 3 styling with a light color scheme by default and a dark color scheme when the system is in dark theme, using the indigo primary `0xFF4958A9` (light) / `0xFFBAC3FF` (dark).

#### Scenario: Light theme follows system
- **WHEN** the system theme is light
- **THEN** surfaces, text, buttons, and app bar use the light Material 3 palette

#### Scenario: Dark theme follows system
- **WHEN** the system theme is dark
- **THEN** surfaces, text, buttons, and app bar use the dark Material 3 palette

### Requirement: Themed text and scale
The system SHALL render headlines, body text, and button labels using the Material 3 typography scale with increased readable body size on both screens.

#### Scenario: Headlines use themed type
- **WHEN** the Extract or Setup screen displays a section heading
- **THEN** the heading uses the headline text style in the primary color

### Requirement: Themed states on Extract and Setup
The system SHALL display loading indicators, error messages, hints, and primary actions in Material 3 state styling so users can distinguish loading, error, and actionable states in both themes.

#### Scenario: Extracting state in dark theme
- **WHEN** extraction is running and the system is in dark theme
- **THEN** a progress indicator is visible in the primary color on the dark surface

#### Scenario: Error state on Extract screen
- **WHEN** extraction fails
- **THEN** the error message appears in the error color with a retry action

### Requirement: Unified application identity
The system SHALL be installed and identified as `de.franzsw.ctx2cal` on Android and iOS, with no `com.example.*` package identifiers remaining.

#### Scenario: Package identity is unified
- **WHEN** the app is installed on a device
- **THEN** Android `applicationId` and iOS bundle ID equal `de.franzsw.ctx2cal` (with `ContextToCalendar` suffix on iOS)
