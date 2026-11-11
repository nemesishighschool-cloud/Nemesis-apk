# Design Consistency — Optional Review Reference

Read when an interface change adds a major new flow, redesigns important interactions, or spans multiple interdependent screens/states. Apply only the relevant checks; this is not a release acceptance checklist.

## User Flow

- The user can enter through a real entry point and reach the intended outcome via the main path.
- Branches preserve necessary context, and cancel/exit/return destinations make sense.
- Real failures have a recovery path; unnecessary confirmation dialogs are not used to compensate for weak interaction design.

## UX and states

- Primary versus secondary actions, state transitions, and affected objects are unambiguous.
- Relevant loading, empty, error, saved/unsynced, and other real states are distinguishable.
- Delays, external failures, offline conditions, or permission limits do not masquerade as successful completion.
- Visual feedback is timely and accurate; critical outcomes have understandable labels or accessible semantics.
- Canceling a request, dismissing its UI, and undoing an edit are described accurately for the actual backend.

## UI and platform

- Visual hierarchy supports the primary task; existing tokens and components are reused appropriately.
- Supported viewports and platforms do not clip, obscure, or make essential controls unreachable.
- Applicable keyboard, focus, accessible naming, contrast, target size, and reduced-motion expectations are met.
- No unsolicited slogans, redundant status narration, or decoration replaces correct interaction.

## Implementation alignment

- No extra page, dialog, confirmation, or product mode has been added without a flow reason.
- The product UI does not expose agent reasoning, TODOs, implementation steps, or internal instructions.
- Report actual design mismatches and fixes. Functional tests, performance checks, and release readiness are covered by separate engineering requirements.
