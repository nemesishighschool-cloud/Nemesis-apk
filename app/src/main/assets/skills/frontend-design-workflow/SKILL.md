---
name: frontend-design-workflow
description: Use when designing or revising web, desktop, or mobile interfaces, including new screens, user journeys, interaction states, frontend redesigns, and creative AI tools. Follow Context → User Flow → UX → UI → Implementation → Design Consistency Check. Reuse existing flows for small changes; do not require a new document, state framework, or approval stage for every task.
metadata:
  version: "1.1"
---

# Frontend Design Workflow

## Purpose and boundaries

Help users understand where they are, what actions are available, what an action will change, and how to recover from failure. Establish a coherent **flow and interaction** before focusing on visual styling.

Default sequence: **Context → User Flow → UX → UI → Implementation → Design Consistency Check**.

This is a scalable decision sequence, **not** six mandatory deliverables or a stage-gate approval process. Small local changes can reuse established flows and UX. Return to earlier stages when implementation reveals a real interaction problem.

The workflow is independent of supervised or autonomous agent modes. The active agent instructions determine how major decisions are surfaced. **Autonomous development does not remove the product user's control over their actions or data.**

This skill governs design methodology and consistency with the implemented interface. Functional correctness, integration tests, performance goals, security checks, and release acceptance remain under the task and engineering rules. Do not claim this design check proves a complete software release.

**Communication language:** Use the language of the user's latest request for user-facing replies, unless the user explicitly requests another language. For multilingual requests, use the dominant language or established conversation language. Keep source code, component names, and technical identifiers as written. The reusable skill and templates themselves stay in English.

## Load references only when useful

- For complex input, asynchronous operations, multiple results, editing history, persistent context, or personalization, read [general UX patterns](references/ux-patterns.md).
- For image generation, prompting, selecting results, or generative creative iteration, additionally read [generative image UX](references/generative-image-ux.md).
- For significant redesigns or interconnected flows/states, consult [design consistency reference](references/design-consistency.md).
- When a durable project design record is warranted, adapt the [DESIGN.md template](assets/DESIGN.template.md). Prefer updating an existing design record rather than creating a competing one.

These references contain **conditional patterns, not default feature requirements** or guaranteed outcomes.

## 1. Understand context

Inspect the actual user outcome, current product behavior, neighboring flows, affected screens/components, existing design system, supported platforms, and constraints that genuinely affect interaction.

Separate explicit requirements and current decisions from examples, screenshots, historical suggestions, and assumptions. Do not copy an inspiration screenshot's product features, marketing copy, or page layout as requirements without evidence.

Determine which product capabilities are **supported, partially supported, unsupported, or unknown**. For progress, cancelation, undo, persistence, version history, estimates, or recommendations, inspect the real APIs and state sources. Do not invent backend abilities to justify an attractive UI.

Resolve ordinary gaps by inspecting the implementation and adjacent flows. Make minimal, reversible assumptions when evidence is incomplete. Record only assumptions that affect meaningful design behavior; handle consequential forks according to the active agent mode.

## 2. User Flow: define how the goal is completed

For each affected flow, identify:

- **User and context:** Who is acting, and under what circumstances?
- **Entry point and goal:** How do they start, and what result do they expect?
- **Primary path:** The shortest ordinary path to completion.
- **Decisions and branches:** Only material choices and alternate paths.
- **Completion, exit, and return:** What happens on success, cancellation, abandonment, or revisit?
- **Failure and recovery:** Realistic interruptions, errors, and how to resume.
- **Context ownership:** Which state should survive navigation or a retry?

Use a compact flow only when it adds clarity:

```text
Entry -> Input or selection -> Action -> Result -> Next step
                            |-> Failure -> Edit or retry
Leave or cancel -> Preserve relevant context -> Resume when supported
```

This is an illustrative structure; it does not require every product to implement generation, sessions, comparison, or version branching.

Remove unnecessary steps, choices, settings, and confirmation dialogs. Match protection to actual irreversibility, cost, and misclick risk; favor recovery when an action is reversible.

**Flow check:** Can the user reach the outcome, exit or return coherently, and recover from important failure without getting stranded?

## 3. UX: turn the flow into understandable interactions

### Information structure and behavior

Give each screen, panel, and dialog a clear responsibility. Keep primary content and actions prominent, put related controls near the affected object, and reuse established navigation and mental models.

For each meaningful action, define its preconditions, target object, immediate feedback, in-progress state, completion, interruption, failure response, and actual undo/cancel/return semantics. Handle plausible states at real boundaries; do not design a defensive state for every imaginable failure.

### UX principles

