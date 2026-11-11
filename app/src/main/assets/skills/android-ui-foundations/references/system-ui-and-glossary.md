# System UI and working terminology

## Distinguish the bars

- Status bar: system information and notification indicators.
- System navigation bar/gesture handle: Back, Home, and overview interactions.
- Caption bar: window controls in applicable windowed environments.
- App navigation bar/rail/drawer: destinations inside the app.
- Top/bottom app bar: app content, actions, or navigation affordances.

Do not call the app's tab bar a system navigation bar in implementation specifications.

Include relevant system UI in design frames. Keep system icon contrast readable across theme and content changes. Use actual window insets rather than fixed guessed heights, including captions and cutouts when applicable.

Gesture navigation and three-button navigation have different protection needs. The former should remain transparent; the latter may need contrast protection over scrolling content. Inspect current platform defaults and the app's existing handling before overriding them.

Edge-to-edge drawing and temporary immersive hiding are separate choices. For practical ownership of insets and keyboard behavior, read [insets and immersion](../../android-ui-layout-content/references/insets-and-immersion.md).

## Terms that affect decisions

A pane is a content region that can adapt independently. A canonical layout is a common composition, not a requirement that every screen look identical. Containment groups related content through spacing or visible boundaries.

dp expresses density-independent geometry; sp additionally follows font-size preferences. Hue, chroma, and tone describe different color properties; matching hue alone does not ensure readable contrast.

An activity is a runtime UI entry/context, not necessarily one design screen. A task's activity back stack and an app's internal navigation stack are related but distinct. Intents can request actions from other apps, such as sharing. Check the actual architecture before mapping design destinations to activities.

Sources, reviewed 2026-10-04:
- [System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)
- [Glossary](https://developer.android.com/design/ui/mobile/guides/foundations/glossary)
