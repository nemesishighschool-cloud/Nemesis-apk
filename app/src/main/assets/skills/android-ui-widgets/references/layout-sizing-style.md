# Widget layout, sizing, and style

Choose the composition from one main benefit:
- Text for a concise status or fact; add imagery only when it helps.
- Toolbar for a few frequent actions, optionally centered on search.
- List for messages, tasks, or other scannable entries.
- Grid for a visual collection with optional labels.

A checklist needs distinct usable targets and immediate feedback. A toolbar may reveal more actions when room permits. Some newer layouts, such as full-bleed snap scrolling, depend on Android versions and host support; verify before specifying them.

## Responsive sizing

Treat recommended cell sizes and Pixel-derived dp ranges as starting points, not universal launcher geometry. Define target cells and usable minimum dimensions using current APIs. Test actual assigned width and height.

Use breakpoints where content needs change. Keep the principal content/action, then reveal secondary content as space grows. Do not shrink text or targets until the whole phone screen fits.

Fill the allocated widget bounds instead of adding arbitrary outside padding; retain necessary internal content spacing and system corner treatment. Non-rectangular shapes should align usefully with the available grid. Do not enforce a fixed square at every size.

## Styling

Use semantic color roles with dynamic, light, dark, and fallback behavior as supported. Check contrast on the actual home-screen surface. Use system corner-radius conventions for rectangular widgets; expressive shapes are most useful for simple content.

Establish a readable type hierarchy. Exact type tokens should come from the actual widget toolkit/design system, not an inconsistent role list copied from prose. System fonts and rendering limitations differ from normal app UI.

## Runtime checks

Test minimum, default, and enlarged sizes; long content; loading/stale/error; theme changes; tap targets and accessible names. Check instance-specific state when multiple widgets exist.

Phone launchers, tablets, and automotive hosts are not interchangeable. The source notes that Android Auto widget lists do not scroll; consult dedicated car guidance when that host is in scope.

Sources, reviewed 2026-10-04:
- [Widgets](https://developer.android.com/design/ui/mobile/guides/widgets)
- [Canonical layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts)
- [Sizing](https://developer.android.com/design/ui/mobile/guides/widgets/sizing)
- [Style](https://developer.android.com/design/ui/mobile/guides/widgets/style)
- [Home-screen widgets overview alias](https://developer.android.com/design/ui/mobile/guides/home-screen/widgets)
