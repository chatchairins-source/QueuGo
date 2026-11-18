# QueueGo — ตรวจความพร้อม Google Play สำหรับ Native Android

ตรวจวันที่ 14 พฤศจิกายน 2569 ตามเวลาไทย สถานะ **เตรียมเอกสารแล้ว / รอยืนยัน Release AAB, การทดสอบจริง, การตรวจทางกฎหมาย และ Play Console** ใช้เป็นชุดเตรียมส่งทั้งสามแอป

- [Data Safety และ Manifest](data-safety.md)
- [Privacy, Account Deletion, PDPA และงานส่งต่อ](privacy-and-handoff.md)
- [Store Listing ไทยและอังกฤษ และรายการหลักฐานสำหรับ Play Console](store-listings.md)

- [หลักฐานสำหรับกรอก Play Console และสคริปต์ตรวจรับ](play-console-evidence.md)

## ฐานหลักฐานและขอบเขต

- Web, เอกสารเดิม และ SQL: `main` ณ `369e4b49b20a0781f6d52be1c54aef5bd473609d` (merge #1031)
- Native Customer/Merchant/Rider: [draft PR #1057](https://github.com/chatchairins-source/QueuGo/pull/1057) ณ `f30e54020562cad7c72f1eb0ce454507e2a58342` มี Gradle และ Manifest ครบสามแอป ขณะที่ `main` ยังไม่มีไฟล์เหล่านี้ ตรวจ ancestry แยกกัน โดย checkout แบบ shallow ยังยืนยันจุดร่วมระหว่าง Native กับ main ไม่ได้
- [เอกสาร Play เดิม](../play-closed-beta-readiness.md) และ [listing เดิม](../play-store-listing-draft.md) เก็บหลักฐานสาย Capacitor เช่น Longdo และข้อห้าม RECORD_AUDIO ให้เทียบเฉพาะผลิตภัณฑ์ที่ส่งจริง

| งาน | หลักฐานขอบเขต | ขอบเขตเจ้าของ |
|---|---|---|
| Worker 1 | [#1057](https://github.com/chatchairins-source/QueuGo/pull/1057) | Native Android |
| Worker 2 | [#1041](https://github.com/chatchairins-source/QueuGo/pull/1041), [#968](https://github.com/chatchairins-source/QueuGo/pull/968) | Backend, Supabase, auth และ lifecycle |
| Worker 3 | [#1020](https://github.com/chatchairins-source/QueuGo/pull/1020), [#969](https://github.com/chatchairins-source/QueuGo/pull/969) | Web, Admin, POS Web และหน้า public |
| Worker 4 | [#1048](https://github.com/chatchairins-source/QueuGo/pull/1048) (`d74ba8214ddf4b455ccfc1535c4c5ae0d24aa5c7`), [#1036](https://github.com/chatchairins-source/QueuGo/pull/1036) | CI, QA, signing และ Release |
| Worker 5 | [#1055](https://github.com/chatchairins-source/QueuGo/issues/1055), [#1056](https://github.com/chatchairins-source/QueuGo/pull/1056) (`84983a5eeb25cb5614a1fc0a1e08c311f3d01be1`, สืบจาก main) | `docs/pilot-ops/**` |
| Worker 6 | ชุดเอกสารนี้ | ไฟล์ใหม่ใน `docs/store-readiness/**` |

## ผลตรวจในรอบนี้

- GET หน้า Privacy และ Account Deletion สาธารณะได้ HTTP 200 ทั้งคู่ เนื้อหาตรงกับ main แบบ byte-for-byte ตรวจโดยไม่ลงชื่อเข้าใช้
- สคริปต์บน main: `node tests/play-store-readiness.cjs` 36 ผ่าน, `node tests/store-readiness-lifecycle.cjs` 74 ผ่าน, `node tests/account-deletion-privacy.cjs` ผ่าน เป็นหลักฐาน source checks ของ repository
- หลักฐานที่ยังต้องแนบคือ merged manifest และ dependency tree ของ AAB จริง, ผลทดสอบสิทธิ์/การลบข้อมูลทั้งสามบทบาท, การยืนยันผู้ให้บริการและ PDPA และผลการตรวจใน Play Console
