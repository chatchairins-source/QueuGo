# QueueGo โต๊ะ QR v1 — ผลทดสอบ 28 กันยายน 2569

Source: GitHub `chatchairins-source/QueuGo` main at `a50f02d` before this change; Supabase project `pkypiqhlrmzocysgeqew`. Schema checked before migration. Applied migration `queuego_table_qr_v1` successfully.

| กรณี | ผล | หลักฐาน/ขอบเขต |
|---|---|---|
| 1 สแกน QR โต๊ะ 1 | PASS (RPC) | QR token ของโต๊ะ 1 ในร้านทดสอบออก session `688b6114…` |
| 2 เปิดร้านที่ตรงกัน | PASS (RPC) | shop_id `b2012f75…`, ชื่อร้านและ table_id ตรงฐานข้อมูล |
| 3 สั่งสินค้าจริง | PASS (RPC) | order `75c5b2a4…`, สินค้า `477e94ae…`, ยอด 56 บาทจาก products |
| 4 ร้าน/ครัวเห็นโต๊ะ | PASS (DB) | `orders.table_id`, `table_session_id`, `sales_channel=POS`, `kitchen_status=SENT_TO_KITCHEN`; merchant POS query ดึง POS นี้ได้ตาม schema; ยังไม่ได้ยืนยันภาพบนเครื่องร้านจริง |
| 5 สั่งเพิ่มภายในหนึ่งชั่วโมง | PASS (RPC) | session โต๊ะ 2 สร้างออเดอร์เพิ่มที่มียอด 112 บาท คำขอคนละรายการ |
| 6 Refresh ใช้ session เดิม | PASS (RPC), UI NOT RUN | `qg_table_session_status` คืน id/expiry เดิม; ยังไม่ได้ทดสอบ refresh บนอุปกรณ์จริง |
| 7 ปิด/เปิด Browser | NOT RUN | โค้ดเก็บ session/device ใน localStorage แล้วตรวจ server ใหม่; ยังไม่ได้ทดสอบบน iPhone จริง |
| 8 เปลี่ยน table_id ใน URL | PASS (design/DB) | QR URL มี token โต๊ะ; checkout ไม่รับ shop_id/table_id จาก client; FK `(table_id,shop_id)` |
| 9 หมดอายุสั่งไม่ได้ | PASS (RPC) | จำลอง session ทดสอบหมดอายุใน DB แล้ว checkout ปฏิเสธ |
| 10 Refresh หลังหมดอายุ | PASS (RPC), UI NOT RUN | status คืน active=false และ expires_at เดิม; browser UI ยังไม่ได้ทดสอบจริง |
| 11 สแกนใหม่ได้ session ใหม่ | PASS (RPC) | สแกนโต๊ะ 1 สองครั้งได้ UUID ใหม่; ไม่มีการต่ออายุ session เดิม |
| 12 กดส่งซ้ำเร็ว | PASS (RPC) | สอง transaction ขนาน request UUID เดียวกันคืน order `17f7081d…` เพียงรายการเดียว; อีกกรณี UUID ต่างกันและตะกร้าเดียวกันภายใน 8 วินาทีคืน order `49853c5e…` เดิม |
| 13 สองโต๊ะพร้อมกัน | PASS (DB/RPC) | โต๊ะ 1 และ 2 แยก session/table_id และ order |
| 14 สองร้านไม่ข้าม | PASS (security logic), NOT RUN (real second-shop flow) | product checkout ตรวจ `products.shop_id=session.shop_id`; ยังไม่ได้ทำออเดอร์จริงร้านที่สอง |
| 15 Secret ไม่รั่ว | PASS (static) | HTML/JS ใช้ publishable key เท่านั้น; ไม่มี service-role key; ยังไม่ได้ดู Network ในมือถือจริง |
| ระยะเกิน 100 เมตร | PASS (RPC) | สแกนด้วยพิกัดกรุงเทพถูกปฏิเสธจาก DB |
| Device key ผิด | PASS (RPC) | checkout ถูกปฏิเสธ |
| แก้ราคาใน request | PASS (RPC) | field `price` ถูกปฏิเสธ ยอดมาจาก products |
| สิทธิ์ anon | PASS (DB) | execute RPC ได้ แต่ไม่มี direct SELECT session หรือ INSERT request; RLS เปิดอยู่ |
| QR rotate / download / print | NOT RUN | UI มีปุ่มและ RPC เฉพาะ merchant; ยังไม่ได้ทดสอบบัญชีเจ้าของร้านและเครื่องพิมพ์จริง |
| Mobile layout | NOT RUN | CSS mobile first และ viewport ตั้งค่าแล้ว ยังไม่ได้ดู iPhone จริง |

## ข้อจำกัดจริง

- QR ถาวรที่ถูกถ่ายรูปส่งต่อกันพิสูจน์ไม่ได้ว่าผู้ใช้สแกน ณ โต๊ะจริง เว็บไซต์ตรวจ GPS 100 เมตรตอนสแกนและส่งออเดอร์ แต่พิกัดฝั่งอุปกรณ์อาจถูกปลอมได้ หากต้องการป้องกันระดับสูงต้องเพิ่มการยืนยันโดยพนักงานหรืออุปกรณ์ในร้าน
- QR renderer ใช้ `qrcodejs` 1.0.0 จาก CDN เมื่อกดดู QR; ต้องต่ออินเทอร์เน็ตขณะสร้าง/พิมพ์ หาก CDN โหลดไม่ได้หน้าจอแสดงข้อผิดพลาด
- ออเดอร์ทดสอบที่สร้างในร้าน “ร้านค้าทดสอบ” คงอยู่เพื่อ audit: `75c5b2a4…`, `17f7081d…`, `49853c5e…` ยังไม่ชำระ
- ยังไม่ได้ทดสอบ Safari บน iPhone, GPS จริง, ปริ้นเตอร์, บัญชีเจ้าของร้าน, Supabase Realtime ระหว่างสองเครื่อง และ Customer/Delivery regression แบบเต็ม

## ทดสอบ QR จริงแบบสั้น

1. เปิด Merchant > หน้าร้าน > โต๊ะ > `QR` ของโต๊ะที่เปิดใช้งาน กดพิมพ์และติดโต๊ะ
2. ใช้มือถือที่อยู่ในระยะ 100 เมตรสแกนและอนุญาตตำแหน่ง
3. เลือกสินค้า ส่งเข้าครัว และตรวจชื่อโต๊ะ/เลขบิลใน Merchant > ครัว/คิดเงิน
4. ส่งเพิ่มอีกครั้งและลองกดส่งซ้ำรวดเร็ว ตรวจว่าบิลไม่ซ้ำ
5. หลังหนึ่งชั่วโมงสั่งไม่ได้ ต้องสแกน QR ที่โต๊ะอีกครั้ง
