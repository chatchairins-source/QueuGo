# Customer Shop blueprint inspection — 2026-10-09

Source: Production main `9026bf445650dbeeb0fd254582481a08a16108fc`, actual rendered public Shop opened through Grocery, store `b2012f75-0370-4fe9-be04-6e858bf6e4aa`. No Production catalog, favorites, reviews or orders were mutated during inspection.

The browser viewport was 1363 × 936. This is desktop evidence, not a mobile or Native pixel comparison. No Android device/emulator is attached.

| Element | Rendered/source measurement |
| --- | --- |
| Store cover | 720 × 195; source <=420px: 178px; viewport height <760px: 164px |
| Cover controls | 42px; radius12; top12; left/right14 |
| Store intro | white; rounded top20; overlaps cover18; desktop padding14/12/9 |
| Store heading | 21px, weight800, line-height24.15 |
| Store favorite | separate row below heading, 32px high; not placed beside heading |
| Store category/address | 11px; gap8; margin-top5 |
| Product row | white; padding9/12; gap9; image72×66 radius13 |
| Product name/price | 14px; description10px, max2 lines |
| Add button | round36px; icon19px; gray when store closed |
| Unavailable product | remains visible with “หมด”; add button absent |
| Filters | “ทั้งหมด”; padding12px vertical; Production loadMenu currently does not select category/menu_category |
| Reviews | width696; white radius16; padding12/13; top margin10 |
| Review heading | 18px; line-height21.6 |
| Latest review | initially visible; remaining5 revealed by “ดูรีวิวทั้งหมด” |
| Expanded review list | source max-height310; independently scrollable |
| Cart dock | source fixed above bottom navigation, red radius15, separate count/amount |

Implemented Native body repairs: original cover-only image choice, overlapping info panel, dedicated back/share controls, shop and product favorites using `qg_customer_favorites`, reviews using `qg_public_shop_reviews`, folded review list, real unavailable inventory display, product row sizing, cart dock and return-to-origin navigation. Back/plus vectors preserve the web SVG paths. Favorite/share are clean vectors; the web uses heart and diagonal-arrow glyphs, so their exact font geometry remains a visual comparison item. No backend or web UI changed.

Validation is build/source/regression only. Production favorites writes, native share sheet, authenticated review retrieval, mobile breakpoints, shadows, global header/navigation, category data omitted by the existing web query, menu loading/error races, and native screenshots still require review. No Visual Parity or Functional Parity pass is claimed. No Owner APK delivery.
