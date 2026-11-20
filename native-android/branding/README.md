# Approved QueueGo native launcher family

Owner selected the connected-symbol red family on 2026-11-08; the selected visual reference remains in the conversation; `family-preview.png` shows the native vector implementation.

- Customer: **QueueGo**, shopping bag.
- Merchant: **QueueMarket**, storefront.
- Rider: **Queue Rider**, forward road arrow.

All three use white artwork on red `#EF1717`, one thick circular Q, a short low tail, and a role symbol joined to its lower inner rim. English wordmarks are outlined paths, so installed devices do not need a font. Native vectors reproduce the approved design; the generated reference is retained for comparison.

`generate-icons.cjs` is the shared geometry source for adaptive foreground/background XML, Android 13 monochrome resources, SVG/store assets and previews. Run `node native-android/branding/generate-icons.cjs --integrate`, then `node native-android/branding/export-icons.cjs` (Sharp). `wordmarks.json` contains reusable outlined DejaVu Sans Bold lettering; regenerate only when names change using `python3 native-android/branding/generate-wordmarks.py` (fontTools; optional `QG_BRAND_FONT`).

Exports include 512px store PNGs and 48/72/96/144/192px legacy launcher PNGs. Adaptive foreground geometry, including the names, must stay inside Android's 33dp safe circle. Export measures actual rasterized alpha before writing `geometry-check.json`. Android applies its launcher mask to the independent full-bleed red background.

Validate with `node tests/native-launcher-assets.cjs` and `node tests/native-android-source.cjs`. Source checks do not certify installed-device rendering. This change does not alter package IDs, backend, navigation, POS, signing, or Production Web assets. Existing installed APKs retain their previous icon until a new build is installed.
