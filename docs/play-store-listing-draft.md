# QueueGo — Google Play Store Listing Draft

Last reviewed: 2026-10-10

Google Play limits used here:
- App name: max 30 characters
- Short description: max 80 characters
- Full description: max 4,000 characters

The copy below is intentionally factual. Do not add claims such as “cheapest”, “best”, “#1”, “free delivery”, rankings, awards, or competitor comparisons unless QueueTech can substantiate them and the claims comply with Play metadata policy.

---

## 1. Customer app

Package: `com.queuego.customer`

### App name

**QueueGo**

### Short description

**สั่งอาหาร ของใช้ ตลาดสด และติดตามการจัดส่งกับ QueueGo**

### Full description

QueueGo คือแอปสำหรับลูกค้าที่ต้องการสั่งสินค้าและบริการจากร้านค้าใกล้พื้นที่ให้บริการ พร้อมติดตามสถานะออเดอร์และการจัดส่งในแอปเดียว

ฟังก์ชันหลัก
- เลือกร้านอาหาร เครื่องดื่ม ร้านขายของชำ ตลาดสด และบริการที่ QueueGo เปิดให้ใช้งาน
- เลือกตำแหน่งรับสินค้าและตรวจพื้นที่ให้บริการ
- ดูรายการสินค้า ราคา ค่าส่ง และยอดรวมก่อนยืนยันออเดอร์
- ติดตามสถานะออเดอร์ตั้งแต่ร้านรับงานจนถึงการส่งมอบ
- ดูเลขออเดอร์สำหรับตรวจสอบกับร้านค้าและ Rider
- รับการแจ้งเตือนเกี่ยวกับสถานะออเดอร์
- ติดต่อ Rider ผ่านแชตที่ผูกกับออเดอร์ในช่วงเวลาที่ระบบกำหนด
- รายงานข้อความหรือผู้ใช้ และบล็อกคู่สนทนาได้จากหน้าแชต
- ส่งรูปหรือหลักฐานเมื่อฟังก์ชันนั้นจำเป็นต่อออเดอร์หรือการช่วยเหลือ
- จัดการบัญชี ความเป็นส่วนตัว และลบบัญชีได้จากแอป

QueueGo ใช้ตำแหน่งเฉพาะเมื่อจำเป็นต่อฟังก์ชัน เช่น การระบุตำแหน่งจัดส่ง การตรวจพื้นที่บริการ และการคำนวณระยะทาง แอปไม่ขอสิทธิ์ตำแหน่งเบื้องหลัง

การให้บริการแต่ละประเภทขึ้นอยู่กับพื้นที่ ร้านค้า และฟีเจอร์ที่ QueueGo เปิดใช้งานในขณะนั้น

นโยบายความเป็นส่วนตัว กติกาการแชต และช่องทางลบบัญชีสามารถเปิดดูได้จากภายในแอป

### Closed Beta release notes

**Closed Beta รุ่นทดสอบ QueueGo Customer**
- ปรับ flow สั่งซื้อและติดตามออเดอร์
- เพิ่ม Service Area ฝั่ง server
- เพิ่ม Account Deletion และ Privacy
- เพิ่ม Background Notification foundation
- เพิ่มกติกาแชต ระบบรายงาน และบล็อกผู้ใช้
- ปรับความเสถียรของ session, cart และ realtime recovery

### Tester focus

1. สมัคร/เข้าสู่ระบบและกลับเข้าแอปหลังปิดเปิดใหม่
2. เลือกตำแหน่งและตรวจว่าออเดอร์นอก Service Area ถูกปฏิเสธอย่างชัดเจน
3. สั่งออเดอร์จริงหนึ่งครั้งและตรวจว่าตะกร้าถูกล้างหลังสำเร็จ
4. ตรวจเลขออเดอร์ 4 หลักกับร้านค้า
5. ติดตามสถานะจนส่งสำเร็จ
6. เปิดการแจ้งเตือนและใช้ “ทดสอบการแจ้งเตือน”
7. ทดสอบแชต Customer–Rider: Terms → ส่งข้อความ → Report → Block/Unblock
8. ทดสอบลบบัญชีเมื่อไม่มีงานค้าง

---

## 2. Merchant app

Package: `com.queuego.merchant`

### App name

**QueueGo Merchant**

### Short description

**รับออเดอร์ จัดการร้าน สินค้า และรายได้สำหรับร้านค้า QueueGo**

### Full description

QueueGo Merchant คือแอปสำหรับร้านค้าที่เข้าร่วมระบบ QueueGo ใช้รับและจัดการออเดอร์จากลูกค้า จัดการข้อมูลร้าน สินค้า และสถานะการเตรียมสินค้า

ฟังก์ชันหลัก
- รับหรือปฏิเสธออเดอร์จากลูกค้า
- ดูรายละเอียดออเดอร์ เลขออเดอร์ และยอดที่เกี่ยวข้อง
- อัปเดตสถานะการเตรียมสินค้าและพร้อมรับสินค้า
- จัดการข้อมูลร้าน เวลาเปิดปิด ประเภทบริการ และตำแหน่งร้าน
- จัดการสินค้า ราคา ความพร้อมขาย และสต๊อกในส่วนที่เปิดใช้งาน
- ดูข้อมูลรายได้และรายการที่ระบบแสดงสำหรับร้านค้า
- รับข้อความและการแจ้งเตือนจากระบบ
- ติดต่อฝ่ายช่วยเหลือ QueueGo
- จัดการบัญชี ความเป็นส่วนตัว และลบบัญชีเมื่อไม่มีงานค้าง

