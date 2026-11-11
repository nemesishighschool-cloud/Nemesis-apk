# Type, shape, icons, and motion

Use a role-based type scale that matches the project's design system. Define display, heading/title, body, and label needs rather than selecting sizes per screen. Use sp and test long labels, translated text, and enlarged fonts.

The Android theme guide permits custom fonts and custom design systems. Roboto is a familiar default, not a requirement to replace a suitable brand face. Check legibility and character coverage.

Shape should communicate a coherent product character and component hierarchy. Use supported shape tokens rather than unrelated corner values. Clipping must not hide focus indicators, content, or useful touch area.

Use one coherent icon family and style. Pair unfamiliar icons with labels, provide semantics for icon-only controls, and distinguish an icon's visible bounds from its touch target. Verify direction-sensitive icons for RTL rather than mirroring every symbol.

Motion should explain entry, exit, hierarchy, or feedback. Preserve a comprehensible static/reduced-motion experience. Predictive Back is behavioral work: use [its reference](../../android-ui-patterns/references/predictive-back.md) instead of adding a generic page animation.

For implementation, inspect actual typography/shape/theme APIs. The two Styles pages are not a complete motion or typography specification; follow their current Material links when exact specifications are needed. Do not claim exhaustive Material 3 coverage from this bundle.

Authored review: compare the same content across theme variants, enlarged text, disabled animation, selected/error states, and image backgrounds. A consistent visual language should survive all of them.

Sources, reviewed 2026-10-04:
- [Themes and theme attributes](https://developer.android.com/design/ui/mobile/guides/styles/themes)
- [Platform translation and motion](https://developer.android.com/design/ui/mobile/guides/foundations/translate-designs)
