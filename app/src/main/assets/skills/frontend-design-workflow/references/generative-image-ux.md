# Generative Image UX — Optional Scenario Reference

Use for drawing, image-generation, and image-iteration products. This translates **prompt → generation/waiting → selection → iteration** into optional patterns. It does not require every product to implement all features.

These are design recommendations, not unattributed research claims or guarantees about image quality, processing time, exact reproducibility, or cost-free undo.

## 1. Start from real capabilities

Inspect which controls the actual model and service support: effective prompt, reference images, parameter ranges, seeds, model/version, batch size, queue states, progress events, estimated time, cancellation, result retention, and reuse.

Mark unknown capabilities as unknown. Do not assume support for negative prompts, style controls, image-to-image, inpainting, upscaling, or deterministic seed reproduction. UI visibility, labels, and limitations follow the real implementation, not a competitor's screenshot.

## 2. User Flow

A possible flow is:

```text
Open workspace -> Input/reference/preset -> Inspect effective settings -> Submit
    -> Wait with real status -> Review results -> Select -> Save or iterate
                         |-> Failure -> Preserve input -> Revise or retry
Past result -> Reuse settings or edit -> New result (keep original)
```

An effective-settings review may be a small inline expandable summary; it need not become a confirmation page. Reuse existing project/document organization rather than automatically adding a new session model.

## 3. Prompting: optional structure, not a fixed multistep wizard

For beginners, offer optional cues such as subject, style, composition, light, mood, and color. Do not make all fields mandatory or force experienced users through each one.

Preserve direct editing. Presets, labels, and recommendations must be removable. Before generation, make the effective prompt and material parameters inspectable, clearly distinguishing the user's original input from system enhancements.

If free text cannot be converted losslessly into fields, preserve the original and identify unresolved fields instead of guessing over the user's work. Coordinate guided/free-form editing through an explicit state model so mode changes do not append duplicate text or lose edits.

Suggested terms help expression; they must not automatically keep appending purported “quality terms” without selection. Style previews show direction rather than guaranteed output; do not imply the reference image is the inevitable result of the current settings.

## 4. Generation: submitted snapshots and actual state

Associate every submitted job with a snapshot of effective input and settings. Edits while a job runs apply to future submissions unless the service really supports in-flight changes.

Map submitted, queued, running, finished, failed, and cancellation states only to events the backend supports. If internal stage events are unavailable, show known waiting/submission status rather than inventing phases such as “parsing prompt.”

Show approximate ranges only with a reliable estimate; otherwise show real elapsed time and known status. Estimates are not completion promises.

Distinguish *cancellation requested* from *cancellation confirmed*. Stopping frontend polling or hiding a result is not the same as stopping remote computation, and cancellation need not reverse fees.

Introduce multiple concurrent jobs only when product requirements and backend capacity justify them. Multiple visible task cards do not imply concurrent inference. Clarify real queueing or concurrency behavior. If retry may cause another charge, surface that consequence and avoid infinite automatic retries.

## 5. Selection: support evaluation without result overload

For a small result set, prioritize clear thumbnails and detail viewing. When comparisons matter, add side-by-side and optionally synchronized zoom. Different aspect ratios should preserve fair viewing rather than being stretched into deceptive uniform boxes.

For many results, consider lightweight shortlist/unsure/hidden states and filtering. Hiding or rejecting a result should not silently delete its original asset.

Add similarity or quality rankings only if a meaningful real scoring capability exists and the user benefits; do not present a score as objective aesthetic truth.

Place supported next actions by each result. Distinguish **regenerate using settings**, **create a variation conditioned on an image**, **edit image**, and **upscale**. Do not call a prompt rerun a composition-preserving variation when the backend cannot do that.

## 6. Iteration: preserve provenance, not a promise of exact reproduction

When available, retain the effective prompt, material parameters, model/version, seed, reference-asset identifiers, and parent-result relationship. Store only what the product needs and respect prompt/image privacy.

“Reuse settings” copies old settings into a new draft; it does not rewrite history. If server defaults, model versions, or random conditions cannot be recovered fully, say so. Reusing settings does not guarantee the same output.

Undo/redo applies to recoverable edit state. It does not imply that completed remote work or charges can be undone. Undoing an applied AI result must respect independent edits made after it.

Start with a simple flat history or parent reference. Add a visible branching tree only for actual multi-direction exploration; do not build a general version-control platform for every generation task.

Restore history after navigation, reload, or project switches only to the extent supported by actual storage. In-memory results are not persistent history.

## 7. Recommendations and preferences

Apply only when recommendation/personalization exists or the task explicitly asks for it. Separate “use for this generation” from “save as long-term preference”; one style experiment, rejection, or accidental click is not unconditional permission to update a lasting profile.

Explain recommendations according to the real source or rule; do not fabricate personalized rationales. Reuse existing preference settings. A full preference timeline remains optional complexity.

## 8. Match depth to the product

| Scenario | Start with | Do not infer automatically |
| --- | --- | --- |
| Individual occasionally generates a few images | Editable input, truthful state, retained originals, reusable settings | Job scheduler, elaborate tagging or branch tree |
| Frequent iteration on one subject | Effective snapshots, comparisons where valuable, history, context recovery | Cross-team permissions, generalized recommendation platform |
| Large-batch or professional workflow | Job organization, filtering, comparisons, queue/concurrency/cost semantics | Unverified real-time progress or automatic quality rankings |

## 9. Relevant design-consistency scenarios

Only for implemented capabilities, examine: editing a prompt during generation; a late job completing after switching contexts; a cancellation arriving around completion; reloading and recovering history; reusing settings from a model version no longer available; undoing an AI edit while preserving later independent work.

Distinguish actual interactive checks, static code reasoning, and unverified behavior. Do not create a subsystem just because a scenario appears in this reference.
