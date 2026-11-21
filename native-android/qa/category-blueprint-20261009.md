# Customer category blueprint inspection — 2026-10-09

Source: Production GitHub Pages rendered in the cloud browser; main commit `9026bf445650dbeeb0fd254582481a08a16108fc`. Screens rendered: `#food`, `#cafe` (the actual Drink route reached from Home's button), `#grocery`. The requested `#drink` alias redirects to login; Native admin configuration must use `cafe`, matching Production `qgServiceBannerMarkup`.

Browser viewport was 1363×936. This is a desktop rendering inspection, not the required mobile viewport/native screenshot comparison. No attached Android device or KVM was available; no Native capture or side-by-side gate is certified. Operational authenticated screens remain unavailable.

| Element | Rendered/CSS blueprint | Native repair in this change |
|---|---|---|
| Content | max-width 720px; 12px side padding | preserve 12dp category inset |
| Back/header row | padding 7px top/10px bottom; gap 8px | 38dp white arrow button, radius 12dp; 20sp bold title |
| Banner | width 696px; height 331.56px; ratio 720:343 | replace fixed height with the same aspect ratio |
| Banner corner | radius 20px; clipped photo | radius 20dp for image/container |
| Banner copy | top/right/bottom/left 30/18/16/18px; gradient to black 68% | same padding and transparent-to-black gradient |
| Banner title/subtitle | 20px/23px, 700; 12px/18px; 4px gap | explicit matching size/line height/weight |
| Section | 14px banner-to-section gap; horizontal title inset 2px | same source-derived inset and gap |
| Section title/helper | 17px bold; 10.5px helper; 4px gap | category-specific original text, count at right |
| Empty state | original category text; centered, padding 35px/15px | restore the web empty state, not a generic card |
| Shop card | 8px padding + 1px border; 9px gap; radius 16px | separate category card with equivalent 9dp outer-content inset |
| Shop photo | 78px; 72px at viewport <=420px; radius 13px | corresponding dp breakpoint/size |
| Shop metadata | category and actual `public_open_time`/`public_close_time` | select existing Production fields; no invented hours |

Native shadows use Android elevation and the web's tint/opacity as an approximation; CSS blur/elevation are not identical renderers. Typography uses platform fonts; no native/device pixel match is claimed. Disabled banners disappear as on the web; empty/invalid configured URLs use the exact bundled web fallback, while a failed configured image leaves the web's gradient treatment.

The three bundled WebP resources are decoded directly from main's existing inline constants, without cropping or re-encoding. `tests/native-category-assets.cjs` compares actual image bytes with the web blueprint. Real grocery catalog rendered one server-provided shop; no shops/products/orders were created for this inspection.

Remaining gates: mobile browser captures, actual Native captures, all screenshot comparisons, global header/bottom navigation parity, Guest-vs-login launch behavior, banner link navigation parity, other categories, Shop/Cart/Tracking and authenticated Merchant/Rider pages, and live three-role normal/market/laundry E2E. This change does not certify the three apps complete or authorize APK delivery.
