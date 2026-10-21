const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.join(__dirname,'..','native-android','merchant','src','main','java','com','queuego','merchant');
const read=name=>fs.readFileSync(path.join(root,name),'utf8');
const typography=read('MerchantTypography.kt');
assert(typography.includes('internal val MerchantPageTitleSize = 19.sp'),'Merchant page title standard must be 19sp');
const checks=[
 ['MerchantRevenueScreen.kt','"วิเคราะห์ร้านค้า"'],
 ['MerchantNotificationsScreen.kt','"การแจ้งเตือน"'],
 ['MerchantModulesScreen.kt','"ตั้งค่าร้านค้า"'],
 ['MerchantMediaScreen.kt','"คลังรูปภาพร้าน"'],
 ['MerchantHoursScreen.kt','"เวลาทำการและวันหยุด"'],
 ['MerchantProductsScreen.kt','"สินค้าและเมนู"'],
 ['MerchantPromotionScreen.kt','"โปรโมชั่น"'],
 ['MerchantPosScreen.kt','"QueueGo หน้าร้าน"'],
 ['MerchantMarketStockScreen.kt','"สินค้าและสต๊อกตลาด"'],
 ['MerchantShopSetupScreen.kt','"แก้ไขข้อมูลร้าน"'],
 ['MerchantCatalogScreen.kt','"เพิ่มสินค้าจากคลังสินค้า"'],
 ['MerchantGroceryCatalogScreen.kt','"เพิ่มสินค้าจากแคตตาล็อก"'],
 ['MerchantMarketCatalogScreen.kt','"แคตตาล็อกตลาด"']
];
for(const [file,title] of checks){
 const s=read(file);
 assert(s.includes(title),file+' title missing');
 assert(s.includes('MerchantPageTitleSize'),file+' must use shared Merchant title scale');
}
assert(!read('MerchantRevenueScreen.kt').includes('fontSize = 22.sp'),'Merchant revenue title must not remain oversized');
assert(read('MerchantSupportScreen.kt').includes('MerchantPageTitle("ข้อความถึงแอดมิน"'),'Support must use Merchant page-title component');
assert(read('MerchantLaundryScreen.kt').includes('MerchantPageTitle("ฝากซัก"'),'Laundry must use Merchant page-title component');
assert(read('QueueGoMerchantApp.kt').includes('fontSize = MerchantPageTitleSize'),'Merchant Account title must use the shared page-title scale');
console.log('native Merchant page title standard 18 checks PASS');
