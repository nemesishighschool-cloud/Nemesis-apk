---
name: android-ui-widgets
description: Design or review Android launcher widgets, including canonical layouts, responsive sizing, styling, configuration, picker previews, and contextual promotion. Use for App Widgets or Glance surfaces, not ordinary in-app components.
license: Apache-2.0
---
# Android widgets

Select one primary user benefit: glanceable information or a small useful action. A widget should not reproduce the entire app.

## Read by decision

- Content model, canonical composition, resize behavior, style: [layout, sizing, and style](references/layout-sizing-style.md).
- Setup, reconfiguration, picker representation and pinning: [configuration and discovery](references/configuration-and-discovery.md).

## Workflow

Identify the primary content/action and its refresh or stale behavior. Choose a text, toolbar, list, or grid layout. Define minimum, default, and larger useful presentations using actual available dimensions.

Specify what remains essential as space shrinks, what additional content appears when it grows, and where taps lead. Preserve usable targets and readable text. Account for the launcher/host's constraints.

Provide a useful default where possible. Require initial configuration only when necessary to make the widget useful. Define missing-account, missing-content, loading, stale, error, and recoverable setup states when relevant.

Use semantic colors, supported typography, and appropriate system corner treatment. Widget rendering is not the same as arbitrary Compose UI; verify AppWidget/RemoteViews/Glance capabilities in the project's versions before proposing interactions or animation.

## Validation

Exercise placement, actual minimum/default/maximum sizes, resizing, configuration cancellation/resumption, reconfiguration, multiple instances, theme changes, and deep links as relevant. Preview imagery must match what the user gets after placement.

Check more than one host configuration when claiming broad compatibility. Automotive or other hosts require their own implementation and safety guidance; phone launcher success does not prove those surfaces.

Sources: [Widgets overview](https://developer.android.com/design/ui/mobile/guides/widgets) and its linked subguides, reviewed 2026-10-04. The home-screen widgets URL is an overview alias, not a separate layout system.
