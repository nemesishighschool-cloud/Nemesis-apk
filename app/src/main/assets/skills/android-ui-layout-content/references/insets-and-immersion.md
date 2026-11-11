# Insets, keyboard, edge-to-edge, and immersion

## Separate the layers

Edge-to-edge means drawing under system bars; immersive mode hides system bars temporarily for an appropriate experience. They are not interchangeable.

Let backgrounds and appropriate scrolling content extend to window edges. Protect text, controls, and essential imagery from system UI and physical occlusion.

| Constraint | Design consequence |
| --- | --- |
| Status/navigation bars and window captions | Protect important content and maintain icon contrast |
| Gesture regions | Avoid competing edge drags and inaccessible targets |
| Display cutouts | Keep essential content clear, including in landscape |
| IME / on-screen keyboard | Keep focused input, validation, and relevant actions usable |
| Folding feature or hinge | Place content in usable regions; do not split a control across occlusion |

## Assign inset ownership

Inspect what Scaffold, app bars, navigation components, sheets, and custom containers already handle. Record which layer applies and consumes each inset. Double application can create a large blank gap; missing application can hide controls.

Never replace runtime insets with a guessed status-bar height. Do not add keyboard height and navigation-bar height blindly; use the semantics supported by the actual UI toolkit.

Check the final scroll position: content that can appear above a bottom bar is insufficient if its final action can never scroll into view.

## Protect contrast deliberately

Status bar icons must remain legible over scrolling text or media. Use the supported app-bar protection or a matching custom gradient where needed. Avoid stacking both. In a multi-pane screen, protection should match each pane's background.

Keep gesture navigation transparent. Three-button navigation can need a translucent protection when content scrolls beneath it; when a solid app bar already provides the background, verify whether additional protection is appropriate. Do not disable contrast protection globally just to match one screenshot.

## Keyboard behavior

For a form, bring the active field and error into view. Define Next/Done behavior and submission separately. For a conversation, a composer may remain attached above the keyboard while preserving enough visible context.

Test opening, closing, switching fields, rotation with the keyboard open, a long error, and a short window. Hardware keyboard and accessibility focus also need a coherent order.

## Immersive content

Use immersive mode when uninterrupted video, reading, imagery, or gameplay benefits from it. Supply an intuitive way to reveal controls and allow users to recover system navigation. Do not hide system bars to solve an ordinary layout overflow.

Provide scrims for text over imagery. Define entry, exit, controls-visible, controls-hidden, and interruption states. If relevant, coordinate with picture-in-picture without duplicating playback.

API enforcement and exemptions depend on OS, target SDK, and libraries. Recheck current developer guidance before changing implementation.

Sources, reviewed 2026-10-04:
- [Edge-to-edge design](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge)
- [Immersive content](https://developer.android.com/design/ui/mobile/guides/layout-and-content/immersive-content)
- [System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)
- [Window insets in Compose](https://developer.android.com/develop/ui/compose/system/insets)
