# QueueGo native launcher family

Customer and Merchant retain the Production QueueGo Q family in red `#EF3340`. Rider intentionally uses the owner-approved heavier, cleaner Q in `#E6002D`: no wheel, flame, motorcycle, black detail, text, or prototype-style role glyph inside the mark.

All adaptive foregrounds stay inside the 33dp safe circle. Customer/Merchant service-symbol weight remains cross-checked within 10%; Rider is excluded from that role-glyph comparison because its approved icon is intentionally the Q alone. These source checks supplement visual review and do not claim physical-device launcher certification.

- `*-foreground.svg`: transparent 108dp adaptive foreground.
- `*-play-store.svg` / `*-play-store-512.png`: opaque 512×512 artwork.
- Android: independent foreground/background XML, adaptive resource, and 48/72/96/144/192px density PNGs.
- Rider: Android 13+ monochrome adaptive icon is generated from the same bold Q.

Regenerate vector/preview with `node native-android/branding/generate-icons.cjs`; integrate with `--integrate` after preview review. Export PNGs and geometry report with `node native-android/branding/export-icons.cjs` using Sharp. Verify committed assets with `node tests/native-launcher-assets.cjs`.

All changes are native assets only. Production web icons are unchanged. Do not distribute APKs before the visual, functional, and physical release gates pass.
