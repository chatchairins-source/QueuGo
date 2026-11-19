# Store Listing ไทย / English

## QueueGo — Customer

**ชื่อทั้งสองภาษา** QueueGo

**คำอธิบายสั้น TH**

> สั่งอาหารและสินค้าจากร้านใกล้บ้าน พร้อมดูสถานะการจัดส่ง

**Short description EN**

> Order food and goods from nearby shops and track your delivery.

**คำอธิบายเต็ม TH**

> เลือกดูร้านและสินค้าใกล้คุณ ใส่รายการที่ต้องการลงตะกร้า และระบุจุดส่งเพื่อตรวจสอบบริการในพื้นที่ ดูสถานะและประวัติคำสั่งซื้อในแอป และติดต่อไรเดอร์ผ่านแชตของออเดอร์เมื่อช่องทางนี้เปิดให้ใช้งาน

**Full description EN**

> Browse nearby shops and products, add items to your cart, and set a delivery address to check service availability. View order status and history in the app, and contact your rider through the order chat when it is available.

หลักฐาน [พิกัดและที่อยู่](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/customer/src/main/java/com/queuego/customer/CustomerApi.kt#L371-L389) · [สถานะและประวัติ](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/customer/src/main/java/com/queuego/customer/CustomerApi.kt#L230-L247) · [การเชื่อมแชต Rider กับออเดอร์](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/RiderChat.kt#L127)

## QueueMarket — Merchant

**ชื่อทั้งสองภาษา** QueueMarket

**คำอธิบายสั้น TH**

> จัดการสินค้า ออเดอร์ และยอดขายหน้าร้านด้วย POS สำหรับร้านค้า

**Short description EN**

> Manage products, orders, and in-store sales with merchant POS tools.

**คำอธิบายเต็ม TH**

> จัดการข้อมูลและรูปสินค้าของร้าน ดูคำสั่งซื้อและสถานะการเตรียมสินค้า ใช้ POS บันทึกการขายหน้าร้าน เปิดดูบิลและสถานะการชำระเงินจากแอปสำหรับร้านค้า

**Full description EN**

> Manage shop details and product images, review incoming orders, and follow preparation status. Use the merchant POS to record in-store sales and review bills and payment status.

หลักฐาน [รูปสินค้า](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt#L1010) · [คำสั่งซื้อ](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt#L656) · [POS](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt#L759)

## Queue Rider — Rider

**ชื่อทั้งสองภาษา** Queue Rider

**คำอธิบายสั้น TH**

> รับงานส่ง ดูจุดรับและจุดส่ง แล้วอัปเดตสถานะพร้อมหลักฐาน

**Short description EN**

> Accept deliveries, view pickup and drop-off points, and update job status.

**คำอธิบายเต็ม TH**

> ดูงานจัดส่งที่เปิดให้บัญชีไรเดอร์ของคุณรับ ตรวจรายละเอียดจุดรับและจุดส่ง และอัปเดตความคืบหน้าของงานในแอป แนบรูปหลักฐานในขั้นตอนที่กำหนด และดูประวัติงานที่ส่งแล้ว

**Full description EN**

> View available delivery jobs, check pickup and drop-off details, and update your progress in the app. Add photo proof where required and review your completed deliveries.

หลักฐาน [งานที่เสนอ](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt#L1838) · [รูปหลักฐาน](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt#L1322) · [ประวัติงาน](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt#L1494)

หากทดสอบยืนยันว่ารุ่นที่ส่งเข้าถึงพิกัดต่อเมื่อแอปไม่อยู่ด้านหน้า ให้เติมคำอธิบาย Rider TH/EN จากพฤติกรรมจริงก่อนส่ง เช่น “เมื่อเปิดการนำทางระหว่างงานจัดส่ง Queue Rider ใช้ตำแหน่งต่อเมื่อคุณสลับไปแอปอื่น จนกว่าคุณจะหยุดนำทาง” / “During an active delivery, Queue Rider uses your location while navigation remains active, including when you switch apps, until you stop navigation.” ให้ QA ตรวจว่า trigger และ stop condition ตรงกับรุ่นที่ส่งก่อนใช้ข้อความ

## ข้อมูลและหลักฐานสำหรับ Play Console

| รายการ | Customer | Merchant | Rider |
|---|---|---|---|
| Package name จาก Gradle | [com.queuego.customer](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/customer/build.gradle.kts#L13-L15) | [com.queuego.merchant](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/merchant/build.gradle.kts#L13-L15) | [com.queuego.rider](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/build.gradle.kts#L15-L17) |
| Launcher label / icon | [QueueGo / ic_queuego_launcher](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/customer/src/main/AndroidManifest.xml#L9-L10) | [QueueMarket / ic_queuego_launcher](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/merchant/src/main/AndroidManifest.xml#L10-L11) | [Queue Rider / ic_queuego_launcher](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/rider/src/main/AndroidManifest.xml#L21-L23) |
| ภาพหน้าจอแนะนำจาก build จริง | ร้าน/สินค้า, ตะกร้า, สถานะออเดอร์ | คำสั่งซื้อ, POS, จัดการสินค้า | งานที่รับได้, จุดรับ-ส่ง, อัปเดตสถานะ |
| Contains Ads ร่างตามหลักฐาน | Yes หาก paid Promote เปิดได้จากรุ่นที่ส่ง [RPC](https://github.com/chatchairins-source/QueuGo/blob/f30e54020562cad7c72f1eb0ce454507e2a58342/native-android/customer/src/main/java/com/queuego/customer/CustomerPromotionScreen.kt#L45) | No ตามเอกสารเดิม ยืนยัน release | No ตามเอกสารเดิม ยืนยัน release |

- ชื่อ ≤30 ตัวอักษร, short description ≤80 และ full description ≤4000 ต่อภาษา [Store setup](https://support.google.com/googleplay/android-developer/answer/9859152)
- ตรวจไฟล์ launcher ระดับ xxxhdpi จาก Native snapshot แล้ว ทั้งสามภาพมีขนาด 192×192 และเป็นคนละไฟล์ (SHA-256 prefix ตามลำดับ Customer, Merchant, Rider คือ `c322356a5c91`, `7c6422c12b20`, `627e5b07f4b9`) ตรวจภาพ Store โดยใช้ไฟล์ต้นฉบับที่อนุมัติสำหรับแต่ละแอป
- เตรียม icon 512×512 PNG ตามสเปก, feature graphic 1024×500 และอย่างน้อย 2 ภาพต่อแอปจากรุ่นจริง ตรวจภาพให้ตรง branding และไม่มีข้อมูลบุคคลจริง [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)
- ยืนยันชื่อผู้พัฒนา/อีเมลช่วยเหลือจริง, ภูมิภาค/track, เลขเวอร์ชัน, target API ของ AAB, application ID และ Play App Signing [Target API](https://support.google.com/googleplay/android-developer/answer/11926878)
- กรอก Privacy URL และ Account Deletion URL, Data Safety, app access, content rating, ads, target audience และ FGS ตามรุ่นจริง อ้างอิงการตัดสินใจ 18+ สำหรับ closed beta เดิม แล้วตรวจค่าที่กรอกบน Console [เอกสารเดิม](https://github.com/chatchairins-source/QueuGo/blob/369e4b49b20a0781f6d52be1c54aef5bd473609d/docs/play-closed-beta-readiness.md) · [app access](https://github.com/chatchairins-source/QueuGo/blob/369e4b49b20a0781f6d52be1c54aef5bd473609d/docs/play-app-access-content-rating.md)
- จัดบัญชีรีวิวที่ใช้ซ้ำได้แยกบทบาทพร้อมขั้นตอนเข้าแต่ละฟีเจอร์ ภาพต้องอ้างถึง AAB รุ่นเดียวกัน ตรวจประเภท/วันที่สมัครบัญชี Play เพื่อตัดสินข้อกำหนด closed testing [ข้อกำหนด personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
