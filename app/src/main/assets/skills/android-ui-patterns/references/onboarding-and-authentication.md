# Onboarding and authentication

## Decide what is actually required upfront

List what the user must set up, learn, and authorize. Separate prerequisites from optional customization and education. Let users experience useful content before registration when the product permits it.

Choose a welcome flow when access truly requires setup first. Prefer contextual education for features that can be learned during use. Explain value before asking for an account or permission.

A walkthrough should have a clear skip or returning-user sign-in path when possible. Avoid turning every field into its own screen, or building one long form full of optional questions.

## Registration and sign-in

Collect only necessary information. Group related fields and explain constraints before an avoidable validation failure. Support appropriate autofill and credential APIs.

Show a clear pending state and prevent duplicate submission. Preserve nonsensitive input after recoverable failures. Keep secrets masked by default and do not prefill a recovered password or reveal sensitive data in feedback.

Make returning-user sign-in fast. Do not force existing users through introductory slides or account creation. Provide visible recovery for the actual credential types the product supports.

## Permissions and education

Ask at the point of need. Explain the benefit and what remains possible after denial. A dismissed permission explanation should not immediately trigger the system prompt anyway.

Distinguish an app explanation from the OS-owned permission sheet. Do not imitate system permission UI or promise to bypass a denied permission.

Use tooltips, sheets, or other educational surfaces when they help the current action. Avoid stacking multiple overlays or blocking the core task for optional education.

## Progress and interruption

Use recognizable steps or progress indicators for a multi-stage setup. Explain whether progress is saved and how to resume. Handle Back, cancellation, app backgrounding, network failure, expired challenges, and re-entry according to the task.

For lengthy steps, keep editable data until completion is confirmed. Resume at an appropriate checkpoint; do not replay completed side effects.

## Layout and writing

Constrain form width on expanded windows. Keep focused fields, errors, and relevant actions visible with the keyboard open. Use clear labels, assistive errors, and brief confirmations. Put field-specific problems near their fields; a transient snackbar alone may not be enough.

When the mobile app completes sign-in initiated on another device, explain which device/account is being authorized and the return path. Do not assume such integration exists unless it is in scope and verified.

Authored acceptance: first-time and returning user, skipped education, denied permission, cancelled sign-in, network interruption, recovery, and resumed progress all have an intelligible next action.

Source: [Authentication and onboarding](https://developer.android.com/design/ui/mobile/guides/patterns/onboarding), reviewed 2026-10-04.
