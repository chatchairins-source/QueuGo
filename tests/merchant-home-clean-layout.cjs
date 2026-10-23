const fs=require('fs'),assert=require('assert');
const merchant=fs.readFileSync('merchant/index.html','utf8');
assert(!merchant.includes('class="qgm-revenue-entry"'),'Merchant Home must not render a duplicate revenue card');
assert(!merchant.includes("revenue.className='qgm-revenue-entry'")&&!merchant.includes("revenue.innerHTML='<span>฿</span><span><strong>รายได้ทั้งหมด</strong>"),'Merchant Home must not render the duplicate revenue entry block');
assert(!merchant.includes('id="qgm-delivery-readiness"'),'Merchant Home must not render the Delivery readiness card');
assert(merchant.includes("['shop-settlement','chart','รายงาน']"),'Merchant Home must keep the Reports menu icon');
assert(merchant.includes("qgmLoadShopOpen()"),'Merchant Home must keep real open/close state loading');
assert(merchant.includes("window.qgmSetShopOpen=async function"),'Merchant Home must keep the real shop open/close control');
assert(!merchant.includes("['shop-profile','home','ข้อมูลร้านค้า']")&&!merchant.includes("['shop-modules','gear','ตั้งค่า']")&&!merchant.includes("['notifications','bell','การแจ้งเตือน']"),'Merchant Home menu must not duplicate Account/settings/notification entry points');
assert(merchant.includes("onclick=\"navigate('shop-modules')\"")&&merchant.includes('<b>ตั้งค่าร้านค้า</b>'),'Merchant Account must retain Settings access after Home dedupe');
assert(merchant.includes('aria-label="การแจ้งเตือน"')&&merchant.includes("navigate('notifications')"),'Merchant header bell must remain the notification entry point');

assert(merchant.includes('.qgm-shopping-subcategory-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))'),'Merchant shopping subcategories must remain a compact two-column grid on mobile');
assert(merchant.includes('.qgm-shopping-subcategory-grid input[type=checkbox]')&&merchant.includes('width:18px!important')&&merchant.includes('height:18px!important'),'Merchant shopping subcategory checkboxes must override generic form input sizing');
assert(!merchant.includes('@media(max-width:430px){.qgm-shopping-subcategory-grid{grid-template-columns:1fr}}'),'Merchant shopping subcategories must not expand to one oversized row per item on phones');
console.log('merchant home clean layout 8 checks PASS');
