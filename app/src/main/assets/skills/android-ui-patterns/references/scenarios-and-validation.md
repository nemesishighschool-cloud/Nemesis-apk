# Interaction scenarios and evaluation

These authored exercises test decisions, not memorized wording. They can be used for a design review or to evaluate an agent using the skill. They do not require real accounts or external writes.

## Scenario 1: detail opened from a notification

A message notification opens a detail destination. The user then selects an attachment and closes it.

Expected reasoning: define the external entry and actual history; dismiss the attachment before leaving detail; distinguish Up from Back; preserve meaningful context; handle unavailable/deleted content. Do not invent an app back stack from a screenshot.

## Scenario 2: edit, preview Back, cancel

A user has entered an unsaved title, starts a Back gesture, and cancels.

Expected reasoning: the current surface, draft, and navigation state remain intact; no save/delete/discard happens at gesture start; commit has a separately defined policy; an unavailable emulator leaves behavior unverified.

## Scenario 3: settings on a wide display

A preference overview opens "Downloads." A master switch disables its dependent options. The app is resized to one pane.

Expected reasoning: retain the active category and values, communicate dependency, preserve signed-out access where relevant, and use radio selection only for mutually exclusive choices. Avoid stretched rows and duplicate routes.

## Scenario 4: first use with optional account

The product permits guest browsing and requests notifications for an optional reminder.

Expected reasoning: allow useful browsing, introduce account benefits when needed, request notification permission in context, respect dismissal and denial, and provide a later entry point. Avoid fabricating that registration is a product requirement.

## Scenario 5: interrupted passkey creation

A user cancels the system sheet after choosing an account.

Expected reasoning: no success claim; keep alternate sign-in and account context; explain a useful next step; verify the real provider result before changing application state. Do not infer cancellation means account deletion.

## Scenario 6: a focused request

The user asks only to improve the "Help" screen's labels.

Expected reasoning: apply relevant help and writing guidance; preserve scope; do not redesign navigation, replace auth, install tooling, or load all skills.

## Flow acceptance record

| Case | Required observation |
| --- | --- |
| Happy path | Reaches the intended result once |
| Cancel/dismiss | Clear return path; no unintended mutation |
| Denial/unavailability | Explanation and supported alternative |
| Error/retry | Useful recovery; no duplicate effects |
| Interrupted/resumed | Defined state restoration |
| Back/Up/close | Correct operation for entry and hierarchy |
| Resize/rotation | Journey and task identity preserved |
| Keyboard/accessibility | Reachable controls and coherent focus |
| Reduced motion | Meaning remains understandable |

Record expected and actual results separately. A source review can validate the proposed contract; only exercising the implementation validates actual runtime behavior. Do not report these scenarios as passed merely because this document contains the expected reasoning.
