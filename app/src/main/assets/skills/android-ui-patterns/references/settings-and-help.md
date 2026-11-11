# Settings, help, and feedback

## Classify the control

A setting is usually an infrequently changed preference. A frequent action belongs near its feature; a filter modifies the current content view. Account management, app information, and help have distinct purposes even when they share a secondary navigation area.

Respect system settings, including accessibility and theme preferences. Offer app-specific refinements where useful instead of duplicating or silently overriding OS controls.

Use low-interruption, low-risk defaults. Persist actual user choices and expose the current value.

## Organize settings

Start with an overview of meaningful groups. Add child screens for substantial categories; the official guide suggests grouping into subscreens at 15 or more settings. Treat that as an organizational heuristic, not an automatic threshold that dictates the UI.

Use a list-detail composition on wider windows if it fits. Constrain single-pane content width. Match each overview label to its child-screen title.

Settings are usually secondary navigation; promote them only if central to the product's main journey. If accessed through an account/profile area, make relevant settings reachable while signed out too.

## Select the interaction

| Value | Suitable pattern |
| --- | --- |
| One independent on/off preference | Switch or checkbox |
| One choice among mutually exclusive values | Radio group or equivalent single-choice selection |
| Several independent selections | Checkboxes |
| Continuous or approximate range | Slider with an understandable value |
| Precise date/time | Appropriate picker |
| Complex consequence needing explanation | Child screen with the control and explanation together |

Do not mistake the source heading "Multiple choices" for multiple selection: its described radio pattern allows exactly one selected option.

Dependent settings belong near their parent. Explain why a disabled control is unavailable. If the dependency is a system setting, direct users to the appropriate OS location when implementation permits it.

Labels should identify the preference, not repeatedly say "Manage" or "Change." Supporting text should communicate value, state, or consequence when the label cannot. Avoid redundant explanations and double negatives.

## Help and feedback

Place help in secondary navigation and maintain consistent naming. Prioritize common questions and link directly to relevant features or settings. Keep legal information accessible without displacing task help.

A feedback entry should explain what is sent and provide clear success/failure behavior. Do not send feedback, diagnostics, or a review merely because the UI offers that action. A help design is not authorization to contact anyone.

Authored checks: signed-in/out access; persisted values after reopening; parent dependency states; large text; keyboard and screen-reader selection; help entry from an error; feedback retry without duplicate sends.

Sources, reviewed 2026-10-04:
- [Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
- [Help and feedback](https://developer.android.com/design/ui/mobile/guides/patterns/help-content)
