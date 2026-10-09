# Customer navigation and Home banner inspection — 2026-10-09

Actual rendered Production Shop and Home were inspected again through Chrome. These were desktop viewports (1363px and 1348px content widths). No viewport/mobile emulation or Android screenshot comparison was performed.

| Element | Observed web/source |
| --- | --- |
| Shop bottom nav | always visible, four equally spaced links; measured total49px |
| Home bottom nav | total59px after Home finishes loading |
| Nav top border | 1px #e9eaec; not a border surrounding every edge |
| Standard nav | vertical padding4; item padding2; icon area22; SVG20; label9 |
| Home nav | vertical padding6; item padding3; icon area24; SVG20; label10 |
| Nav icon stroke | 1.8; round cap and join; actual web path/circle geometry |
| Cart nav | fixed text “ตะกร้า”, quantity in a badge above the icon |
| Home banner | original rendered Rider/service collage; CSS aspect662/386; radius20 |
| Banner dots | inside image, bottom9; gap6; inactive7×7, active20×7 |
| Slide fallback | original HOME_FALLBACK_BANNER, not a red text card |

Native changes restore the always-visible Customer nav, Home/standard sizes, icon vectors from the Production SVGs and cart badge. Android navigation-bar insets are applied as the native equivalent of CSS safe-area bottom padding. Existing normal page selections follow the web's tab argument; authenticated account/support/detail pages still require rendered-session comparison.

Home reuses the exact original WebP bytes, SHA256 `1149f10b2608e9e183200d233676795dcb1200a065386c6b5cc6b6624c723972`, 45,722 bytes, without image generation or re-encoding. Configured photos draw over that fallback and failures retain the original photo. Existing automatic slide rotation and three-slot configuration remain; dots now select a slide inside the image.

Pending: Native screenshots, safe-area behavior on devices, fonts, shadows/blur, Home location/search/header/category/card geometry, configured banner links, configurable carousel rotation timing and all authenticated-page parity. No complete Home or visual gate pass is claimed.
