# Android architecture rules
- Keep the existing native Kotlin/JNI editor and inference engines; changes must not replace them with a web app.
- Keep screen orientation independent of artwork dimensions; size previews to the artwork aspect ratio without distorting source images.
- Fit legacy drawing bitmaps into the current canvas without overwriting the original files during loading, to retain source artwork.
- Keep high-resolution generation warnings and explicit user confirmation even when high resolution is the default, because mobile memory is limited.
