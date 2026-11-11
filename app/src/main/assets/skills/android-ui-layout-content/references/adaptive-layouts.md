# Adaptive layouts and navigation placement

## Choose a canonical composition

| Content relationship | Starting composition | Compact behavior | Roomier behavior |
| --- | --- | --- | --- |
| A collection and the selected item's detail | List-detail | One pane at a time | List and selected detail together |
| Equivalent independent items for browsing | Feed | List or a small number of columns | More columns or adjusted card sizes |
| A primary task with contextual controls or related content | Supporting pane | Primary content plus a sheet or separate destination | Supporting content beside primary content |

These are starting points. Explain when another composition serves the content better.

For list-detail, define the unselected state, selection persistence, detail entry and exit, and what becomes visible when shrinking. Avoid showing the list again when the user was actively editing detail.

For a feed, define minimum useful item width, spacing, crop, and maximum readable card width. Add columns when useful; never shrink images and labels indefinitely.

For a supporting pane, decide whether its content is persistent, dismissible, or modal. A modal sheet becoming a persistent side pane changes focus and dismissal behavior, not just coordinates.

## Window rules

Use available app window size, not physical device type. At the 2026-10-04 review, current width guidance is:
- Compact: below 600 dp.
- Medium: 600 to below 840 dp.
- Expanded: 840 to below 1200 dp.
- Large: 1200 to below 1600 dp.
- Extra large: 1600 dp and above.

Height is classified separately: compact below 480 dp, medium below 900 dp, expanded from 900 dp. Short landscape windows can invalidate a two-pane design even when width increases.

Older design pages describe only three width classes. Check the current implementation guide and installed adaptive library: availability and opt-in for additional classes are version dependent. Do not invent a new class detector when the project's supported library already supplies one.

## Reflow, reveal, or change presentation

- Reflow content and controls without changing their meaning.
- Reveal useful supporting content when there is room.
- Change presentation, such as a bottom sheet to a side sheet.
- Constrain width rather than stretching every item.
- Keep a stable visual anchor when a player or editor reorganizes.

Compact layouts usually use one pane; medium may use one or two; expanded and larger may expose more. These are content-dependent choices, not pane counts imposed solely by a breakpoint.

## Navigation

A bottom navigation bar commonly serves 3-5 peer destinations on compact windows. A navigation rail is the typical larger-window counterpart; a drawer may serve broader hierarchies. Do not stretch compact bottom navigation across a wide window.

Tabs group sibling content within a section. A FAB is an action, not a destination. Keep action hierarchy and navigation hierarchy distinct. Cover-screen ergonomics can justify different placement even at compact width.

## Continuity contract

Decide what survives resizing, rotation, folding and multi-window changes: selected ID, entered text, search query, filters, scroll anchor, focus, playback position, and pending work as applicable. Do not create a duplicate back-stack entry simply because a second pane becomes visible.

Sources, reviewed 2026-10-04:
- [Common layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/common-layouts)
- [Adapt layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout)
- [Layout and navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns)
- [Current window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
