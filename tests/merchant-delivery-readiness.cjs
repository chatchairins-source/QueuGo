const fs=require('fs'),path=require('path'),assert=require('assert');
const merchant=fs.readFileSync(path.resolve(__dirname,'../merchant/index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(merchant.includes('id="qgm-delivery-readiness"'),'Merchant dashboard must show Delivery readiness');
ok(merchant.includes('window.qgmLoadDeliveryReadiness=async function'),'Merchant must evaluate Delivery readiness without blocking POS');
ok(merchant.includes("shop_business_hours?select=is_closed,opens_at,closes_at"),'Delivery readiness must check opening hours');
ok(merchant.includes("products?select=id,available,delivery_available,delivery_price,price,delivery_restriction"),'Delivery readiness must check sellable Delivery products');
ok(merchant.includes("market_products?select=product_id,stock_quantity,pack_size"),'Market readiness must check real market stock');
ok(merchant.includes("market_membership_status!=='approved'"),'Market readiness must require approved market membership');
ok(merchant.includes("ยังเปิด Delivery ไม่ได้ · กรุณาตั้งเวลาทำการก่อน"),'Opening-hours failure must be explained in Thai');
ok(merchant.includes("ยังเปิด Delivery ไม่ได้ · ต้องมีสินค้าที่เปิดขาย Delivery อย่างน้อย 1 รายการและมีราคา"),'Product readiness failure must be explained in Thai');
ok(merchant.includes("ยังเปิด Delivery ไม่ได้ · กรุณาเพิ่มที่อยู่และปักหมุดตำแหน่งร้าน"),'Location readiness failure must be explained in Thai');
ok(merchant.includes("{qgmLoadShopOpen();qgmLoadDeliveryReadiness()}"),'Dashboard must refresh open state and readiness together');

const start=merchant.indexOf('window.qgmLoadDeliveryReadiness=async function');
const end=merchant.indexOf('window.qgmSetShopOpen=async function',start);
const readiness=merchant.slice(start,end);
ok(start>=0&&end>start,'Delivery readiness function must be locatable');
ok(!readiness.includes("rpc/pos_enable_delivery"),'Readiness check must never enable Delivery by itself');
ok(!readiness.includes("method:'POST'"),'Readiness check must be read-only');

console.log(JSON.stringify({checks,failures:0,scope:'Merchant Delivery readiness is read-only, actionable, and Market-aware'}));