- **Optional input assistance:** Examples, presets, tags, and structured fields can lower the entry barrier, but should not force proficient users into a wizard. Preserve direct editing when appropriate.
- **Predictable effective behavior:** Make important parameters, automation, and scope understandable. Do not silently rewrite user intent or promise uncertain results.
- **Truthful visible state:** Use actual backend events and status. Unknown progress remains unknown; do not fabricate percentages, stages, estimates, or success indicators.
- **Appropriate control:** When actually supported, expose cancel, retry, continue, edit, or recovery. Distinguish stopping a local wait, hiding a result, requesting cancellation, and confirmed backend cancellation.
- **Decision-ready results:** Display relevant comparison information and next actions near the resulting item. Avoid option-heavy workflows when the user has only a simple decision.
- **Recoverable exploration:** Do not destroy originals through exploratory edits. Prefer existing undo, draft, snapshot, or copy mechanisms over an automatic version-tree subsystem.
- **Continuous context:** Preserve object-specific inputs, selections, and locations when switching tasks. Late asynchronous responses must remain associated with the request that created them.
- **Manageable personalization:** Only when personalization exists, distinguish a one-off experiment from a long-term preference, with meaningful correction, deletion, or opt-out where supported.

**UX check:** Do information structure, actions, and feedback truly support the chosen user flow without introducing unnecessary complexity?

## 4. UI: express hierarchy and state visually

Start with visual hierarchy, typography, spacing, grouping, alignment, and contrast before decoration. The appearance should support the product's real task; do not default every tool to a card-heavy dashboard or a promotional landing page.

Reuse existing components and design tokens. Add a component or token when a concrete interaction, state, or repeated pattern requires one—not merely to create a broader design system.

Communicate routine states with familiar icons, shape, position, hierarchy, actual progress, and restrained motion. Color is a supporting cue, never the sole carrier of critical meaning. Add concise labels and accessible names where they improve clarity or are essential.

Keep text for action labels, consequential status details, useful errors, recovery directions, and irreversible effects. Avoid unsolicited slogans, marketing phrases, conversational filler, redundant status narration, and decorative paragraphs. Such copy can be appropriate when the product explicitly requires marketing or storytelling.

**Do not leak internal reasoning, agent instructions, engineering constraints, TODOs, or design-process notes into the user-visible interface.** Keep developer assumptions and implementation caveats in design records or delivery notes. Real restrictions that affect the product user must still be communicated clearly.

## 5. Platform, accessibility, and motion

Design only for platforms and device types actually supported by the task. Responsive adaptation preserves task priority and the flow rather than simply shrinking desktop content.

Address the relevant keyboard operation, focus order and visibility, semantics, accessible icon names, reading order, contrast, touch targets, text scaling, and reduced-motion preferences. Prefer native web semantics or their platform-native equivalents.

Use motion to clarify continuity, causality, navigation, reordering, or real status transitions. Do not block interaction with decorative animation or pretend time-based animation is actual backend progress. Important meaning cannot depend on color or motion alone.

## 6. Implementation: build the defined interaction

Before editing, make sure the flow, significant states, affected objects, screens/components, and supported platform behavior are clear enough to implement. A complete high-fidelity mockup or long design document is not required.

Follow the existing architecture and implement the simplest complete solution. If backend capabilities fall short, adjust the interface's promise or implement the needed real behavior **within task scope**. Do not disguise missing features behind fabricated data, fake cancel/progress controls, or inert buttons.

Use simulated states only for explicitly requested prototypes/demos, and identify them as simulations. Do not claim prototype interactions are integrated with a real backend. Avoid unrelated redesigns and incidental framework migration.

## 7. Design Consistency Check

Check the implementation against this task's design decisions:

- **Flow:** Do real entry points, user actions, outcomes, return paths, and important recovery paths match the intended journey?
- **UX:** Are feedback, focus, state, interruption, cancel/undo semantics, and asynchronous result ownership correct? Are any controls seemingly enabled but nonfunctional?
- **UI:** Are hierarchy and status clearly expressed? Are there blocking clipping/overflow issues on supported sizes? Are relevant accessibility semantics preserved?

When possible, inspect the actual rendered interface and exercise representative paths. A screenshot does not prove interactions; code inspection does not prove visual correctness. List what was not checked rather than inventing verification.

**This is alignment between design and implementation—not a sign-off that all software tests, backend behavior, performance requirements, or release gates passed.**

## 8. Design records and handoff

Update a project design record only for significant new flows, cross-screen state ownership, reusable interaction patterns, or other durable choices. Preserve current decisions and meaningful assumptions, not every discarded proposal or a copy of this skill.

Report concisely in the user's language: what flow and interaction changed, which views were affected, what was actually inspected or exercised, and any real capability gaps or unverified claims. Do not invent a separate design-approval protocol; follow the active agent mode.
