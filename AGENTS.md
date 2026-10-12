# Android architecture rules
- Keep the existing native Kotlin/JNI editor and inference engines; changes must not replace them with a web app.
- Keep screen orientation independent of artwork dimensions; size previews to the artwork aspect ratio without distorting source images.
- Fit legacy drawing bitmaps into the current canvas without overwriting the original files during loading, to retain source artwork.
- Keep high-resolution generation warnings and explicit user confirmation even when high resolution is the default, because mobile memory is limited.

- Store Studio-to-episode imports as immutable local flattened frame snapshots with cumulative timing and source offsets; preserve Studio layers and episode overlays.
- Use side-by-side preview/timeline and independently scrollable tracks on landscape phones; keep optional tools collapsible.
