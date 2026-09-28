# QueueGo โต๊ะ QR v1

- ใช้ `pos_tables` และ `orders.table_id` ของเดิม เพิ่ม QR token, session หนึ่งชั่วโมง, `orders.table_session_id` และ request keys ใน Supabase
- Customer page `table-order.html` สแกน QR, ตรวจตำแหน่ง, แสดงเมนูจริง, ตะกร้า, ส่งครัว, สั่งเพิ่ม และคืนสภาพ session ผ่าน server
- Merchant POS รุ่นแยก `merchant/pos-qr-v1.js/css` เพิ่มดู/พิมพ์/ดาวน์โหลด/หมุน QR ในเมนูโต๊ะ; UI และ POS อื่นรักษาของเดิม
- Checkout แบบ transaction เดียว; DB ยืนยันราคา/ร้าน/โต๊ะ/session/ระยะทาง และจับออเดอร์ซ้ำด้วย request UUID และตะกร้าเหมือนกันใน 8 วินาที
- `DINE_IN` จาก QR ใช้ GP=0 ค่าส่ง=0; session ถูกเพิกถอนเมื่อหมุน QR
- สิทธิ์ session/request ไม่เปิด direct client access; RPC ตรวจ device key และการเป็นเจ้าของร้านในการหมุน QR
