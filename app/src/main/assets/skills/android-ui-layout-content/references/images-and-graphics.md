# Images, graphics, and asset handoff

## Select the asset representation

Use vector assets for simple scalable shapes and icons where the Android rendering path supports them. An arbitrary SVG is not automatically a valid VectorDrawable; verify conversion support. Use raster formats for photos or complex imagery and provide appropriate resolution resources.

Do not bake meaningful UI text into images. Keep it localizable, scalable, and accessible. Use a description for meaningful images; decorative images should not produce redundant screen-reader output.

Name Android resources in lowercase with a consistent convention. Density variants belong in resource directories rather than resolution suffixes in the resource identifier.

## Specify image behavior

For every image role, define:
- Intrinsic content and focal point.
- Container size or aspect policy at each relevant window range.
- Fit versus crop and alignment.
- Loading, missing, and failed states.
- Contrast protection where controls or text overlap.

Choose one of fixed aspect ratio, adaptive ratios, or fixed height with flexible width according to the content. A fixed ratio is appropriate when content integrity requires it; it is not mandatory for every hero image.

A photo may crop around its subject; a diagram or document often must fit without losing information. Avoid stretching either. Test portrait source media inside landscape containers and the inverse.

## Effects and motion

Gradients, tint, clipping, blur, and blending can be runtime effects. Check performance and platform support before choosing them. Avoid a blur that is the only means of making text readable on unsupported devices.

Use small programmatic or vector animations when they fit the interaction and rendering stack. Provide a stable, understandable state when animation is reduced or absent.

## Launcher and launch assets

The official image guide also links to adaptive/legacy app icons, monochrome system assets, and splash screens. Include these in asset handoff when in scope; they are not launcher widgets. Verify current icon and splash specifications through those linked developer guides before exporting dimensions.

Authored acceptance examples: the subject is preserved at every crop; no text exists only in a bitmap; contrast survives bright and dark photos; the loading box does not shift the primary action; exported assets match runtime format support.

Source: [Images and graphics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/images-graphics), reviewed 2026-10-04.
