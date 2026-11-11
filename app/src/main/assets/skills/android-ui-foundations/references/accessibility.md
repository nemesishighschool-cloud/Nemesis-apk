# Accessibility

The source recommends at least 4.5:1 contrast for text and 3:1 for meaningful non-text elements against their backgrounds. Check the actual rendered color pairs, including opacity, imagery, disabled/error/selected states, and both themes. Do not rely on color alone to communicate a status or action.

Use sp for text and respect the system's font scaling. The guide's 12 sp body-text floor is a lower bound, not a suggested default body size. Prefer readable type appropriate to the task. Let text wrap and containers grow; do not defeat scaling to preserve geometry.

Interactive targets should be at least 48 dp in each dimension even when the visible icon is smaller. Ensure expanded hit areas do not overlap or obscure nearby controls.

## Semantics and input

Expose a meaningful name, role, state, and value. Describe meaningful non-text content; suppress decorative image descriptions. Avoid repeating a visible label with a redundant content description. Group related information at a useful reading granularity while preserving independently actionable controls.

Supply a non-gesture way to complete essential actions. A swipe-to-delete row needs an accessible action or another reachable control. Keep focus and traversal order aligned with the content's meaning.

Errors should identify the affected input and correction. Dynamic announcements should be useful and proportionate, without repeatedly interrupting the user. Haptics or sound can reinforce feedback but must not be its only channel.

## Verification

Use automated scanning for detectable problems, then explore the real flow with TalkBack and relevant alternate input such as keyboard, Voice Access, or Switch Access. Test entry, action, error, modal dismissal, and focus restoration. Record exactly which checks were performed.

Authored checks: large text with the keyboard open; repeated unlabeled icons; a custom gesture-only action; selection changes announced coherently; focus returns after a modal closes. A static design can specify these behaviors but cannot demonstrate them.

Source: [Accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility), reviewed 2026-10-04.
