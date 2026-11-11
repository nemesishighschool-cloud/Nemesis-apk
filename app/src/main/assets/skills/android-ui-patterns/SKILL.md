---
name: android-ui-patterns
description: Design or review Android interaction flows, including navigation hierarchy, Back and predictive Back, onboarding, sign-in and passkeys, settings, help, and recovery. Use for journey behavior and state transitions rather than visual styling alone.
license: Apache-2.0
---
# Android interaction patterns

Specify what happens before, during, after, and when a user cancels an interaction. Start with the user's goal, not a preferred component.

## Model the flow

Identify entry points, top-level destinations, child destinations, transient surfaces, user actions, and state ownership. Include entry from outside the app where applicable.

For each transition, record:
- Trigger, source, and destination.
- Whether it navigates, filters, selects, edits, confirms, or dismisses.
- Back, Up, close, cancel, and resume behavior.
- Which state is preserved and when any mutation commits.
- Loading, failure, denial, interruption, and recovery outcomes.

This transition model is a recommended working method, not an Android-mandated artifact. Scale it to the task; a one-button change does not need a complete flow map.

## Read only the relevant reference

| Flow | Reference |
| --- | --- |
| Destinations, tabs, actions, deep-link entry and adaptive navigation | [navigation and state](references/navigation-and-state.md) |
| Back gesture preview, commit, cancel, custom motion | [predictive Back](references/predictive-back.md) |
| Registration, sign-in, permissions, education, recovery | [onboarding and authentication](references/onboarding-and-authentication.md) |
| Passkey enrollment, account selection, management and fallback | [passkeys](references/passkeys.md) |
| Preferences, dependency controls, help and feedback | [settings and help](references/settings-and-help.md) |
| Example transitions and checks | [scenarios and validation](references/scenarios-and-validation.md) |

## Decision rules

- Separate destinations from actions and filters. Primary navigation identifies app sections; a primary action executes a task.
- Preserve familiar Android Back behavior. Up follows app hierarchy; Back follows the relevant history. Dismissing a transient surface must not unexpectedly leave the destination.
- Prefer built-in navigation and Material transitions. Custom predictive motion needs verified support in the actual navigation stack.
- Keep preview reversible. Do not save, delete, submit, or discard data just because a predictive gesture begins.
- Introduce permissions and account steps when their value is clear. Provide a usable denial, cancellation, or alternative path where the product allows it.
- Reflect system preferences and use contextual controls for frequent adjustments. Keep long-lived preferences discoverable and understandable.
- Design recovery alongside the happy path. Avoid duplicate submissions and unexplained loss of entered data.
- Use platform-owned UI for platform-owned experiences such as credential selection. Do not simulate successful authentication with a mock sheet.

## Deliver and verify

Explain the selected pattern, concrete transition behavior, persistence decisions, and exceptions. For implementation, exercise complete journeys and cancellation paths in the app; a diagram or screenshot alone does not validate the interaction.

Pair with [layout](../android-ui-layout-content/SKILL.md) for navigation placement and pane adaptation. Accessibility applies to every transition, including focus restoration and non-gesture alternatives.

Sources: [Predictive Back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) and the other official pattern pages linked in references. Reviewed 2026-10-04. Check SDK, navigation, Credential Manager, and Material versions before prescribing implementation details.
