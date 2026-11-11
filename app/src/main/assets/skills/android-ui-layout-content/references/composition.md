# Composition, containment, and units

## Start with a screen contract

Write the user's primary task, the content needed to complete it, one principal action if appropriate, and what can be secondary. Separate system UI, app navigation, and body content. The status bar, system navigation region, and window caption are not app navigation components.

Choose a grid according to content relationships:
- A column grid supports alignment and flexible single-direction flow.
- A modular grid suits similarly important items, such as an image collection.
- A hierarchical grid gives an editorial lead or detail header more prominence.

Use the grid as an alignment aid. Do not force unrelated content into identical cards or add columns merely to fill width.

## Group before decorating

Implicit containment uses spacing, typography, and alignment. Explicit containment uses a visible surface, border, divider, or card. Prefer the lightest grouping that makes relationships clear. A card should represent a meaningful group, not wrap every label.

Define:
- Page margins and alignment anchors.
- Space within a group versus between groups.
- Reading order and heading hierarchy.
- Which content scrolls together.
- Which controls remain pinned and why.

A compact margin of 16 dp and an 8 dp baseline with 4 dp refinements are source starting points, not a universal rule that overrides existing tokens or component specifications. Text uses sp. A bitmap's pixel dimensions are not layout dimensions.

## Constraints, not screenshots

For each container, state whether it fills available space, wraps content, or has a minimum/maximum width. Long prose and forms need a readable maximum width. Avoid arbitrary fixed heights for containers with dynamic or translated text.

For repeated items, align comparable information consistently. Reserve space for asynchronous media where appropriate so loading does not cause disruptive jumps. Define empty, sparse, dense, and unusually long content states.

For pinned actions, reserve sufficient scroll space so the last item and its actions remain reachable. An anchored composer must move with the keyboard and not cover the conversation or its own validation message.

## Density and handoff

Use dp for geometry and sp for text. Where a design tool uses nominal pixels, explicitly document the intended dp/sp mapping. Raster density assets use mdpi 1x, hdpi 1.5x, xhdpi 2x, xxhdpi 3x, and xxxhdpi 4x when that resource strategy applies. Vector drawables do not need duplicate density exports.

Authored review prompts:
- Can a user identify the main task before reading every card?
- Does grouping survive enlarged text and translated labels?
- Is spacing describing relationships, or merely filling gaps?
- Is the last action reachable when the keyboard or a pinned bar is present?

Sources, reviewed 2026-10-04:
- [Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)
- [App anatomy](https://developer.android.com/design/ui/mobile/guides/layout-and-content/app-anatomy)
- [Grids and units](https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units)
- [Content structure](https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure)
