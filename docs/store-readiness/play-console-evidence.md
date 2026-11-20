# หลักฐานสำหรับกรอก Play Console

ตรวจ 14 พฤศจิกายน 2569 ฐานซอร์ส Native [#1057](https://github.com/chatchairins-source/QueuGo/pull/1057) ที่ `f30e54020562cad7c72f1eb0ce454507e2a58342` และ Web/SQL ที่ `369e4b49b20a0781f6d52be1c54aef5bd473609d` ณ รอบนี้ Rider [Authenticated Review](https://github.com/chatchairins-source/QueuGo/actions/runs/43545895094) และ [in-process Thailand routing](https://github.com/chatchairins-source/QueuGo/actions/runs/43545895088) จบแบบ failure ทั้งคู่ จึงรอผลจาก commit ที่จะส่งจริงก่อนลงผลตรวจรับ

## Foreground service และการใช้ตำแหน่ง

ให้กรอกตาม Type และพฤติกรรมที่ตรวจได้จาก AAB จริง Google Play ต้องการเหตุผล ผลกระทบต่อผู้ใช้ และวิดีโอแสดงการเริ่มหรือการรับรู้ของผู้ใช้ [คู่มือ FGS](https://support.google.com/googleplay/android-developer/answer/13392821) · [คู่มือ Background Location](https://support.google.com/googleplay/android-developer/answer/9799150)

| Type | แอปที่ต้องตรวจ | สิ่งที่จะบันทึกในวิดีโอและตรวจรับ | หลักฐานซอร์ส |
|---|---|---|---|
| location | Queue Rider | เปิดงานทดสอบ → เริ่มนำทางในแอป → แสดงคำอธิบายและ prompt → กด Home/ดับหน้าจอ → แสดงการแจ้งเตือนและผลจากการนำทางระหว่างใช้งาน → หยุดนำทางและตรวจว่า notification, location callbacks และการส่งตำแหน่งหยุดตามที่อธิบาย ทดสอบแยกเมื่อปฏิเสธ coarse/fine, จบงาน และออกจากบัญชี | [บริการ](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt#L167-L187) · [ทดสอบ Home/stop ในซอร์ส](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/androidTest/java/com/queuego/rider/RiderNavigationLocationServiceRuntimeTest.kt#L14-L53) |
| specialUse | Queue Rider | เปิดการนำทาง → เปิดตัวควบคุมกลับหน้าส่งงาน → แสดงการขอ overlay และผลเมื่ออนุญาต/ปฏิเสธ → ทดสอบกดกลับแอป → หยุดงานและดูว่า overlay หาย เจ้าของฟีเจอร์ระบุว่าทำไมต้องใช้ specialUse และ user impact หากขัดจังหวะ | [Manifest subtype](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/AndroidManifest.xml#L63-L69) · [Overlay](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/RiderReturnService.kt#L79-L115) |
| microphone | ทั้งสามหากอยู่ใน merged manifest | เริ่มหรือรับสายที่เกี่ยวข้องกับงาน → แสดง prompt และสถานะไมค์ → กด Home → แสดงการแจ้งเตือนระหว่างสาย → ปิดไมค์และวางสาย ตรวจว่า FGS และ audio track หยุด แยกผลตามบทบาท โดยเฉพาะ Merchant หากไม่มี flow ที่เข้าถึงได้ ให้เจ้าของ Android ประเมิน permission/FGS ของ package นั้น | [บริการ](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/shared/src/main/java/com/queuego/shared/NativeVoiceForegroundService.kt#L60-L77) · [ทดสอบ Rider ในซอร์ส](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/androidTest/java/com/queuego/rider/NativeVoiceForegroundRuntimeTest.kt#L19) |

ตรวจแยกสามอย่างใน Rider ได้แก่ FGS ยังทำงาน การอ่านพิกัดยังเกิด และพิกัดยังส่งออกไป การทดสอบ Home ที่พบในซอร์สครอบคลุม lifecycle บางส่วน ให้ QA เก็บผลตำแหน่งบนหน้าจอดับ สลับแอป ปัด task จบงาน และ session หมดอายุแยกกัน จากนั้นให้ผู้ดูแล Play ตรวจว่าต้องกรอก declaration เพิ่มหรือแสดง disclosure ก่อน permission หรือไม่ แม้ source manifest จะไม่ประกาศ ACCESS_BACKGROUND_LOCATION

## สคริปต์ตรวจ Account Deletion บนบัญชีทดสอบ

ใช้คนละบัญชีสำหรับเส้นทางในแอปและหน้าเว็บ รวมอย่างน้อย 6 บัญชี ระบุ SKU/role, เวลาเริ่มและเสร็จ, รุ่นแอป และผลการตรวจโดยอ้าง case ID

1. เตรียมออเดอร์ที่ปิดแล้ว โปรไฟล์ และข้อมูลตามบทบาท (รูป เอกสาร ไฟล์ support หรือ push token ตามที่มีจริง) ใช้ข้อมูลทดสอบ
2. สำหรับแต่ละบทบาท ทดสอบว่าผู้ใช้หาเมนูลบเจอในแอป จากนั้นใช้หน้าเว็บกับบัญชีคู่ทดสอบที่ลงชื่อเข้าใช้ได้โดยไม่ต้องเปิดแอป ทดสอบกรณีลืมรหัสผ่านแยกด้วย
3. ทดสอบกรณีมีงานค้าง บันทึกข้อความและเส้นทางเคลียร์งานหรือขอความช่วยเหลือ ลบจากบัญชีที่ไม่มีงานค้างและบันทึกเวลาที่ระบบยืนยัน
4. ให้ Worker 2 ตรวจ Auth/session เดิม, profile/KYC, Storage, token, แชต/การโทร, support และคำสั่งซื้อที่เก็บต่อ ใช้หลักฐานที่ปกปิดชื่อ เบอร์ รหัสผ่าน และ token ระบุอายุข้อมูลที่ต้องเก็บและเหตุผลสำหรับรายการที่เก็บต่อ
5. ตรวจว่า Public Privacy/Deletion URLs ใน Play Console ตรงกับหน้าที่ใช้ตรวจและสอดคล้องกับผลทดสอบจริง อ้างผลจาก [ขั้นตอนลบบนเว็บ](https://github.com/chatchairins-source/QueuGo/blob/369e4b49b20a0781f6d52be1c54aef5bd473609d/docs/account-deletion.html#L40-L55)

## ข้อมูลประกอบการตัดสินว่ามีการแชร์ตำแหน่งหรือไม่

RPC `qg_search_places` ใน migration ที่ตรวจใช้ฐานข้อมูล `queuego_places` และ aliases [SQL](https://github.com/chatchairins-source/QueuGo/blob/369e4b49b20a0781f6d52be1c54aef5bd473609d/supabase/migrations/20261025102500_queuego_road_search_grouping.sql#L6-L25) ให้ยืนยัน definition ที่ deploy และ server/provider logs ที่เกี่ยวข้อง รวม Supabase, tile hosts, routing/pack downloads และกรณีเปิดแผนที่ภายนอก แยกพิกัดจริงจาก geographic tile coordinates และแยกการแชร์ที่เกิดจากแอปหรือ SDK จากการเปิดแอปภายนอกโดยผู้ใช้

## บันทึกสำหรับแต่ละ package ก่อนส่ง

| หลักฐาน | QueueGo | QueueMarket | Queue Rider | ผู้กรอก |
|---|---|---|---|---|
| AAB versionCode, SHA-256, source SHA, merged manifest, dependency report | รอ | รอ | รอ | Worker 4 |
| วิดีโอ/ผลตรวจ FGS และ permission | รอ | รอ | รอ | Worker 1 และ 4 |
| ลบบัญชีจากแอปและเว็บ + ผล backend | รอ | รอ | รอ | Worker 2 และ 4 |
| Data Safety ราย type + การยืนยันผู้รับข้อมูล | รอ | รอ | รอ | Worker 6 และเจ้าของข้อมูล |
| เกณฑ์ PDPA และข้อความ privacy ที่ผ่านการทบทวน | รอ | รอ | รอ | ผู้เชี่ยวชาญกฎหมายไทย |
| Store assets, reviewer access, กลุ่มเป้าหมาย, content rating, ประกาศที่ส่งจริง | รอ | รอ | รอ | ผู้ดูแล Play Console |

เก็บลิงก์หลักฐานอ้างอิงเป็นราย package และ release ในช่องที่เกี่ยวข้องก่อนส่ง สำหรับ reviewer credentials ใช้ช่อง App access ของ Play Console
