# UX Patterns: Input, Waiting, Evaluation, and Recovery

Use **only** when the current user goal exposes the problem a pattern solves. Before adopting a pattern, ask: Which part of the user flow is failing? Which real backend capability supports the solution? What new complexity will the UI introduce?

## 1. Lower the input barrier without taking away editing

For difficult or ambiguous input tasks, optional examples, editable templates, presets, tags, and structured fields can reduce effort. Do not build a wizard around an otherwise simple input.

Allow users to begin with a short input and expand advanced options when needed. If guided and free-form input coexist, define whether they edit one shared state or have distinct responsibilities. Switching modes must not silently discard or duplicate user changes.

When a system transforms or enhances input in a way that changes execution, let the user inspect the effective input and edit or recover the original where appropriate. Helpful completion must not silently change the goal.

## 2. Show real feedback rather than fabricated certainty

| Backend actually provides | Reasonable UI representation | Misleading representation to avoid |
| --- | --- | --- |
| Submission acknowledged, no internal progress | Submitted/waiting state, indeterminate progress, elapsed time, real available controls | Invented stages or completion percentages |
| Trustworthy stage events | Current and finished real stages | Time-driven rotation through fictional stages |
| Measured progress | Progress tied to real work units, with remaining uncertainty | A timer pretending to be work completion |
| Evidence-based duration estimate | Clearly labeled approximate time or range, updated when wrong | A precise countdown or guaranteed finish time |
| No reliable final status after disconnection | Unknown/disconnected/reconciling state | Declaring backend failure solely because the UI timed out |

Prefer visual cues and brief labels over redundant narrative. Put useful error context and a recovery action near the point of failure instead of hiding it behind endless spinners.

## 3. Name actions according to their real effects

Differentiate **stop waiting**, **stop displaying a stream**, **request cancellation**, **cancellation confirmed**, and **delete a record**.

If the backend cannot cancel a job, do not offer a button that falsely implies work or charges have stopped. If cancellation is asynchronous, represent request and confirmation separately. If completion races with a cancellation request, do not discard a real result or oscillate between contradictory states.

A retry may create another operation, cost, or duplicate result. Make important consequences understandable, and avoid unbounded silent resubmission. Use the simplest idempotency or duplicate-prevention approach available in the current system rather than constructing a general job platform by default.

## 4. Enable comparison where decisions happen

When users must choose among results, identify the actual evaluation criteria before adding side-by-side views, before/after comparisons, sorting, labels, or filters.

Control irrelevant differences so users can see what input or setting changed. A numeric “quality” or “similarity” score must have a real definition, evidence, and limitations; never substitute arbitrary ratings for users' own assessment.

Put next-step actions such as edit, reuse, continue, or save near the associated result. A handful of results rarely needs a tagging and filtering subsystem.

## 5. Recoverability does not make everything undoable

Define the recoverable unit, scope, lifetime, and actual persistence of history. Prefer existing undo, drafts, save states, or copies before adding a dedicated version database.

Distinguish draft input, submitted snapshots, running tasks, completed results, and the currently selected version. Editing a live draft must not silently mutate a previous submission's meaning.

An AI-assisted edit can often be treated as one undoable user action, but undoing it must not blindly overwrite independent changes made afterward.

Reverting a local edit does not reverse an external request, completed computation, message, or cost. “Retry,” “regenerate,” “restore,” and “undo” describe different operations and must not be interchanged.

## 6. Keep context and ownership stable

When users switch objects, pages, projects, or tasks, determine which input, scroll location, selection, and view state belongs to each context. Promise restoration across refreshes, restarts, or devices only when the real storage design supports it.

Attach asynchronous results to the object, request identifier, and relevant input snapshot that caused them. A late response must not overwrite the current context after the user switches away. Prefer non-disruptive notification of new results instead of stealing focus.

Batching, parallel processing, queues, and sessions should exist only when workload or organization requires them. Displaying several cards does not imply the backend can compute several jobs simultaneously.

## 7. Personalization is not the collection of every behavior

Apply this only if the product actually supports long-term personalization. Do not add a recommendation or memory subsystem because this reference mentions it.

Differentiate the current experiment, a project's settings, and long-term cross-project preferences. Make what influences recommendations understandable and give users effective means to correct, remove, or disable those influences where applicable.

Recommendation explanations must reflect actual data or rules; do not invent “because you like...” statements or pretend to expose hidden model reasoning.

A full preference timeline is optional. A small editable list or settings control may be enough. Disabling learning, deleting a saved preference, and deleting original content are separate operations with different effects; describe only what the system really does.

## 8. Choose appropriate complexity

Implement the smallest useful subset. A one-task generative tool might need only editable input, truthful waiting, retained results, and reusable settings. It should not automatically gain a scheduler, synchronized comparisons, a history graph, or preference explanations.

The design-consistency check covers only patterns actually chosen. Complete business logic testing, persistence testing, performance, and release obligations belong to the engineering task rather than this pattern reference.
