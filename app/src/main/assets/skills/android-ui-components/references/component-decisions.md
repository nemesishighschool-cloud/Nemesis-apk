# Component decision guide

The official overview contains six practical families, including communication, even though its introductory sentence says five. Use the purpose of each family rather than reproducing that counting inconsistency.

| Intent | Starting options | Decision to resolve |
| --- | --- | --- |
| Execute a task | Button, icon button, FAB | Relative priority, label, pending result |
| Communicate status | Badge, progress, snackbar | Duration, urgency, accessible announcement |
| Group related content | Card, list, carousel | Whether grouping needs a visible surface |
| Focus a temporary task | Dialog, sheet | Modality, dismissal, focus, adaptation |
| Move through the app | Navigation bar/rail/drawer, tabs, app bars | Hierarchy and destination identity |
| Choose values | Checkbox, radio, switch, chips, slider | Independent, exclusive, binary, or range |
| Enter or edit text | Text field | Label, input purpose, validation, keyboard |

## Avoid category mistakes

Use radio buttons for exactly one selection from alternatives; independent selections use checkboxes. A switch changes an on/off preference. A slider suits a range, but may need a precise value representation.

A badge signals status and is not a substitute for an action. An indefinite progress indicator cannot claim completion. A transient message should not be the only location of a blocking form error.

Choose a modal surface when a task merits interruption; keep secondary content in context when possible. Define cancel and confirm semantics and return focus after closing.

A carousel needs discoverable additional content and usable controls. Do not assume swiping is the only access path.

## State and accessibility contract

For the chosen component, record label, role, value/state, focus, enabled conditions, loading/error feedback, and result. Check minimum touch area, text scaling, contrast, and keyboard/screen-reader use.

Read the relevant linked Material specification for exact variants and the installed library documentation for implementation. Do not invent props or migrate a component library solely to obtain a visual variant.

Source: [Material Components](https://developer.android.com/design/ui/mobile/guides/components/material-overview), reviewed 2026-10-04.
