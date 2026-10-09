# QueueGo native launcher family

The three launcher symbols share the same Q contour, Production red `#EF3340` and white background. Customer uses fork/spoon; Merchant uses storefront; Rider uses a wheel/flame, without a detailed motorcycle or role badge outside the Q. App names appear only as preview labels.

`family-preview.png` was rendered and visually inspected before Android integration, including 48/32/24px samples and circular adaptive masks. Merchant stroke weight was reduced after the first coverage measurement. Final role coverage spread is 8.44%; every foreground stays within the 33dp adaptive safe circle. These metrics supplement visual review; they are not a claim of Owner approval or physical-device launcher certification.

- `*-foreground.svg`: transparent 108dp adaptive foreground.
- `*-play-store.svg` / `*-play-store-512.png`: opaque 512×512 artwork with the same center crop as Android's adaptive launcher.
- Android: independent foreground/background XML, adaptive resource, and 48/72/96/144/192px density PNGs. Minimum SDK is 26.

Regenerate source/vector/preview with `node native-android/branding/generate-icons.cjs`; integrate with `--integrate` after preview review. Export PNGs and geometry report with `node native-android/branding/export-icons.cjs` using Sharp 0.35.4. This is an asset-generation tool, not an application runtime dependency. Verify committed assets with `node tests/native-launcher-assets.cjs`.

All changes are native assets only. Production web icons are unchanged. Do not distribute APKs before the visual and functional release gates pass.
