# Navigation, actions, and state

## Distinguish intent

| User intent | Typical UI |
| --- | --- |
| Move between peer app sections | Navigation bar, rail, or drawer |
| Move between related content views within a section | Tabs |
| Narrow a collection | Filters, chips, search |
| Perform the main creation/task action | Button or FAB |
| Inspect an item | Detail destination or detail pane |
| Adjust supporting context without losing the task | Sheet, pane, or appropriate dialog |

The official layout/navigation guide recommends 3-5 peers for a compact navigation bar and a rail on larger windows. A drawer can hold more destinations but has reachability costs. Do not add dummy sections to reach a count. Reconsider the information architecture when primary navigation becomes a list of actions.

A FAB should represent one highest-priority action in the current context. Secondary actions belong near their content or in app bars; infrequent actions can use an overflow menu.

## Back, Up, and dismiss

Back returns through relevant navigation history and may leave the app. Up moves within the app's parent hierarchy. Close dismisses a modal/task surface according to its contract. These operations can coincide, but are not universally interchangeable.

For each entry point, including notifications or external links when present, determine the actual route history and intended Up destination. Do not implement Up as an unconditional history pop or Back as an unconditional jump to the app's home screen.

A transient sheet, dialog, search state, or keyboard can affect the next Back action. Use the actual framework's dispatch behavior and test the sequence. Avoid competing handlers that pop two layers for one action.

## Adaptive navigation

Keep destinations and selected state stable when a bar changes to a rail. When list-detail changes from one pane to two, define Back in terms of navigation state, not merely the existence of a second visible pane.

Expose primary navigation consistently on parent sections. For child and modal flows, choose visibility deliberately; do not allow a navigation bar to become an accidental escape from an uncompleted critical task.

## Authored state contract

Record state by owner:
- Destination: selected item, query, filters, list position.
- Task: draft, validation, pending submit, completion.
- Surface: sheet visibility, expanded controls, focused input.
- External flow: return destination and pending request identity.

Decide what survives navigation, resize, process recreation, or sign-out separately. Do not promise persistence without implementation evidence. For unsaved edits, choose draft preservation or a proportional confirmation when loss would matter. Every minor Back action does not need a confirmation.

A sharing action normally invokes the supported Android share experience. Check the current implementation source through the Android glossary's intent links rather than inventing a bespoke recipient picker.

Sources, reviewed 2026-10-04:
- [Layout and navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns)
- [Platform translation, including Up versus Back](https://developer.android.com/design/ui/mobile/guides/foundations/translate-designs)
- [Glossary and intents](https://developer.android.com/design/ui/mobile/guides/foundations/glossary)
