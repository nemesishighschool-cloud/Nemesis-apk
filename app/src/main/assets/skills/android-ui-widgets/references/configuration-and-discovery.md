# Widget configuration and discovery

## Configuration

Require setup during placement only if content cannot be useful without it or customization is central. Otherwise offer a desirable default and allow later reconfiguration.

Keep the setup focused, usually one or two screens where possible. Show a preview for appearance choices, progressively disclose advanced options, and make the finish/add outcome obvious.

Define interruption and cancellation explicitly. The design guide recommends recoverable unconfigured states; verify what the actual widget host and configuration API permit. Do not promise that a widget is placed after cancellation if the host removes it. When an instance exists but lacks content/account access, give it a useful configuration or authentication entry point.

Preserve each widget's configuration independently. Reconfiguration should not silently alter other instances.

## Picker and promotion

Provide an accurate preview and concise value description. The size, shape, and visible content should match initial placement, including relevant dynamic color behavior.

Show distinct use cases rather than many entries for colors or shapes. The guide suggests a bounded set of roughly 6-8 picker variations at most; use configuration for further customization. This is a recommendation, not a platform maximum or a target to fill.

Promote pinning when the feature is relevant, such as after a related successful task. Keep the suggestion optional and do not block the user's main action. A design for pinning is not authorization for an agent to pin a widget on someone's device.

## Authored acceptance

Check picker preview versus placed output, default size on supported hosts, completed and interrupted setup, missing data/account, reconfiguration, two independent instances, and resize after customization. Confirm pinning result from the host rather than assuming a request was accepted.

Sources, reviewed 2026-10-04:
- [Widget configuration](https://developer.android.com/design/ui/mobile/guides/widgets/configuration)
- [Discovery and promotion](https://developer.android.com/design/ui/mobile/guides/widgets/discovery-promotion)
