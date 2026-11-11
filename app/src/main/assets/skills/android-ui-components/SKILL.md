---
name: android-ui-components
description: Choose and review Android Material or custom UI components for actions, communication, containment, navigation, selection, and text input. Use for component suitability, variants, interaction states, and accessibility.
license: Apache-2.0
---
# Android components

Choose a component by the user's intent and the interaction it must support. Do not select it solely because it resembles a mockup.

Read [component decisions](references/component-decisions.md) for a practical selection table and checks.

## Workflow

Identify whether the interaction navigates, acts, selects, edits, communicates, or contains related content. Inspect existing components first. Use supported Material components when they fit, or preserve a justified custom component with equivalent semantics.

Specify the states that matter: enabled, disabled with explanation where needed, selected, focused, pressed, loading, empty, and error. Include labels, accessibility role/value/state, touch area, and keyboard behavior.

For modal components, define entry, dismissal, focus containment, and return focus. For adaptive presentation, coordinate the change with layout and journey semantics.

## Constraints

- A visual icon is not a complete accessible control.
- A selectable chip is not automatically a navigation destination.
- A snackbar is not a replacement for persistent field-error guidance.
- A card does not need an action if its content is informational.
- A custom dialog should not imitate platform-owned authentication or permissions.
- Do not shrink targets or clip text to force a component to fit.

## Verification

Exercise changed states in the app's actual toolkit. Check touch targets, screen-reader semantics, keyboard activation, enlarged text, long labels, and light/dark themes as relevant. Do not claim component support from a Figma library or a dependency name alone.

The official overview links to detailed Material specifications. Consult the relevant component source when exact dimensions, variants, or API availability matter; this skill is not an exhaustive copy of every Material spec.

Source: [Material Components](https://developer.android.com/design/ui/mobile/guides/components/material-overview), reviewed 2026-10-04.