QueueGo Merchant ใช้ตำแหน่งร้านเพื่อการค้นหาร้าน การคำนวณบริการ การรับสินค้า และการจัดส่งตามฟังก์ชันที่เปิดใช้งาน แอปไม่ขอสิทธิ์ตำแหน่งเบื้องหลังและไม่ขอสิทธิ์เข้าถึงคลังรูปทั้งหมด

### Closed Beta release notes

**Closed Beta รุ่นทดสอบ QueueGo Merchant**
- ปรับการรับออเดอร์ให้ตอบสนองครั้งเดียว
- ปรับ KDS และสถานะเตรียม/พร้อมรับ
- เพิ่ม Account Deletion และ Privacy
- เพิ่ม Background Notification foundation
- ปรับ Native Android session persistence และการแจ้งเตือน
- ปรับ server idempotency และ order routing

### Tester focus

1. เข้าสู่ระบบและตรวจร้านของบัญชีตนเองเท่านั้น
2. รับออเดอร์ด้วยการกดครั้งเดียว
3. ปฏิเสธออเดอร์และตรวจสถานะฝั่ง Customer
4. เปลี่ยน Preparing → Ready และตรวจ realtime
5. ทดสอบการแจ้งเตือนออเดอร์และสถานะสำคัญ
6. ตรวจสินค้า ราคา สต๊อก และสถานะเปิดปิด
7. ทดสอบ offline → reconnect
8. ลอง logout/login และตรวจ session isolation

---

## 3. Rider app

Package: `com.queuego.rider`

### App name

**QueueGo Rider**

### Short description

**รับงาน ส่งสินค้า นำทาง และติดตามรายได้สำหรับ Rider QueueGo**

### Full description

QueueGo Rider คือแอปสำหรับ Rider ที่ผ่านขั้นตอนการอนุมัติของ QueueGo ใช้รับงานจัดส่ง ดูข้อมูลจุดรับและจุดส่ง นำทาง และอัปเดตขั้นตอนการส่งมอบ

ฟังก์ชันหลัก
- ออนไลน์และรับข้อเสนองานที่ server ส่งให้บัญชี Rider
- ดูข้อมูลร้าน จุดรับสินค้า ลูกค้า และยอดเงินที่เกี่ยวข้อง
- เปิดเส้นทางนำทางไปร้านและไปยังจุดส่ง
- อัปเดตขั้นตอนถึงร้าน รับสินค้า เดินทาง และส่งมอบ
- ถ่าย/ส่งหลักฐานตามขั้นตอนที่ QueueGo กำหนด
- จบงานด้วยขั้นตอนการส่งมอบที่กำหนด
- ติดต่อ Customer ผ่านแชตที่ผูกกับออเดอร์
- รายงานข้อความหรือผู้ใช้ และบล็อกคู่สนทนาได้
- ดูประวัติงานและข้อมูลรายได้ที่แอปแสดง
- รับ Background Notification สำหรับงานและสถานะสำคัญ
- จัดการบัญชี ความเป็นส่วนตัว และลบบัญชีเมื่อไม่มีงานค้าง

QueueGo Rider ใช้ตำแหน่งขณะใช้ workflow งานเพื่อการเลือกงาน ระยะทาง นำทาง และส่งมอบ แอปไม่ขอสิทธิ์ตำแหน่งเบื้องหลังใน Android manifest ปัจจุบัน

### Closed Beta release notes

**Closed Beta รุ่นทดสอบ QueueGo Rider**
- ใช้ Sequential Rider Offer: หนึ่งงานเสนอให้ Rider หนึ่งคนในแต่ละช่วงเวลา
- ปรับรับงานและ action ให้ idempotent
- เพิ่มรูปหลักฐานรับสินค้าและส่งมอบ
- เพิ่ม Customer/Rider chat safety
- เพิ่ม Background Notification foundation
- ปรับ navigation, session และ recovery หลัง reconnect

### Tester focus

1. ออนไลน์และตรวจว่าเห็นเฉพาะงานที่ server เสนอให้ตนเอง
2. รับงานครั้งเดียวและตรวจว่า Rider คนอื่นไม่สามารถรับงานเดียวกัน
3. เปิดนำทางไปร้าน
4. กดถึงร้านและทำขั้นตอนรับสินค้า
5. ตรวจรูปหลักฐานรับสินค้า
6. เปิดนำทางไปลูกค้า
7. ส่งมอบและจบงานพร้อมรูปหลักฐาน
8. ทดสอบแชต Terms/Report/Block
9. ทดสอบ Background Notification ตอนกด Home และล็อกหน้าจอ
10. ทดสอบ offline/reconnect และ background/foreground

---

## Store assets checklist

For each app:
- 512×512 Play Store icon, PNG, no ranking/price/download badges
- At least 2 representative phone screenshots
- Screenshots must be captured from the actual current Native Android app, not mock UI or the legacy Web/Capacitor build
- Feature graphic if required by the selected listing setup
- Privacy policy URL
- Account deletion URL where applicable
- Support contact
- Category and content rating questionnaire
- Data Safety form
- App access/reviewer instructions if login is required

Do not reuse screenshots from an outdated UI after a production UI replacement.

## Reviewer access note draft

QueueGo requires authentication for role-specific functionality. Provide Google Play review with dedicated review/test accounts for each submitted app role, with no real customer personal data in those accounts. Review accounts must have the permissions needed to reach the primary app features without manual intervention from QueueTech.

Do not place production admin credentials, service-role keys, or personal tester credentials in Play Console reviewer notes.
