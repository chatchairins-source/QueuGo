# ตัดสินใจเปิดพื้นที่นำร่อง

สถานะเริ่มต้น **NO-GO · PENDING/NOT VERIFIED** ณ 14 พ.ย. 2569 (ICT) ผู้ประสานพื้นที่รวบรวมหลักฐานจากเจ้าของงาน เจ้าของโครงการหรือผู้ได้รับมอบหมายอนุมัติการเปิดตามพื้นที่ ช่วงเวลา บริการ และรุ่นที่ระบุ

## รายการตรวจรับ

| ตรวจ | เจ้าของหลักฐาน | หลักฐานที่ต้องแนบและสถานะ |
| --- | --- | --- |
| ☐ พื้นที่และบริการ | เจ้าของโครงการ / Pilot Ops | ขอบเขตจุดรับ–ส่งจากการตั้งค่าจริง บริการและช่องทางชำระที่เปิด เวลาเปิด–ปิด รายชื่อร้าน/ไรเดอร์ และผู้รับเวรสำรอง · **PENDING/NOT VERIFIED** |
| ☐ Migration และสิทธิ์ Production | Worker 2 / Worker 4 | ผลเทียบ migration ledger กับรุ่นที่จะเปิด และผลตรวจ Auth/RLS/RPC ตามบทบาท Customer, Merchant, Rider, Admin · [PR #968](https://github.com/chatchairins-source/QueuGo/pull/968), [PR #909](https://github.com/chatchairins-source/QueuGo/pull/909) · **PENDING/NOT VERIFIED** |
| ☐ สำรองและกู้คืน | Worker 4 | รายงานสำรองแบบเข้ารหัส ผลคืนข้อมูลและตรวจ checksum ในสภาพแวดล้อมแยก พร้อมผู้รับผิดชอบและแผนกู้คืน · [เกณฑ์ PR #1048](https://github.com/chatchairins-source/QueuGo/pull/1048), [workflow](../../.github/workflows/backup-restore-drill.yml) · **PENDING/NOT VERIFIED** |
| ☐ ออเดอร์ครบวงจรและยอด | Worker 4 / Worker 2 | รายงาน QA บน release candidate ที่ระบุ SHA และสภาพแวดล้อม ครบ Customer สั่ง → Merchant รับ → Rider รับ → Delivered → Settlement พร้อมผลตรวจยอดและเคสสถานะผิดปกติตามเกณฑ์ Worker 4 · [PR #1048](https://github.com/chatchairins-source/QueuGo/pull/1048) · **PENDING/NOT VERIFIED** |
| ☐ Web/POS ตามบริการที่เปิด | Worker 3 / Worker 4 | ผลตรวจตามบัญชีและสิทธิ์จริงของชุดทดสอบ รายการบาร์โค้ด ขาย/ปิดบิล ช่องทางรับเงินที่เลือกใช้ และผลตรวจร่างเมื่อเชื่อมต่อใหม่ถ้ารวมในขอบเขต · [PR #1013](https://github.com/chatchairins-source/QueuGo/pull/1013) มีหลักฐานเฉพาะ UI · **PENDING/NOT VERIFIED** |
| ☐ Android และนำทาง | Worker 1 / Worker 4 | ผล Emulator ของรุ่นเดียวกับที่จะเปิด ครบ Login, GPS, Push, offline/return-to-app และ Rider Offline Map/เส้นทาง/คำนวณใหม่ตามเกณฑ์ Worker 4 · [PR #1048](https://github.com/chatchairins-source/QueuGo/pull/1048) · **PENDING/NOT VERIFIED** |
| ☐ Signed APK 3 แอป | Worker 4 | ลิงก์ดาวน์โหลด Customer, Merchant, Rider แยกแต่ละตัว พร้อม version, source SHA, ผลตรวจลายเซ็นและ fingerprint, SHA-256 และหลักฐานตรงกับรุ่นที่ทดสอบ · [Release Gate](../../RELEASE_GATE.md), [PR #906](https://github.com/chatchairins-source/QueuGo/pull/906), [build workflow](../../.github/workflows/build-native-release.yml) · **PENDING/NOT VERIFIED** |
| ☐ เวรสนับสนุนและแผนหยุด/เปิดใหม่ | Pilot Ops / Worker 4 | รายชื่อเวรหลัก/สำรอง ช่องทางที่ทดสอบแล้ว ผู้มีสิทธิ์พักงาน แผนรับออเดอร์ค้าง คืนรุ่น/กู้ระบบ ซ้อมเคสและบันทึกผล · [คู่มือเวร](README.md), [Runbook](incident-runbook.md) · **PENDING/NOT VERIFIED** |

## ตรวจย้อนจากขั้นตอนปฏิบัติงาน

| ขั้นตอน | ซอร์สหรือ PR ที่ตรวจ | สถานะหลักฐาน | สิ่งที่ยังขาด |
| --- | --- | --- | --- |
| สมัครร้าน เปิดรับ Delivery จัดการสินค้า | [Merchant Web](../../merchant/index.html) | ตรวจซอร์สแล้ว | รายชื่ออนุมัติและการตั้งค่าร้านในรุ่นนำร่อง |
| สแกน บันทึกช่องทางชำระ และส่งร่าง | [Web POS](../../merchant/pos-table-v2.js), [#1013](https://github.com/chatchairins-source/QueuGo/pull/1013) merged, [#1020](https://github.com/chatchairins-source/QueuGo/pull/1020) draft (หน้าเปิดแอป), [#1041](https://github.com/chatchairins-source/QueuGo/pull/1041) draft (QR ที่โต๊ะ) | ตรวจซอร์สและขอบเขต PR แล้ว | ผล QA บนรุ่นเปิดจริงและวิธีชำระที่อนุมัติ |
| รับงาน รับสินค้า ไปส่ง ยืนยันถึงและจบงาน | [Rider Web](../../rider/index.html), [#1053](https://github.com/chatchairins-source/QueuGo/pull/1053) draft | ตรวจซอร์ส Web แล้ว · Native **PENDING/NOT VERIFIED** | E2E, Android GPS/Push และ offline routing |
| เปิดเรื่องจากออเดอร์ ตรวจ และส่งต่อ | [Customer Web](../../index.html), [Rider issues](../../rider/issues.js), [Admin](../../admin/index.html) | ตรวจซอร์สแล้ว | ซ้อมเคสและยืนยันสิทธิ์บัญชีเวร |
| พักงาน กู้คืน และเปิดรับงานใหม่ | [Runbook](incident-runbook.md), [#1048](https://github.com/chatchairins-source/QueuGo/pull/1048) draft | **PENDING/NOT VERIFIED** | ชื่อผู้มีอำนาจ ช่องทางสั่งพักจริง และผลซ้อมกู้คืน |

## หลักฐาน CI ที่พบ

- [43509849584](https://github.com/chatchairins-source/QueuGo/actions/runs/43509849584): งาน offline-routing ล้มเหลว ขั้นเผยแพร่ APK ถูกข้าม รายงาน Worker 4 ใน [#1048](https://github.com/chatchairins-source/QueuGo/pull/1048) ระบุแหล่งกราฟตอบ HTTP 404
- [43509849511](https://github.com/chatchairins-source/QueuGo/actions/runs/43509849511): งาน Android capture ล้มเหลว รายงาน [#1048](https://github.com/chatchairins-source/QueuGo/pull/1048) ระบุข้อผิดพลาด Kotlin ฝั่ง Merchant และ [#1039](https://github.com/chatchairins-source/QueuGo/pull/1039) เสนอการแก้
- [43510742359](https://github.com/chatchairins-source/QueuGo/actions/runs/43510742359) และ [43464892458](https://github.com/chatchairins-source/QueuGo/actions/runs/43464892458): ตรวจพบ job test สำเร็จใน RC ที่ Worker 4 อ้างอิง หลักฐานการกู้คืนจริงและการส่งมอบ signed APK ทั้งสามยัง **PENDING/NOT VERIFIED**

## บันทึกมติ

ลงวันที่เวลา (ICT), รุ่น Web/Android และ source SHA, พื้นที่และเวลาที่อนุมัติ, บริการและวิธีชำระ, ลิงก์หลักฐานแต่ละข้อ, เวรหลัก/สำรอง, คำสั่งพักงานและผู้มีสิทธิ์, งานค้างพร้อมผู้รับผิดชอบ, ผล Go/No-Go และชื่อผู้อนุมัติ ถ้าหลักฐานของบริการที่จะเปิดยังขาด ให้คง No-Go และมอบหมายเจ้าของหลักฐานพร้อมวันทบทวน หากปรับขอบเขตให้บันทึกมติใหม่และตรวจผลกระทบต่อออเดอร์เดิมก่อนเปิด
