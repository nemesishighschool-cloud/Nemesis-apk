# Live updates and picture-in-picture

## Live updates

Use for a finite or trackable activity initiated by the user, such as a journey or delivery. There should be a meaningful end. They are unsuitable for open-ended promotion, recommendations, or a custom animated mini-app.

Use predictable fields and an understandable progress model. Distinct phases need labels. Time/distance progress must reflect the actual state; keep timestamp/duration formatting consistent between compact and expanded surfaces.

Alert for a critical change, such as arrival, not every small estimate adjustment. Provide immediate visible evidence of why an alert occurred. Respect the user's ability to dismiss or demote the update. Finish or remove stale updates when tracking ends.

Check current platform eligibility, permissions, target SDK, and APIs. A fitting design use case alone does not guarantee promotion to a live update.

## Picture-in-picture

PiP lets appropriate ongoing content continue while the user browses elsewhere. Video is the central case; other supported content needs its own platform considerations.

Show the useful content without shrinking the app's full chrome into an unreadable miniature. Use supported system/media controls and a few appropriate custom actions.

Define entry, return-to-fullscreen, pause/stop, and close behavior. Keep one playback/task identity. Selecting a new video should not accidentally create a second competing player.

For smooth transitions, verify current auto-entry, source bounds, and resizing APIs in the actual implementation. Non-video content may need different seamless-resize behavior. Avoid copying a source's incidental flag relationship without checking current API contracts.

Test real entry/exit, controls, continuous playback, resizing/stashing where supported, and interruption. Do not infer PiP support from a floating card in a mockup.

Sources, reviewed 2026-10-04:
- [Live update notifications](https://developer.android.com/design/ui/mobile/guides/home-screen/live-updates)
- [Picture-in-picture](https://developer.android.com/design/ui/mobile/guides/home-screen/picture-in-picture)
- [Widget surface overview](https://developer.android.com/design/ui/mobile/guides/home-screen/widgets): use the widget skill for that separate surface.
