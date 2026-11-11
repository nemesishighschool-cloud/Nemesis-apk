# Predictive Back

Predictive Back lets users preview the destination of a Back gesture before committing. Design the preview, commit, and cancel states explicitly.

## Prefer supported behavior

Inspect whether navigation uses Compose Navigation, fragments, activities, or a custom stack, and which versions are installed. Use the platform/framework transition where appropriate. A historical dependency minimum in a design page is not a reason to pin an old alpha or upgrade a project.

The official guide distinguishes system transitions, full-screen custom surfaces, and shared-element transitions. Its custom shared-element recipe has stack-specific limitations. Verify those against current library documentation before adopting it; do not combine a generic 90% scale recipe with any navigation stack indiscriminately.

## Transition contract

| Phase | Visual behavior | State behavior |
| --- | --- | --- |
| Start | Establish preview of the correct destination | Keep current task intact |
| Progress | Respond continuously and coherently to gesture | No irreversible mutation |
| Commit | Finish the transition to the intended destination | Apply the navigation change once |
| Cancel | Restore pre-gesture position, scale, opacity, and focus as appropriate | Preserve draft and navigation state |

Only the system dismisses the app window. Do not suggest that swiping a detail preview deletes its underlying content or throws the item off-screen.

## Custom motion when justified

Read the live source for exact interpolation, progress thresholds, scaling, shift limits, and margins. Distinguish gesture progress from the visual interpolated progress. Maintain safe regions and understand how fast release/fling is handled.

Custom motion should clarify the relationship between destinations. If the project cannot support that motion reliably, retain correct native Back semantics and document the visual limitation.

Honor reduced/disabled animation behavior supported by the platform. Correct navigation must not depend on seeing an animation.

## Authored runtime checks

- Begin from both gesture edges where supported and cancel at several progress points.
- Complete slowly and with a fast release.
- Repeat after editing a form, scrolling detail, and opening a transient surface.
- Check destination preview against the actual committed destination.
- Test gesture navigation and ordinary button Back separately.
- Resize during an active journey and verify the next Back still has coherent meaning.
- Verify accessibility focus after the destination changes.

A screenshot proves neither cancellation safety nor predictive support. Record the device/emulator OS, target SDK, library versions, configuration, and observed result.

Source: [Predictive Back design](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back), reviewed 2026-10-04.
