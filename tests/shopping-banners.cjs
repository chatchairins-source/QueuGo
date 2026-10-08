const fs=require('fs'),path=require('path'),assert=require('assert');
let checks=0;
const ok=(value,message)=>{assert.ok(value,message);checks++};
const customer=fs.readFileSync(path.resolve(__dirname,'../index.html'),'utf8');
const merchant=fs.readFileSync(path.resolve(__dirname,'../merchant/index.html'),'utf8');
const admin=fs.readFileSync(path.resolve(__dirname,'../admin/index.html'),'utf8');
const adminService=fs.readFileSync(path.resolve(__dirname,'../admin/admin-service-banners.js'),'utf8');
const db=fs.readFileSync(path.resolve(__dirname,'../supabase/migrations/20261008051127_shopping_categories_and_service_banners_v1.sql'),'utf8');
const banners=fs.readFileSync(path.resolve(__dirname,'../supabase/migrations/20261008050646_home_banner_carousel_storage_v1.sql'),'utf8');

ok(customer.includes("V.shopping=async("),'customer shopping route missing');
ok(customer.includes("go('shopping/mobile_accessories')"),'mobile category missing');
ok(customer.includes("go('shopping/computer_it')"),'IT category missing');
ok(customer.includes("go('shopping/automotive')"),'automotive category missing');
ok(customer.includes("go('shopping/automotive_car')"),'car-parts filter missing');
ok(customer.includes("go('shopping/automotive_motorcycle')"),'motorcycle-parts filter missing');
ok(customer.includes('public_subcategories'),'public shopping subcategories not read');
ok(customer.includes("qgServiceBannerMarkup('food'"),'food managed banner missing');
ok(customer.includes("qgServiceBannerMarkup('cafe'"),'cafe managed banner missing');
ok(customer.includes("qgServiceBannerMarkup('grocery'"),'grocery managed banner missing');
ok(customer.includes("qgServiceBannerMarkup('market'"),'market managed banner missing');
ok(customer.includes("qgServiceBannerMarkup(bannerKey"),'shopping managed banner missing');
ok(customer.includes("if(e.active===false)return ''"),'customer banner visibility switch missing');
ok(customer.includes('qgInitHomeBannerCarousel'),'home banner carousel missing');

ok(merchant.includes("<option value=\"shopping\">ช้อปปิ้ง</option>"),'merchant shopping type missing');
for(const key of ['mobile_accessories','computer_it','automotive_car','automotive_motorcycle'])ok(merchant.includes(key),'merchant subcategory missing: '+key);
ok(merchant.includes("shoppingSubcategories"),'merchant shopping subcategories not persisted');

ok(admin.includes("category==='shopping'")&&admin.includes("automotive_motorcycle:'อะไหล่มอเตอร์ไซค์'"),'admin shopping/subcategory label missing');
ok(admin.includes('qgAdminLoadHomeBanners'),'admin home banner manager missing');
ok(admin.includes('qgAdminLoadServiceBanners')&&adminService.includes('qgAdminLoadServiceBanners'),'admin category banner manager missing');
ok(adminService.includes("const KEY='service_banners',BUCKET='queuego-banners'"),'service banner storage manager missing');

ok(db.includes('public_subcategories text[]'),'database public subcategory projection missing');
ok(db.includes('trg_queuego_shop_public_subcategories'),'database shopping subcategory sync trigger missing');
ok(db.includes("'service_banners'"),'service banner settings missing');
ok(db.includes("'home_service_banner','service_banners'"),'public banner read allowlist missing');
ok(banners.includes("'queuego-banners'"),'banner storage bucket migration missing');
ok(banners.includes("jsonb_build_object('slot',3,'active',false)"),'home carousel must support three slots');

const forbidden=['queuego_place_shopping_order','shopping_rider_claim','shopping_orders'];
for(const token of forbidden)ok(!customer.includes(token)&&!merchant.includes(token)&&!db.includes(token),'shopping must reuse existing order flow: '+token);

console.log(JSON.stringify({checks,failures:0,scope:'Shopping categories, reusable retail flow, icons, managed category banners and three-slot home carousel'}));
