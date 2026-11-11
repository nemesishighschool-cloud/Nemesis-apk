# Color roles and themes

Assign color by semantic role: surface, content on a surface, primary action, container, outline, error, and product-specific meanings. Use tokens rather than repeated literal colors.

Material's HCT model separates hue, chroma, and tone. Tonal relationships help build contrast; a brand hex value is not automatically suitable for every role. Check actual foreground/background pairs after customization.

## Choose a theme source

Dynamic color can personalize the app from wallpaper or, selectively, content. Provide a branded static fallback when unavailable. Avoid extracting unrelated palettes from many content items within one screen.

Support light and dark themes and respect the user's system choice. Keep semantic meaning stable between variants. An app-specific override can be useful, but should not silently defeat accessibility or personalization.

Use a clear hierarchy: vibrant accent roles attract attention, container roles offer lower emphasis, and surfaces organize the screen. Repeating maximum-saturation primary color everywhere removes hierarchy.

Custom semantic colors should have a defined purpose and readable on-colors. Keep mappings consistent across success, warning, membership, or other product states. Color must not be the sole indicator.

## Theme verification

Check contrast in actual states and backgrounds, including dynamic palettes, long text, scrims, selection, errors, and system chrome. A generated Material scheme is a starting point; later customizations can break it.

Authored handoff: document each changed role, its purpose, light/dark/fallback behavior, impacted components, and measured contrast. Include meaningful preview content rather than only color swatches.

Sources, reviewed 2026-10-04:
- [Color](https://developer.android.com/design/ui/mobile/guides/styles/color)
- [Themes](https://developer.android.com/design/ui/mobile/guides/styles/themes)
