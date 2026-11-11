# Notifications

A notification should offer timely, relevant value outside the app. First decide whether interruption is needed at all. Avoid reminders whose only purpose is to bring users back, requests for ratings, and alerts about background work that requires no user action.

## Specify the notification

Choose a standard, expanded-text, image, progress, media, messaging, or call template according to the content. Use the actual platform template where supported.

Define concise title/content, meaningful imagery, timestamp, category, channel, importance, privacy level, primary destination, and useful actions. Avoid repeating the app name or adding an action that merely duplicates tapping the body.

Group related notifications when several can arrive together. Each child should make sense on its own. Update or remove stale information. Match the tap destination to the specific item or task, including unavailable-content recovery.

## Consent and user control

Explain notification value in context, then request the applicable system permission. If the user dismisses the explanation, respect that. Denial should lead to a supported experience and a later settings path where appropriate.

Channels and OS settings control actual presentation. Choose importance according to urgency, not engagement goals. Users can mute or alter behavior; do not promise a heads-up alert or sound.

Set lock-screen visibility deliberately. For sensitive content, provide an appropriate public-safe representation or hide it according to the product's needs and platform support.

## Ongoing activity

Foreground-service notifications and exemptions are version-sensitive platform obligations. Do not treat all ongoing notifications as permanently non-dismissible or copy old design prose into an API guarantee. Check current developer documentation, OS, target SDK, and service type.

When a user-initiated task can stop, provide the relevant action. Keep action results, progress, and termination synchronized with the actual operation.

## Validation

Test collapsed/expanded, grouped/solo, relevant lock-screen privacy, permission denial, muted channels, tap/deep-link, action failure/retry, completion, and stale cleanup. Synthetic preview rendering does not prove notification delivery.

Source: [Notifications](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications), reviewed 2026-10-04. Its linked implementation guides govern current permission and service requirements.
