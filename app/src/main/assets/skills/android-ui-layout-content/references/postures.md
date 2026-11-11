# Orientation, folding, and small cover screens

Treat window size, orientation, and folding posture as related but distinct inputs. A foldable can change available space without behaving like a simple phone-to-tablet switch.

## Orientation

Do not lock portrait to hide layout defects. For landscape, examine available height as well as width. A large hero image can consume nearly the whole short window; reduce its prominence, change crop, or reflow related content.

Keep essential tasks and their progress intact. When scrolling becomes necessary for enlarged text or short height, make it discoverable and intentional rather than clipping content to preserve a static composition.

## Folding

- Flat: consider additional panes if the actual available window supports them.
- Folded: provide a focused experience suitable for the actual outer display.
- Tabletop: where useful, separate viewing above the fold from controls below it.
- Book-like or separating configurations: place panes within usable regions.

A crease is not always an occluding hinge. Inspect actual folding-feature information before inventing a gap. Conversely, do not center text or a primary button over a genuinely occluding feature. Multiple hinges are possible; avoid an assumption that every device has exactly two equal halves.

Use pane gutters and constraints to account for occlusion. Preserve identity and state as panes move. Treat opening or closing the device as a configuration transition, not implicit confirmation or cancellation.

## Cover-screen decisions

Prioritize a small number of relevant actions. Respect camera cutouts, gestures, and unusual aspect ratios. A media cover can emphasize artwork and playback; a messaging cover should prioritize the active input when the keyboard opens.

Navigation may need to change orientation to fit ergonomics and safe regions. "Compact" alone does not require a bottom navigation bar on every cover display.

## Authored checks

During playback, editing, or detail selection:
1. Rotate the window and verify useful content remains visible.
2. Resize to a compact-height configuration.
3. Fold/unfold using an emulator or device that exposes folding features.
4. Verify occlusion avoidance and preservation of the current task.
5. Repeat with enlarged text and an open keyboard where relevant.

Report simulated posture evidence separately from a physical-device test. Do not claim foldable support from static screenshots alone.

Source: [Foldable postures and orientation](https://developer.android.com/design/ui/mobile/guides/layout-and-content/postures-and-orientation), reviewed 2026-10-04.
