# Layout recipes and validation

These are authored examples, not universal Android requirements. Apply only configurations supported by the product, and explain exclusions.

## Recipe: collection and editor

A compact catalogue shows a list, then a selected detail/editor. A wider window exposes the list and editor together if both remain usable.

Keep the selected item and unsaved input stable across resizing. When the window narrows, the active editor remains visible. Returning to the list preserves its scroll/filter context. Do not discard the draft or insert a fresh detail route because a pane appears.

## Recipe: short-window registration

A 700 dp wide but 400 dp high window is not a reason to force two columns. Use a constrained, scrollable form, modest header, and keyboard-aware field visibility. A large illustration is secondary to completing the form.

Keep validation close to the field; ensure the error and corrective input can be read together. Do not pin a large button bar over the final input.

## Recipe: feed

Choose item width from content needs. On wider windows, add sensible columns while limiting paragraph and card width. Keep consistent spacing and preserve the focal subject of media. A feed with one item should still have an intentional layout.

## Recipe: media plus supporting controls

Compact: prioritize the player and expose secondary controls in a sheet or adjacent section. Expanded: move suitable controls into a supporting pane. Tabletop: use actual fold information to separate playback and controls where appropriate.

Keep playback continuous and controls reachable. Do not assume rotation means entering immersive mode or PiP.

## Validation matrix

| Exercise | Evidence to inspect |
| --- | --- |
| Small compact width and unusually long content | No clipped labels or unreachable actions |
| Just below/at/above each adopted breakpoint | Stable transitions; no route duplication |
| Short landscape or split-screen window | Task remains usable without excessive decoration |
| Enlarged font and display size | Text reflows; targets remain usable |
| Keyboard open on first and last fields | Input, error, and action remain reachable |
| Gesture and three-button navigation | Correct protection and no competing edge targets |
| Cutout and window caption where applicable | Essential content stays clear |
| Relevant fold/hinge posture | Controls avoid occlusion; state survives |
| Light/dark themes and bright media | System and app content remain legible |
| RTL and longer translation, if supported | Logical order, icons, and spacing remain coherent |
| Loading, empty, error, offline, dense content | Explicit state and useful recovery |

## Evidence record

Record configuration, action, expected result, observed result, and evidence location. For a layout-only mockup, mark interactive cases pending. For an implemented app, use its normal test harness and inspect actual rendered screens; do not infer accessibility, IME, or state restoration from source inspection alone.

A representative matrix is preferable to repeating many visually identical sizes. Add cases when behavior changes or a defect suggests a gap.
